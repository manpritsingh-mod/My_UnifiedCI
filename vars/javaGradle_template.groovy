import Logger

def call(Map config = [:]) {
    Logger.info("Starting Java Gradle Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        Logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Execute Gradle-specific pipeline stages
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
            bat './gradlew --version'
        }
    }
    
    stage('Install Dependencies') {
        script {
            Logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('java', 'gradle', config)
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
