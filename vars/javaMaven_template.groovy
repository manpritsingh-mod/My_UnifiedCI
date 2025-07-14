def call(Map config = [:]) {
    Logger.info("Starting Java Maven Template Pipeline")
    
    // Get configuration from environment variable if not passed
    if (!config) {
        config = readJSON text: env.PROJECT_CONFIG
    }
    
    // Execute Maven-specific pipeline stages
    stage('Checkout') {
        script {
            Logger.info("CHECKOUT STAGE")
            core_github.checkout()
        }
    }
    
    stage('Setup') {
        script {
            Logger.info("SETUP STAGE")
            core_utils.setupProjectEnvironment(config.project_language, config)
            // Use bat for Windows compatibility
            bat 'java -version'
            bat 'mvn -version'
        }
    }
    
    stage('Install Dependencies') {
        script {
            Logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('java', 'maven', config)
        }
    }
    
    stage('Lint') {
        when {
            expression {
                return core_utils.shouldExecuteStage('lint', config)
            }
        }
        script {
            Logger.info("LINTING STAGE")
            lint_utils.runLint(config)
        }
    }
    
    stage('Build') {
        script {
            Logger.info("BUILDING STAGE")
            core_build.buildLanguages(config.project_language, config)
        }
    }
    
    stage('Unit Test') {
        when {
            expression {
                return core_utils.shouldExecuteStage('unittest', config)
            }
        }
        script {
            Logger.info("UNIT-TEST STAGE")
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
