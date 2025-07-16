def call(Map config = [:]) {
    // Logger.info("Starting Java Maven Template Pipeline")
    echo "Starting Java Maven Template Pipeline"
    echo "Result of the config file ${config}"
    
    // Use default configuration if not passed
    if (!config) {
        // Logger.info("No config provided, using default configuration")
        echo "No config provided, using default configuration"
        config = core_utils.getDefaultConfig()
    }
    
    // Execute Maven-specific pipeline stages
    stage('Checkout') {
        script {
            // Logger.info("CHECKOUT STAGE")
            echo "CHECKOUT STAGE"
            core_github.checkout()
        }
    }
    
    stage('Setup') {
        script {
            // Logger.info("SETUP STAGE")
            echo "SETUP STAGE"
            core_utils.setupProjectEnvironment(config.project_language, config)
            // Use bat for Windows compatibility
            bat 'java -version'
            // bat 'mvn -version'
        }
    }
    
    stage('Install Dependencies') {
        script {
            // Logger.info("INSTALL DEPENDENCIES STAGE")
            echo "INSTALL DEPENDENCIES STAGE"
            core_build.installDependencies('java', 'maven', config)
        }
    }
    
    stage('Lint') {
        when{
                expression{
                    core_utils.shouldExecuteStage('lint', config)
                    echo "Lint are disabled - skipping"
                }
            }
        script {
            // Logger.info("LINTING STAGE")
            echo "LINTING STAGE"
            lint_utils.runLint(config)
        }
    }
    
    stage('Build') {
        script {
            // Logger.info("BUILDING STAGE")
            echo "BUILDING STAGE"
            core_build.buildLanguages(config.project_language, config)
        }
    }
    
    stage('Unit Test') {
        when{
                expression{
                    core_utils.shouldExecuteStage('unittest', config)
                    echo "Unit tests are disabled - skipping"
                }
            }
        script {
            // Logger.info("UNIT-TEST STAGE")
            echo "UNIT-TEST STAGE"
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
