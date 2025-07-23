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
            lint_utils.runLint(config)
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
    
    stage('Generate Reports') {
        script {
            logger.info("GENERATE REPORTS STAGE")
            
            // Generate Allure report and send email summary
            sendReport.generateAndSendReports(config, [
                'Checkout': 'SUCCESS',
                'Setup': 'SUCCESS',
                'Install Dependencies': 'SUCCESS', 
                'Lint': 'SUCCESS',
                'Build': 'SUCCESS',
                'Unit Test': 'SUCCESS'
            ])
        }
    }
}