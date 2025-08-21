def call(Map config = [:]) {
    logger.info("Starting Java Gradle Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }

    if (!config.nexus?.registry || !config.nexus?.credentials_id || !config.nexus?.url) {
        logger.error("Missing Nexus configuration. Please give nexus.registry, nexus.url and nexus.credentials_id")
    }
    
    // Initilize stage results for tracking 
    def stageResults = [:]
    
    stage('Checkout') {
        script {
                logger.info("CHECKOUT STAGE")
                core_github.checkout()
                stageResults['Checkout'] = 'SUCCESS'
        }
    }
    
    stage('Setup') {
        script {
                logger.info("SETUP STAGE")
                core_utils.setupProjectEnvironment(config.project_language, config)
                sh script: GradleScript.javaVersionCommand()
                sh script: GradleScript.gradleVersionCommand()
                stageResults['Setup'] = 'SUCCESS'
        }
    }
    
    stage('Install Dependencies') {
        script {
                logger.info("INSTALL DEPENDENCIES STAGE")
                core_build.installDependencies('java', 'gradle', config)
                stageResults['Install Dependencies'] = 'SUCCESS'
        }
    }
    
    stage('Lint') {
        if (core_utils.shouldExecuteStage('lint', config)) {
            script {
                logger.info("LINTING STAGE")
                def lintResult = lint_utils.runLint(config)
                env.LINT_RESULT = lintResult
                stageResults['Lint'] = lintResult
                logger.info("Lint stage completed with result: ${lintResult}")
            }
        } else {
            script {
                logger.info("Linting is disabled - skipping")
                env.LINT_RESULT = 'SKIPPED'
                stageResults['Lint'] = 'SKIPPED'
            }
        }
    }
    
    
    stage('Build') {
        script {
                logger.info("BUILDING STAGE")
                core_build.buildLanguages(config.project_language, config)
                stageResults['Build'] = 'SUCCESS'
        }
    }
    
    stage('Test Execution') {
            script {
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
               
                        stage('Smoke Tests') {
                            if (core_utils.shouldExecuteStage('smoketest', config)) {
                                logger.info("Running Smoke Tests")
                                sh script: GradleScript.smokeTestCommand() 
                                env.SMOKE_TEST_RESULT = 'SUCCESS'
                                stageResults['Smoke Tests'] = 'SUCCESS'
                                logger.info("Smoke Tests completed successfully")
                            } else {
                                logger.info("Smoke Tests are disabled - skipping")
                                env.SMOKE_TEST_RESULT = 'SKIPPED'
                                stageResults['Smoke Tests'] = 'SKIPPED'
                            }
                        }
                        
                        // Individual Stage: Sanity Tests (runs after Smoke)
                        stage('Sanity Tests') {
                            if (core_utils.shouldExecuteStage('sanitytest', config)) {
                                logger.info("Running Sanity Tests")
                                sh script: GradleScript.sanityTestCommand() 
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
                                sh script: GradleScript.regressionTestCommand()
                                env.REGRESSION_TEST_RESULT = 'SUCCESS'
                                stageResults['Regression Tests'] = 'SUCCESS'
                                logger.info("Regression Tests completed successfully")
                            } else {
                                logger.info("Regression Tests are disabled - skipping")
                                env.REGRESSION_TEST_RESULT = 'SKIPPED'
                                stageResults['Regression Tests'] = 'SKIPPED'
                            }
                        }
                        
                        env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                        logger.info("All Functional Test Stages completed")
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
        }
    
    stage('Generate Reports') {
        script {
                logger.info("GENERATE REPORTS STAGE")
                sendReport.generateAndSendReports(config, stageResults)
                stageResults['Generate Reports'] = 'SUCCESS'
        
        }
    }
}
