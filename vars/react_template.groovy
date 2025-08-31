/**
 * React Pipeline Template - Executes complete React CI/CD pipeline
 * Handles React project build, test, lint, and reporting with comprehensive error handling
 * @param config Pipeline configuration map (optional, uses defaults if not provided)
 * Usage: react_template() or react_template([project_language: 'react'])
 */
def call(Map config = [:]) {
    logger.info("Starting React Template Pipeline")
    
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
            stageResults['Checkout'] = 'SUCCESS'
        }
    }

    // Pull Docker image and run ALL stages inside single container session
    script {
        logger.info("PULLING NODE.JS IMAGE FROM NEXUS")
        
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
            }
            
            logger.info("✅ Node.js image ready!")
            env.REACT_DOCKER_IMAGE = imageConfig.imagePath
            stageResults['Pull Image'] = 'SUCCESS'
            
            // Run ALL stages inside this single Docker container session
            image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
                
                stage('Setup') {
                    logger.info("SETUP STAGE")
                    core_utils.setupProjectEnvironment(config.project_language, config)
                    sh script: ReactScript.nodeVersionCommand()
                    stageResults['Setup'] = 'SUCCESS'
                }

                stage('Install Dependencies') {
                    logger.info("INSTALL DEPENDENCIES STAGE")
                    def result = core_build.installDependencies('react', 'npm', config)
                    
                    if (result) {
                        stageResults['Install Dependencies'] = 'SUCCESS'
                    } else {
                        stageResults['Install Dependencies'] = 'FAILED'
                        logger.error("Dependency installation failed")
                    }
                }

                stage('Lint') {
                    if (core_utils.shouldExecuteStage('lint', config)) {
                        logger.info("LINTING STAGE")
                        def lintResult = lint_utils.runLint(config)
                        env.LINT_RESULT = lintResult
                        stageResults['Lint'] = lintResult
                        logger.info("Lint stage completed with result: ${lintResult}")
                    } else {
                        logger.info("Linting is disabled - skipping")
                        env.LINT_RESULT = 'SKIPPED'
                        stageResults['Lint'] = 'SKIPPED'
                    }
                }

                stage('Build') {
                    logger.info("BUILDING STAGE")
                    def result = core_build.buildLanguages(config.project_language, config)

                    if (result) {
                        stageResults['Build'] = 'SUCCESS'
                    } else {
                        stageResults['Build'] = 'FAILED'
                        logger.error("Build failed")
                    }
                }

                stage('Test Execution') {
                    def parallelTests = [:]
                    
                    if (core_utils.shouldExecuteStage('unittest', config)) {
                        parallelTests['Unit Test'] = {
                            logger.info("Running Unit Tests")
                            def testResult = core_test.runUnitTest(config)
                            env.UNIT_TEST_RESULT = testResult
                            stageResults['Unit Test'] = testResult
                            logger.info("Unit test stage completed with result: ${testResult}")
                        }
                    } else {
                        logger.info("Unit testing is disabled - skipping")
                        env.UNIT_TEST_RESULT = 'SKIPPED'
                        stageResults['Unit Test'] = 'SKIPPED'
                    }
                    
                    if (core_utils.shouldExecuteStage('functionaltest', config) || 
                        core_utils.shouldExecuteStage('smoketest', config) || 
                        core_utils.shouldExecuteStage('sanitytest', config) || 
                        core_utils.shouldExecuteStage('regressiontest', config)) {
                        
                        parallelTests['Functional Tests'] = {
                            logger.info("Starting Functional Tests with Individual Stages")
                            
                            // Functional tests run in same container - no additional Docker calls
                            stage('Smoke Tests') {
                                if (core_utils.shouldExecuteStage('smoketest', config)) {
                                    logger.info("Running Smoke Tests")
                                    sh script: ReactScript.smokeTestCommand()
                                    env.SMOKE_TEST_RESULT = 'SUCCESS'
                                    stageResults['Smoke Tests'] = 'SUCCESS'
                                    logger.info("Smoke Tests completed successfully")
                                } else {
                                    logger.info("Smoke Tests are disabled - skipping")
                                    env.SMOKE_TEST_RESULT = 'SKIPPED'
                                    stageResults['Smoke Tests'] = 'SKIPPED'
                                }
                            }
                            
                            stage('Sanity Tests') {
                                if (core_utils.shouldExecuteStage('sanitytest', config)) {
                                    logger.info("Running Sanity Tests")
                                    sh script: ReactScript.sanityTestCommand()
                                    env.SANITY_TEST_RESULT = 'SUCCESS'
                                    stageResults['Sanity Tests'] = 'SUCCESS'
                                    logger.info("Sanity Tests completed successfully")
                                } else {
                                    logger.info("Sanity Tests are disabled - skipping")
                                    env.SANITY_TEST_RESULT = 'SKIPPED'
                                    stageResults['Sanity Tests'] = 'SKIPPED'
                                }
                            }
                            
                            stage('Regression Tests') {
                                if (core_utils.shouldExecuteStage('regressiontest', config)) {
                                    logger.info("Running Regression Tests")
                                    sh script: ReactScript.regressionTestCommand()
                                    env.REGRESSION_TEST_RESULT = 'SUCCESS'
                                    stageResults['Regression Tests'] = 'SUCCESS'
                                    logger.info("Regression Tests completed successfully")
                                } else {
                                    logger.info("Regression Tests are disabled - skipping")
                                    env.REGRESSION_TEST_RESULT = 'SKIPPED'
                                    stageResults['Regression Tests'] = 'SKIPPED'
                                }
                            }
                            
                            // Determine overall functional test result
                            def testResults = [env.SMOKE_TEST_RESULT, env.SANITY_TEST_RESULT, env.REGRESSION_TEST_RESULT]
                            def allPassed = testResults.every { it == 'SUCCESS' || it == 'SKIPPED' }
                            def anyFailed = testResults.any { it == 'FAILED' }
                            
                            if (anyFailed) {
                                env.FUNCTIONAL_TEST_RESULT = 'FAILED'
                            } else if (allPassed && testResults.any { it == 'SUCCESS' }) {
                                env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                            } else {
                                env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                            }

                            logger.info("All Functional Test Stages completed with result: ${env.FUNCTIONAL_TEST_RESULT}")
                        }
                    } else {
                        logger.info("All functional tests are disabled - skipping")
                        env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                        stageResults['Functional Tests'] = 'SKIPPED'
                    }
                    
                    if (parallelTests.size() > 0) {
                        parallel parallelTests
                    } else {
                        logger.info("No tests are enabled - skipping test execution")
                    }
                }


            } // End of Docker container session
        } // End of Docker registry
    } // End of script block
    
    stage('Generate Reports') {
        script {
            logger.info("GENERATE REPORTS STAGE")
            // Generate and send email summary
            sendReport.generateAndSendReports(config, stageResults)
            stageResults['Generate Reports'] = 'SUCCESS'
        }
    }

    stage('Cleanup') {
        script {
            logger.info("CLEANUP STAGE - Cleaning up React build artifacts")
            
            try {
                // Cleanup build artifacts and cache
                sh script: "rm -rf build/ dist/ coverage/ node_modules/.cache/"
                
                logger.info("React cleanup completed successfully")
                stageResults['Cleanup'] = 'SUCCESS'
            } catch (Exception e) {
                logger.warning("Cleanup failed, but continuing: ${e.getMessage()}")
                stageResults['Cleanup'] = 'WARNING'
            }
        }
    }
}

