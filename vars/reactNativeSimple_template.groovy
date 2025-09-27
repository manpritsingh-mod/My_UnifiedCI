/**
 * Simple React Native Pipeline Template - Physical Devices Only
 * Focuses on real device testing without emulators or device farms
 * Teams simply connect their physical devices via USB
 * @param config Pipeline configuration map
 */
def call(Map config = [:]) {
    logger.info("Starting Simple React Native Pipeline (Physical Devices Only)")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }

    // Validate configuration
    def validation = DockerImageManager.validateDockerConfig(config)
    if (!validation.valid) {
        error "Configuration validation failed: ${validation.message}"
    }

    // Initialize stage results tracking
    def stageResults = [:]

    stage('Checkout') {
        script {
            logger.info("CHECKOUT STAGE")
            core_github.checkout()
            stageResults['Checkout'] = [status: 'SUCCESS', duration: 0]
        }
    }

    // Pull Docker image and run stages inside container
    script {
        logger.info("PULLING REACT NATIVE IMAGE FROM NEXUS")
        
        def imageConfig = DockerImageManager.getImageConfig(config.project_language, config)
        logger.info("Pulling Docker image: ${imageConfig.imagePath}")
        
        docker.withRegistry(imageConfig.registryUrl, imageConfig.credentialsId) {
            def image = docker.image(imageConfig.imagePath)
            
            // Pull image with retry
            retry(3) {
                try {
                    image.pull()
                } catch (Exception e) {
                    logger.warning("Image pull attempt failed: ${e.getMessage()}")
                    sleep(5)
                    throw e
                }
            }
            
            logger.info("✅ React Native image ready!")
            stageResults['Pull Image'] = [status: 'SUCCESS', duration: 0]
            
            // Run stages inside Docker container with USB device access
            image.inside("-v ${WORKSPACE}:/workspace -w /workspace --privileged -v /dev/bus/usb:/dev/bus/usb") {
                
                stage('Setup Environment') {
                    def startTime = System.currentTimeMillis()
                    logger.info("SETUP ENVIRONMENT STAGE")
                    
                    try {
                        // Setup project environment
                        core_utils.setupProjectEnvironment(config.project_language, config)
                        
                        // Display environment info
                        sh script: ReactNativeScript.versionCommand()
                        
                        // Setup Android environment for device testing
                        sh script: ReactNativeScript.setupAndroidEnvironmentCommand()
                        
                        // Display connected devices
                        physical_device_test.displayDeviceSummary()
                        
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Setup Environment'] = [status: 'SUCCESS', duration: duration]
                        
                    } catch (Exception e) {
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Setup Environment'] = [status: 'FAILED', duration: duration]
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
                            
                        } catch (Exception e) {
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            stageResults['Lint'] = [status: 'FAILED', duration: duration]
                            logger.error("Lint stage failed: ${e.getMessage()}")
                        }
                    } else {
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
                        } else {
                            stageResults['Build Apps'] = [status: 'FAILED', duration: duration]
                            error("App build failed")
                        }
                    } catch (Exception e) {
                        def duration = (System.currentTimeMillis() - startTime) / 1000
                        stageResults['Build Apps'] = [status: 'FAILED', duration: duration]
                        throw e
                    }
                }

                stage('Unit Tests') {
                    def startTime = System.currentTimeMillis()
                    if (core_utils.shouldExecuteStage('unittest', config)) {
                        logger.info("RUNNING UNIT TESTS")
                        
                        try {
                            def testResult = mobile_test.runUnitTests(config)
                            env.UNIT_TEST_RESULT = testResult ? 'SUCCESS' : 'FAILED'
                            
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            stageResults['Unit Tests'] = [status: env.UNIT_TEST_RESULT, duration: duration]
                            
                        } catch (Exception e) {
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            env.UNIT_TEST_RESULT = 'FAILED'
                            stageResults['Unit Tests'] = [status: 'FAILED', duration: duration]
                            logger.error("Unit tests failed: ${e.getMessage()}")
                        }
                    } else {
                        stageResults['Unit Tests'] = [status: 'SKIPPED', duration: 0]
                    }
                }

                stage('Physical Device Tests') {
                    def startTime = System.currentTimeMillis()
                    if (core_utils.shouldExecuteStage('device_test', config)) {
                        logger.info("RUNNING TESTS ON PHYSICAL DEVICES")
                        
                        try {
                            def testResult = physical_device_test.runTestsOnPhysicalDevices(config)
                            env.DEVICE_TEST_RESULT = testResult ? 'SUCCESS' : 'FAILED'
                            
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            stageResults['Physical Device Tests'] = [status: env.DEVICE_TEST_RESULT, duration: duration]
                            
                            logger.info("Physical device tests completed: ${env.DEVICE_TEST_RESULT}")
                            
                        } catch (Exception e) {
                            def duration = (System.currentTimeMillis() - startTime) / 1000
                            env.DEVICE_TEST_RESULT = 'FAILED'
                            stageResults['Physical Device Tests'] = [status: 'FAILED', duration: duration]
                            logger.error("Physical device tests failed: ${e.getMessage()}")
                        }
                    } else {
                        logger.info("Physical device testing is disabled - skipping")
                        stageResults['Physical Device Tests'] = [status: 'SKIPPED', duration: 0]
                    }
                }

            } // End of Docker container session
        } // End of Docker registry
    } // End of script block
    
    stage('Generate Reports') {
        def startTime = System.currentTimeMillis()
        script {
            logger.info("GENERATE REPORTS STAGE")
            
            try {
                // Archive test artifacts
                archiveArtifacts artifacts: 'test-artifacts/**/*', allowEmptyArchive: true
                
                // Generate and send reports
                sendReport.generateAndSendReports(config, stageResults)
                
                def duration = (System.currentTimeMillis() - startTime) / 1000
                stageResults['Generate Reports'] = [status: 'SUCCESS', duration: duration]
                
            } catch (Exception e) {
                def duration = (System.currentTimeMillis() - startTime) / 1000
                stageResults['Generate Reports'] = [status: 'FAILED', duration: duration]
                logger.error("Report generation failed: ${e.getMessage()}")
            }
        }
    }

    stage('Cleanup') {
        def startTime = System.currentTimeMillis()
        script {
            logger.info("CLEANUP STAGE")
            
            try {
                // Cleanup build artifacts
                sh script: '''
                    rm -rf node_modules/.cache/
                    rm -rf android/app/build/intermediates/
                    rm -rf ios/build/
                    rm -f *.jsbundle
                '''
                
                def duration = (System.currentTimeMillis() - startTime) / 1000
                stageResults['Cleanup'] = [status: 'SUCCESS', duration: duration]
                
            } catch (Exception e) {
                def duration = (System.currentTimeMillis() - startTime) / 1000
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
        
        // Display device test summary
        if (stageResults['Physical Device Tests']?.status == 'SUCCESS') {
            logger.info("✅ Tests passed on connected physical devices")
        } else if (stageResults['Physical Device Tests']?.status == 'SKIPPED') {
            logger.info("ℹ️  No physical devices connected - device tests skipped")
        } else {
            logger.info("❌ Physical device tests failed - check device connections")
        }
    }
}

/**
 * Run React Native linting
 * @param config Pipeline configuration map
 * @return String lint result status
 */
def runReactNativeLint(Map config) {
    try {
        def lintTool = config.tool_for_lint_testing?.react_native ?: 'eslint'
        sh script: ReactNativeScript.lintCommand(lintTool)
        return 'SUCCESS'
    } catch (Exception e) {
        logger.error("Lint failed: ${e.getMessage()}")
        return 'FAILED'
    }
}

/**
 * Determine overall pipeline status
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