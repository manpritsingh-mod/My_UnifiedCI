def call(Map config = [:]) {
    logger.info("Starting Python Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Execute Python-specific pipeline stages
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
            bat 'python --version'
            bat 'pip --version'
        }
    }
    
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('python', 'pip', config)
        }
    }
    
    stage('Lint') {
        script {
            logger.info("LINTING STAGE")
            def lintResult = lint_utils.runLint(config)
            
            // Store lint result for reporting
            env.LINT_STATUS = lintResult.status
            env.LINT_MESSAGE = lintResult.message
            
            if (lintResult.status == 'UNSTABLE') {
                logger.warning("Lint completed with violations but marked as non-critical")
                currentBuild.result = 'UNSTABLE'
            } else if (lintResult.status == 'FAILED') {
                logger.error("Lint failed critically")
                error("Lint stage failed: ${lintResult.message}")
            } else {
                logger.info("Lint completed successfully")
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
            logger.info("UNIT-TEST STAGE")
            core_test.runUnitTest(config)
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
