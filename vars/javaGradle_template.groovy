def call(Map config = [:]) {
    logger.info("Starting Java Gradle Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Initialize stage results tracking
    def stageResults = [:]
    
    // Execute Gradle-specific pipeline stages
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
            // Use bat for Windows compatibility
            bat script: GradleScript.javaVersionCommand()
            // sh script: GradleScript.javaVersionCommand()     // Linux equivalent
            bat script: GradleScript.gradleVersionCommand()
            // sh script: GradleScript.gradleVersionCommand() // Linux equivalent
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
            
            // Add Unit Test as first parallel branch
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
            
            // Add Serial Functional Tests as second parallel branch
            if (core_utils.shouldExecuteStage('functionaltest', config) || 
                core_utils.shouldExecuteStage('smoketest', config) || 
                core_utils.shouldExecuteStage('sanitytest', config) || 
                core_utils.shouldExecuteStage('regressiontest', config)) {
                
                parallelTests['Smoke → Sanity → Regression'] = {
                    logger.info("Starting Serial Functional Tests")
                    
                    // Stage 1: Smoke Tests
                    if (core_utils.shouldExecuteStage('smoketest', config)) {
                        logger.info("Stage 1: Running Smoke Tests")
                        bat script: GradleScript.smokeTestCommand()
                        // sh script: GradleScript.smokeTestCommand()  // Linux equivalent
                        env.SMOKE_TEST_RESULT = 'SUCCESS'
                        stageResults['Smoke Tests'] = 'SUCCESS'
                        logger.info("Smoke Tests completed successfully")
                    } else {
                        logger.info("Stage 1: Smoke Tests are disabled - skipping")
                        env.SMOKE_TEST_RESULT = 'SKIPPED'
                        stageResults['Smoke Tests'] = 'SKIPPED'
                    }
                    
                    // Stage 2: Sanity Tests (runs after Smoke)
                    if (core_utils.shouldExecuteStage('sanitytest', config)) {
                        logger.info("Stage 2: Running Sanity Tests")
                        bat script: GradleScript.sanityTestCommand()
                        // sh script: GradleScript.sanityTestCommand()  // Linux equivalent
                        env.SANITY_TEST_RESULT = 'SUCCESS'
                        stageResults['Sanity Tests'] = 'SUCCESS'
                        logger.info("Sanity Tests completed successfully")
                    } else {
                        logger.info("Stage 2: Sanity Tests are disabled - skipping")
                        env.SANITY_TEST_RESULT = 'SKIPPED'
                        stageResults['Sanity Tests'] = 'SKIPPED'
                    }
                    
                    // Stage 3: Regression Tests (runs after Sanity)
                    if (core_utils.shouldExecuteStage('regressiontest', config)) {
                        logger.info("Stage 3: Running Regression Tests")
                        bat script: GradleScript.regressionTestCommand()
                        // sh script: GradleScript.regressionTestCommand()  // Linux equivalent
                        env.REGRESSION_TEST_RESULT = 'SUCCESS'
                        stageResults['Regression Tests'] = 'SUCCESS'
                        logger.info("Regression Tests completed successfully")
                    } else {
                        logger.info("Stage 3: Regression Tests are disabled - skipping")
                        env.REGRESSION_TEST_RESULT = 'SKIPPED'
                        stageResults['Regression Tests'] = 'SKIPPED'
                    }
                    
                    env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                    logger.info("Serial Functional Tests completed: Smoke → Sanity → Regression")
                }
            } else {
                logger.info("All functional tests are disabled - skipping")
                env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                stageResults['Functional Tests'] = 'SKIPPED'
            }
            
            // Execute parallel tests if any are enabled
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
            
            // Generate Allure report and send email summary with dynamic stage results
            sendReport.generateAndSendReports(config, stageResults)
            stageResults['Generate Reports'] = 'SUCCESS'
        }
    }
}
