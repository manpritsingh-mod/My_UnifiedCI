def call(Map config = [:]) {
    logger.info("Starting Java Maven Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Initialize stage results tracking
    def stageResults = [:]
    
    // Execute Maven-specific pipeline stages
    stage('Checkout') {
        script {
            try {
                logger.info("CHECKOUT STAGE")
                core_github.checkout()
                stageResults['Checkout'] = 'SUCCESS'
            } catch (Exception e) {
                logger.error("Checkout stage failed: ${e.getMessage()}")
                stageResults['Checkout'] = 'FAILURE'
                throw e
            }
        }
    }
    
    stage('Setup') {
        script {
            try {
                logger.info("SETUP STAGE")
                core_utils.setupProjectEnvironment(config.project_language, config)
                bat 'java -version'
                bat 'mvn -version'
                stageResults['Setup'] = 'SUCCESS'
            } catch (Exception e) {
                logger.error("Setup stage failed: ${e.getMessage()}")
                stageResults['Setup'] = 'FAILURE'
                throw e
            }
        }
    }
    
    stage('Install Dependencies') {
        script {
            try {
                logger.info("INSTALL DEPENDENCIES STAGE")
                core_build.installDependencies('java', 'maven', config)
                stageResults['Install Dependencies'] = 'SUCCESS'
            } catch (Exception e) {
                logger.error("Install Dependencies stage failed: ${e.getMessage()}")
                stageResults['Install Dependencies'] = 'FAILURE'
                throw e
            }
        }
    }
    
    stage('Lint') {
        if (core_utils.shouldExecuteStage('lint', config)) {
            script {
                try {
                    logger.info("LINTING STAGE")
                    def lintResult = lint_utils.runLint(config)
                    env.LINT_RESULT = lintResult
                    stageResults['Lint'] = lintResult
                    logger.info("Lint stage completed with result: ${lintResult}")
                } catch (Exception e) {
                    logger.error("Lint stage failed: ${e.getMessage()}")
                    env.LINT_RESULT = 'FAILURE'
                    stageResults['Lint'] = 'FAILURE'
                    throw e
                }
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
            try {
                logger.info("BUILDING STAGE")
                core_build.buildLanguages(config.project_language, config)
                stageResults['Build'] = 'SUCCESS'
            } catch (Exception e) {
                logger.error("Build stage failed: ${e.getMessage()}")
                stageResults['Build'] = 'FAILURE'
                throw e
            }
        }
    }
    
    stage('Unit Test') {
        if (core_utils.shouldExecuteStage('unittest', config)) {
            script {
                try {
                    logger.info("UNIT-TEST STAGE")
                    def testResult = core_test.runUnitTest(config)
                    env.UNIT_TEST_RESULT = testResult
                    stageResults['Unit Test'] = testResult
                    logger.info("Unit test stage completed with result: ${testResult}")
                } catch (Exception e) {
                    logger.error("Unit Test stage failed: ${e.getMessage()}")
                    env.UNIT_TEST_RESULT = 'FAILURE'
                    stageResults['Unit Test'] = 'FAILURE'
                    throw e
                }
            }
        } else {
            script {
                logger.info("Unit testing is disabled - skipping")
                env.UNIT_TEST_RESULT = 'SKIPPED'
                stageResults['Unit Test'] = 'SKIPPED'
            }
        }
    }
    
    stage('Generate Reports') {
        script {
            try {
                logger.info("GENERATE REPORTS STAGE")
                
                // Generate Allure report and send email summary with dynamic stage results
                sendReport.generateAndSendReports(config, stageResults)
                stageResults['Generate Reports'] = 'SUCCESS'
            } catch (Exception e) {
                logger.error("Generate Reports stage failed: ${e.getMessage()}")
                stageResults['Generate Reports'] = 'FAILURE'
                // Don't throw here as we still want to send notifications
            }
        }
    }
}