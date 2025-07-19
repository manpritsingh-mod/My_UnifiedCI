def call(Map config = [:]) {
    logger.info("Starting Java Gradle Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Execute Gradle-specific pipeline stages
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
            bat './gradlew --version'
        }
    }
    
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('java', 'gradle', config)
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
    
    // Future: Add notification stage
    // stage('Notify') {
    //     script {
    //         notify.sendReport(config)
    //     }
    // }
}
