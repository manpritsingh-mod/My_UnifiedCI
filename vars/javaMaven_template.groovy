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
        if (core_utils.shouldExecuteStage('lint', config)) {
            echo "LINTING STAGE"
            lint_utils.runLint(config)
        } else {
            echo "Linting is disabled - skipping"
        }
    }
    
    stage('Build') {
        script {
            logger.info("BUILDING STAGE")
            core_build.buildLanguages(config.project_language, config)
        }
    }
    
    stage('Unit Test') {
        if (core_utils.shouldExecuteStage('unittest', config)) {
            echo "UNIT-TEST STAGE"
            core_test.runUnitTest(config)
        } else {
            echo "Unit test is disabled - skipping"
        }
    }
    
    // Future: Add notification stage
    // stage('Notify') {
    //     script {
    //         notify.sendReport(config)
    //     }
    // }
}
