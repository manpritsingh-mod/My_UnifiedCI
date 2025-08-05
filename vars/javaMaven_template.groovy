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
            bat script: MavenScript.javaVersionCommand()
            // sh script: MavenScript.javaVersionCommand()  // Linux equivalent
            bat script: MavenScript.mavenVersionCommand()
            // sh script: MavenScript.mavenVersionCommand()   // Linux equivalent
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
    
    stage('Test Execution') {
        script {
            def parallelTests = [:]
            
            // Add Unit Test as first parallel branch
            if (core_utils.shouldExecuteStage('unittest', config)) {
                parallelTests['Unit Test'] = {
                    logger.info("Running Unit Tests")
                    def testResult = core_test.runUnitTest(config)
                    env.UNIT_TEST_RESULT = testResult
                    stageResults['Unit Test'] = testResult
                    logger.info("Unit test stage completed with result: ${testResult}")
                }
            } else {
                logger.info("Unit testing is disabled - skipping")
                env.UNIT_TEST_RESULT = 'SKIPPED'
                stageResults['Unit Test'] = 'SKIPPED'
            }
            
            // Add individual functional tests as separate parallel branches
            if (core_utils.shouldExecuteStage('smoketest', config)) {
                parallelTests['Smoke Tests'] = {
                    logger.info("Running Smoke Tests")
                    bat script: MavenScript.smokeTestCommand()
                    // sh script: MavenScript.smokeTestCommand()  // Linux equivalent
                    env.SMOKE_TEST_RESULT = 'SUCCESS'
                    stageResults['Smoke Tests'] = 'SUCCESS'
                    logger.info("Smoke Tests completed successfully")
                }
            } else {
                logger.info("Smoke Tests are disabled - skipping")
                env.SMOKE_TEST_RESULT = 'SKIPPED'
                stageResults['Smoke Tests'] = 'SKIPPED'
            }
            
            if (core_utils.shouldExecuteStage('sanitytest', config)) {
                parallelTests['Sanity Tests'] = {
                    logger.info("Running Sanity Tests")
                    bat script: MavenScript.sanityTestCommand()
                    // sh script: MavenScript.sanityTestCommand()  // Linux equivalent
                    env.SANITY_TEST_RESULT = 'SUCCESS'
                    stageResults['Sanity Tests'] = 'SUCCESS'
                    logger.info("Sanity Tests completed successfully")
                }
            } else {
                logger.info("Sanity Tests are disabled - skipping")
                env.SANITY_TEST_RESULT = 'SKIPPED'
                stageResults['Sanity Tests'] = 'SKIPPED'
            }
            
            if (core_utils.shouldExecuteStage('regressiontest', config)) {
                parallelTests['Regression Tests'] = {
                    logger.info("Running Regression Tests")
                    bat script: MavenScript.regressionTestCommand()
                    // sh script: MavenScript.regressionTestCommand()  // Linux equivalent
                    env.REGRESSION_TEST_RESULT = 'SUCCESS'
                    stageResults['Regression Tests'] = 'SUCCESS'
                    logger.info("Regression Tests completed successfully")
                }
            } else {
                logger.info("Regression Tests are disabled - skipping")
                env.REGRESSION_TEST_RESULT = 'SKIPPED'
                stageResults['Regression Tests'] = 'SKIPPED'
            }
            
            // Execute parallel tests if any are enabled
            if (parallelTests.size() > 0) {
                parallel parallelTests
            } else {
                logger.info("No tests are enabled - skipping test execution")
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