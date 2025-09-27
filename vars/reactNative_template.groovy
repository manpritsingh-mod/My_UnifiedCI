/**
 * React Native Pipeline Template - Executes complete React Native CI pipeline
 * Handles React Native project build, test, lint, and reporting for both Android and iOS
 * Supports parallel builds, virtual/real device testing, and comprehensive error handling
 * @param config Pipeline configuration map (optional, uses defaults if not provided)
 * Usage: reactNative_template() or reactNative_template([project_language: 'react-native'])
 */
def call(Map config = [:]) {
    logger.info("Starting React Native Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }

    // Validate configuration before starting
    def validation = DockerImageManager.validateDockerConfig(config)
    if (!validation.valid) {
        error "Configuration validation failed: ${validation.message}"
    }
    
    if (!config.nexus?.registry || !config.nexus?.credentials_id || !config.nexus?.url) {
        error "Missing Nexus configuration. Please provide 'nexus.registry', 'nexus.url', and 'nexus.credentials_id'."
    }

    // Initialize stage results tracking for email reporting
    def stageResults = [:]

    stage('Checkout') {
        script {
            logger.info("CHECKOUT STAGE")
            core_github.checkout()
            stageResults['Checkout'] = [status: 'SUCCESS', duration: 0]
        }
    }

    // Pull Docker image and run ALL stages inside single container session
    script {
        logger.info("PULLING REACT NATIVE IMAGE FROM NEXUS")
        
        def imageConfig = DockerImageManager.getImageConfig(config.project_language, config)
        logger.info("Pulling Docker image: ${imageConfig.imagePath}")
        
        docker.withRegistry(imageConfig.registryUrl, imageConfig.credentialsId) {
            def image = docker.image(imageConfig.imagePath)
            
            logger.info("⬇️ Pulling image...")
            // Check if image exists locally first
            def imageExists = sh(
                script: "docker images -q ${imageConfig.imagePath}",
                returnStdout: true
            ).trim()
            
            if (!imageExists) {
                logger.info("Image not found locally, pulling from registry...")
                retry(3) {
                    try {
                        image.pull()
                    } catch (Exception e) {
                        logger.warning("Image pull attempt failed: ${e.getMessage()}")
                        sleep(5) // Wait 5 seconds before retry
                        throw e
                    }
                }
            } else {
                logger.info("Using cached Docker image")
            }
            
            // Verify image works
            image.inside {
                sh 'node --version'
                sh 'npm --version'
                sh 'java -version || echo "Java not available"'
                sh 'adb version || echo "ADB not available"'
            }
            
            logger.info("✅ React Native image ready!")
            env.REACT_NATIVE_DOCKER_IMAGE = imageConfig.imagePath
            stageResults['Pull Image'] = [status: 'SUCCESS', duration: 0]
            
            // Run ALL stages inside this single Docker container session
            image.inside("-v ${WORKSPACE}:/workspace -w /workspace --privileged") {
                
                stage('Setup Environment') {
                    def startTime = System.currentTimeMillis()
                    logger.info("SETUP ENVIRONMENT STAGE")
                    
                    try {
                        // Setup project environment
                        core_utils.setupProjectEnvironment(config.project_language, config)
                        
                        // Display environment info
                        sh script: ReactNativeScript.versionCommand()
                        
                        // Setup Android environment if needed
                        if (config.test_platforms?.contains('android') || !config.test_platforms) {
                            sh script: ReactNativeScript.setupAndroidEnvironmentCommand()
                        }
                        
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Setup Environment'] = [status: 'SUCCESS', duration: duration]
                        
                    } catch (Exception e) {
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Setup Environment'] = [status: 'FAILED', duration: duration]
                        logger.error("Environment setup failed: ${e.getMessage()}")
                        throw e
                    }
                }

                stage('Install Dependencies') {
                    def startTime = System.currentTimeMillis()
                    logger.info("INSTALL DEPENDENCIES STAGE")
                    
                    try {
                        def result = mobile_build.installReactNativeDependencies(config)
                        
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        if (result) {
                            stageResults['Install Dependencies'] = [status: 'SUCCESS', duration: duration]
                        } else {
                            stageResults['Install Dependencies'] = [status: 'FAILED', duration: duration]
                            logger.error("Dependency installation failed")
                            error("Dependency installation failed")
                        }
                    } catch (Exception e) {
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Install Dependencies'] = [status: 'FAILED', duration: duration]
                        throw e
                    }
                }

                stage('Lint') {
                    def startTime = System.currentTimeMillis()
                    if (core_utils.shouldExecuteStage('lint', config)) {
                        logger.info("LINTING STAGE")
                        
                        try {
                            def lintResult = runReactNativeLint(config)
                            env.LINT_RESULT = lintResult
                            
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            stageResults['Lint'] = [status: lintResult, duration: duration]
                            logger.info("Lint stage completed with result: ${lintResult}")
                            
                        } catch (Exception e) {
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            stageResults['Lint'] = [status: 'FAILED', duration: duration]
                            logger.error("Lint stage failed: ${e.getMessage()}")
                            // Continue pipeline even if lint fails
                        }
                    } else {
                        logger.info("Linting is disabled - skipping")
                        env.LINT_RESULT = 'SKIPPED'
                        stageResults['Lint'] = [status: 'SKIPPED', duration: 0]
                    }
                }

                stage('Build Apps') {
                    def startTime = System.currentTimeMillis()
                    logger.info("BUILDING REACT NATIVE APPS")
                    
                    try {
                        def result = mobile_build.buildReactNativeApp(config)
                        
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        if (result) {
                            stageResults['Build Apps'] = [status: 'SUCCESS', duration: duration]
                            
                            // Store build info for reporting
                            def buildInfo = mobile_build.getBuildInfo(config)
                            env.BUILD_INFO = groovy.json.JsonOutput.toJson(buildInfo)
                            
                        } else {
                            stageResults['Build Apps'] = [status: 'FAILED', duration: duration]
                            logger.error("App build failed")
                            error("App build failed")
                        }
                    } catch (Exception e) {
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Build Apps'] = [status: 'FAILED', duration: duration]
                        throw e
                    }
                }

                stage('Test Execution') {
                    def startTime = System.currentTimeMillis()
                    logger.info("STARTING REACT NATIVE TESTS")
                    
                    def parallelTests = [:]
                    
                    // Unit Tests
                    if (core_utils.shouldExecuteStage('unittest', config)) {
                        parallelTests['Unit Tests'] = {
                            def unitStartTime = System.currentTimeMillis()
                            logger.info("Running Unit Tests")
                            
                            try {
                                def testResult = mobile_test.runUnitTests(config)
                                env.UNIT_TEST_RESULT = testResult ? 'SUCCESS' : 'FAILED'
                                
                                def unitDuration = (System.currentTimeMillis() - unitStartTime) / 1000
                                stageResults['Unit Tests'] = [status: env.UNIT_TEST_RESULT, duration: unitDuration]
                                logger.info("Unit test stage completed with result: ${env.UNIT_TEST_RESULT}")
                                
                            } catch (Exception e) {
                                def unitDuration = (System.currentTimeMillis() - unitStartTime) / 1000
                                env.UNIT_TEST_RESULT = 'FAILED'
                                stageResults['Unit Tests'] = [status: 'FAILED', duration: unitDuration]
                                logger.error("Unit tests failed: ${e.getMessage()}")
                            }
                        }
                    } else {
                        logger.info("Unit testing is disabled - skipping")
                        env.UNIT_TEST_RESULT = 'SKIPPED'
                        stageResults['Unit Tests'] = [status: 'SKIPPED', duration: 0]
                    }
                    
                    // E2E Tests
                    if (core_utils.shouldExecuteStage('e2e_test', config)) {
                        parallelTests['E2E Tests'] = {
                            def e2eStartTime = System.currentTimeMillis()
                            logger.info("Running E2E Tests")
                            
                            try {
                                def testResult = mobile_test.runReactNativeTests(config)
                                env.E2E_TEST_RESULT = testResult ? 'SUCCESS' : 'FAILED'
                                
                                def e2eDuration = (System.currentTimeMillis() - e2eStartTime) / 1000
                                stageResults['E2E Tests'] = [status: env.E2E_TEST_RESULT, duration: e2eDuration]
                                logger.info("E2E test stage completed with result: ${env.E2E_TEST_RESULT}")
                                
                            } catch (Exception e) {
                                def e2eDuration = (System.currentTimeMillis() - e2eStartTime) / 1000
                                env.E2E_TEST_RESULT = 'FAILED'
                                stageResults['E2E Tests'] = [status: 'FAILED', duration: e2eDuration]
                                logger.error("E2E tests failed: ${e.getMessage()}")
                            }
                        }
                    } else {
                        logger.info("E2E testing is disabled - skipping")
                        env.E2E_TEST_RESULT = 'SKIPPED'
                        stageResults['E2E Tests'] = [status: 'SKIPPED', duration: 0]
                    }
                    
                    // Functional Tests (if configured)
                    if (core_utils.shouldExecuteStage('functionaltest', config) || 
                        core_utils.shouldExecuteStage('smoketest', config) || 
                        core_utils.shouldExecuteStage('sanitytest', config) || 
                        core_utils.shouldExecuteStage('regressiontest', config)) {
                        
                        parallelTests['Functional Tests'] = {
                            def funcStartTime = System.currentTimeMillis()
                            logger.info("Starting Functional Tests")
                            
                            try {
                                runFunctionalTests(config)
                                env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                                
                                def funcDuration = (System.currentTimeMillis() - funcStartTime) / 1000
                                stageResults['Functional Tests'] = [status: 'SUCCESS', duration: funcDuration]
                                
                            } catch (Exception e) {
                                def funcDuration = (System.currentTimeMillis() - funcStartTime) / 1000
                                env.FUNCTIONAL_TEST_RESULT = 'FAILED'
                                stageResults['Functional Tests'] = [status: 'FAILED', duration: funcDuration]
                                logger.error("Functional tests failed: ${e.getMessage()}")
                            }
                        }
                    } else {
                        logger.info("All functional tests are disabled - skipping")
                        env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                        stageResults['Functional Tests'] = [status: 'SKIPPED', duration: 0]
                    }
                    
                    // Execute parallel tests
                    if (parallelTests.size() > 0) {
                        parallel parallelTests
                    } else {
                        logger.info("No tests are enabled - skipping test execution")
                    }
                    
                    def totalDuration = (System.currentTimeMillis() - startTime) / 1000
                    logger.info("All tests completed in ${totalDuration} seconds")
                }

            } // End of Docker container session
        } // End of Docker registry
    } // End of script block
    
    stage('Generate Reports') {
        def startTime = System.currentTimeMillis()
        script {
            logger.info("GENERATE REPORTS STAGE")
            
            try {
                // Generate and send comprehensive reports
                sendReport.generateAndSendReports(config, stageResults)
                
                def duration = (System.currentTimeMillis() - startTime) / 1000
                stageResults['Generate Reports'] = [status: 'SUCCESS', duration: duration]
                
            } catch (Exception e) {
                def duration = (System.currentTimeMillis() - startTime) / 1000
                stageResults['Generate Reports'] = [status: 'FAILED', duration: duration]
                logger.error("Report generation failed: ${e.getMessage()}")
                // Continue with cleanup even if reporting fails
            }
        }
    }

    stage('Cleanup') {
        def startTime = System.currentTimeMillis()
        script {
            logger.info("CLEANUP STAGE - Cleaning up React Native build artifacts")
            
            try {
                // Cleanup React Native specific artifacts
                mobile_build.cleanReactNativeBuild(config)
                
                // General cleanup
                sh script: '''
                    rm -rf node_modules/.cache/
                    rm -rf android/app/build/intermediates/
                    rm -rf ios/build/
                    rm -rf test-artifacts/
                    rm -f *.jsbundle
                    rm -f android-bundle.js
                '''
                
                def duration = (System.currentTimeMillis() - startTime) / 1000
                logger.info("React Native cleanup completed successfully")
                stageResults['Cleanup'] = [status: 'SUCCESS', duration: duration]
                
            } catch (Exception e) {
                def duration = (System.currentTimeMillis() - startTime) / 1000
                logger.warning("Cleanup failed, but continuing: ${e.getMessage()}")
                stageResults['Cleanup'] = [status: 'WARNING', duration: duration]
            }
        }
    }
    
    // Final pipeline summary
    script {
        logger.info("=== REACT NATIVE PIPELINE SUMMARY ===")
        stageResults.each { stageName, result ->
            logger.info("${stageName}: ${result.status} (${result.duration}s)")
        }
        
        def overallStatus = determineOverallStatus(stageResults)
        logger.info("Overall Pipeline Status: ${overallStatus}")
        
        if (overallStatus == 'FAILED') {
            error("React Native pipeline failed - check individual stage results")
        }
    }
}

/**
 * Run React Native specific linting
 * @param config Pipeline configuration map
 * @return String lint result status
 */
def runReactNativeLint(Map config) {
    logger.info("Running React Native lint checks")
    
    def lintTool = config.tool_for_lint_testing?.react_native ?: 'eslint'
    def results = []
    
    try {
        // Run ESLint
        if (lintTool == 'eslint' || lintTool == 'all') {
            sh script: ReactNativeScript.lintCommand('eslint')
            results.add('eslint: SUCCESS')
        }
        
        // Run Prettier check
        if (lintTool == 'prettier' || lintTool == 'all') {
            sh script: ReactNativeScript.lintCommand('prettier')
            results.add('prettier: SUCCESS')
        }
        
        // Run TypeScript check
        if (lintTool == 'typescript' || lintTool == 'all') {
            if (fileExists('tsconfig.json')) {
                sh script: ReactNativeScript.lintCommand('typescript')
                results.add('typescript: SUCCESS')
            }
        }
        
        logger.info("Lint results: ${results.join(', ')}")
        return 'SUCCESS'
        
    } catch (Exception e) {
        logger.error("Lint failed: ${e.getMessage()}")
        return 'FAILED'
    }
}

/**
 * Run functional tests (smoke, sanity, regression)
 * @param config Pipeline configuration map
 */
def runFunctionalTests(Map config) {
    logger.info("Running functional tests")
    
    def functionalResults = [:]
    
    // Smoke Tests
    if (core_utils.shouldExecuteStage('smoketest', config)) {
        try {
            sh script: ReactNativeScript.smokeTestCommand()
            functionalResults['smoke'] = 'SUCCESS'
        } catch (Exception e) {
            functionalResults['smoke'] = 'FAILED'
            logger.error("Smoke tests failed: ${e.getMessage()}")
        }
    }
    
    // Sanity Tests
    if (core_utils.shouldExecuteStage('sanitytest', config)) {
        try {
            sh script: ReactNativeScript.sanityTestCommand()
            functionalResults['sanity'] = 'SUCCESS'
        } catch (Exception e) {
            functionalResults['sanity'] = 'FAILED'
            logger.error("Sanity tests failed: ${e.getMessage()}")
        }
    }
    
    // Regression Tests
    if (core_utils.shouldExecuteStage('regressiontest', config)) {
        try {
            sh script: ReactNativeScript.regressionTestCommand()
            functionalResults['regression'] = 'SUCCESS'
        } catch (Exception e) {
            functionalResults['regression'] = 'FAILED'
            logger.error("Regression tests failed: ${e.getMessage()}")
        }
    }
    
    // Check if any functional tests failed
    def anyFailed = functionalResults.values().any { it == 'FAILED' }
    if (anyFailed) {
        throw new Exception("Some functional tests failed: ${functionalResults}")
    }
    
    logger.info("All functional tests completed successfully: ${functionalResults}")
}

/**
 * Determine overall pipeline status based on stage results
 * @param stageResults Map of stage results
 * @return String overall status
 */
def determineOverallStatus(Map stageResults) {
    def criticalStages = ['Checkout', 'Install Dependencies', 'Build Apps']
    def failedCritical = criticalStages.any { stage ->
        stageResults[stage]?.status == 'FAILED'
    }
    
    if (failedCritical) {
        return 'FAILED'
    }
    
    def anyFailed = stageResults.values().any { it.status == 'FAILED' }
    if (anyFailed) {
        return 'UNSTABLE'
    }
    
    return 'SUCCESS'
}