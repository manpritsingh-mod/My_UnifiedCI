def call(Map config = [:]) {
    logger.info("Starting Java Maven Template Pipeline")
    echo "Result of the config file ${config}"
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Execute Maven-specific pipeline stages
    stage('Checkout') {
        script {
            logger.info("CHECKOUT STAGE")
            core_github.checkout()
        }
    }
    
    stage('Setup') {
        script {
            logger.info("SETUP STAGE")
            core_utils.setupProjectEnvironment(config.project_language, config)
            // Use bat for Windows compatibility
            bat 'java -version'
            bat 'mvn -version'
        }
    }
    
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('java', 'maven', config)
        }
    }
    
    stage('Lint') {
        script {
            if (core_utils.shouldExecuteStage('lint', config)) {
                logger.info("LINTING STAGE")
                
                try {
                    def lintResult = lint_utils.runLint(config)
                    
                    // Store lint result for reporting
                    env.LINT_STATUS = lintResult.status
                    env.LINT_MESSAGE = lintResult.message
                    
                    if (lintResult.status in ['FAILED', 'UNSTABLE']) {
                        logger.warning("Lint issues found but continuing build (non-critical)")
                        currentBuild.result = 'UNSTABLE'
                        env.LINT_STATUS = 'UNSTABLE'  // Always mark as UNSTABLE, never FAILED
                    } else {
                        logger.info("Lint completed successfully")
                    }
                    
                } catch (Exception e) {
                    logger.error("Lint execution failed: ${e.getMessage()}")
                    env.LINT_STATUS = 'UNSTABLE'
                    env.LINT_MESSAGE = "Lint execution failed: ${e.getMessage()}"
                    currentBuild.result = 'UNSTABLE'
                    logger.warning("Lint failed but continuing build")
                }
            } else {
                logger.info("Linting is disabled - skipping")
                env.LINT_STATUS = 'SKIPPED'
            }
        }
    }
    
    stage('Build') {
        script {
            logger.info("BUILDING STAGE")
            core_build.buildLanguages(config.project_language, config)
        }
    }
    
    stage('Unit Test') {
        script {
            if (core_utils.shouldExecuteStage('unittest', config)) {
                logger.info("UNIT-TEST STAGE")
                def testResult = core_test.runUnitTest(config)
                
                // Store test result for reporting
                env.TEST_STATUS = testResult.status
                env.TEST_MESSAGE = testResult.message
                env.TEST_DETAILS = testResult.details ?: "No test details available"
                
                if (testResult.status == 'UNSTABLE') {
                    logger.warning("Unit tests failed but marked as non-critical")
                    currentBuild.result = 'UNSTABLE'
                } else if (testResult.status == 'FAILED') {
                    logger.error("Unit tests failed critically")
                    error("Unit test stage failed: ${testResult.message}")
                } else {
                    logger.info("Unit tests completed successfully")
                }
            } else {
                logger.info("Unit testing is disabled - skipping")
                env.TEST_STATUS = 'SKIPPED'
            }
        }
    }
    
    // Detailed Reporting and Notification Stage
    stage('Generate Reports') {
        script {
            logger.info("GENERATING DETAILED REPORTS")
            
            // Collect all stage results
            def stageResults = [
                'Checkout': 'SUCCESS',
                'Setup': 'SUCCESS',
                'Install Dependencies': 'SUCCESS',
                'Lint': env.LINT_STATUS ?: 'SUCCESS',
                'Build': 'SUCCESS',
                'Unit Test': 'SUCCESS'  // Will be enhanced later
            ]
            
            // Generate and send detailed reports
            sendReport.generateAndSendReports(config, stageResults)
            
            logger.info("Detailed reports generated and sent")
        }
    }
}
