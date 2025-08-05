/**
 * Java Maven Pipeline Template - Executes complete Maven-based Java CI/CD pipeline
 * Handles Maven project build, test, lint, and reporting with comprehensive error handling
 * @param config Pipeline configuration map (optional, uses defaults if not provided)
 * Usage: javaMaven_template() or javaMaven_template([project_language: 'java-maven', runLintTests: false])
 */
def call(Map config = [:]) {
    logger.info("Starting Java Maven Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Initialize stage results tracking for email reporting
    def stageResults = [:]
    
    // Execute Maven-specific pipeline stages
    stage('Checkout') {
        script {
            logger.info("CHECKOUT STAGE")
            core_github.checkout()
            stageResults['Checkout'] = 'SUCCESS'
        }
    }
    
    stage('Setup') {
        script {
            logger.info("SETUP STAGE")
            core_utils.setupProjectEnvironment(config.project_language, config)
            bat 'java -version'
            // sh 'java -version'  // Linux equivalent
            bat 'mvn -version'
            // sh 'mvn -version'   // Linux equivalent
            stageResults['Setup'] = 'SUCCESS'
        }
    }
    
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('java', 'maven', config)
            stageResults['Install Dependencies'] = 'SUCCESS'
        }
    }
    
    stage('Lint') {
        if (core_utils.shouldExecuteStage('lint', config)) {
            script {
                logger.info("LINTING STAGE")
                def lintResult = lint_utils.runLint(config)
                env.LINT_RESULT = lintResult
                stageResults['Lint'] = lintResult
                logger.info("Lint stage completed with result: ${lintResult}")
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
            logger.info("BUILDING STAGE")
            core_build.buildLanguages(config.project_language, config)
            stageResults['Build'] = 'SUCCESS'
        }
    }
    
    stage('Unit Test') {
        if (core_utils.shouldExecuteStage('unittest', config)) {
            script {
                logger.info("UNIT-TEST STAGE")
                def testResult = core_test.runUnitTest(config)
                env.UNIT_TEST_RESULT = testResult
                stageResults['Unit Test'] = testResult
                logger.info("Unit test stage completed with result: ${testResult}")
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
            logger.info("GENERATE REPORTS STAGE")
            
            // Generate Allure report and send email summary with dynamic stage results
            sendReport.generateAndSendReports(config, stageResults)
            stageResults['Generate Reports'] = 'SUCCESS'
        }
    }
}