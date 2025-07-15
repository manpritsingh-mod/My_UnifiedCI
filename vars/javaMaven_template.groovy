def call(Map config = [:]) {
    Logger.info("Starting Java Maven Template Pipeline")
    
    // Get configuration from environment variable if not passed
    if (!config) {
        if (env.PROJECT_CONFIG && env.PROJECT_CONFIG.trim() != '') {
                config = readJSON text: env.PROJECT_CONFIG
            } else {
                Logger.warning("PROJECT_CONFIG environment variable is empty or not set")
                config = core_utils.getDefaultConfig()
            }
        } catch (Exception e) {
            Logger.error("Failed to parse PROJECT_CONFIG JSON: ${e.getMessage()}")
            config = core_utils.getDefaultConfig()
        }
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
