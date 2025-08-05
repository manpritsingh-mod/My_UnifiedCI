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
        parallel {
            stage('Unit Test') {
                when {
                    expression { core_utils.shouldExecuteStage('unittest', config) }
                }
                steps {
                    script {
                        logger.info("Running Unit Tests")
                        def testResult = core_test.runUnitTest(config)
                        env.UNIT_TEST_RESULT = testResult
                        stageResults['Unit Test'] = testResult
                        logger.info("Unit test stage completed with result: ${testResult}")
                    }
                }
            }
            stage('Functional Tests') {
                when {
                    expression { core_utils.shouldExecuteStage('functionaltest', config) }
                }
                steps {
                    script {
                        logger.info("Running Functional Tests")
                        def functionalTestResults = []
                        
                        // Run Smoke Tests (if enabled)
                        if (core_utils.shouldExecuteStage('smoketest', config)) {
                            logger.info("Running Smoke Tests")
                            bat script: MavenScript.smokeTestCommand()
                            // sh script: MavenScript.smokeTestCommand()  // Linux equivalent
                            functionalTestResults.add("Smoke Tests: SUCCESS")
                        } else {
                            logger.info("Smoke Tests are disabled - skipping")
                            functionalTestResults.add("Smoke Tests: SKIPPED")
                        }
                        
                        // Run Sanity Tests (if enabled)
                        if (core_utils.shouldExecuteStage('sanitytest', config)) {
                            logger.info("Running Sanity Tests")
                            bat script: MavenScript.sanityTestCommand()
                            // sh script: MavenScript.sanityTestCommand()  // Linux equivalent
                            functionalTestResults.add("Sanity Tests: SUCCESS")
                        } else {
                            logger.info("Sanity Tests are disabled - skipping")
                            functionalTestResults.add("Sanity Tests: SKIPPED")
                        }
                        
                        // Run Regression Tests (if enabled)
                        if (core_utils.shouldExecuteStage('regressiontest', config)) {
                            logger.info("Running Regression Tests")
                            bat script: MavenScript.regressionTestCommand()
                            // sh script: MavenScript.regressionTestCommand()  // Linux equivalent
                            functionalTestResults.add("Regression Tests: SUCCESS")
                        } else {
                            logger.info("Regression Tests are disabled - skipping")
                            functionalTestResults.add("Regression Tests: SKIPPED")
                        }
                        
                        env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                        stageResults['Functional Tests'] = functionalTestResults.join(", ")
                        logger.info("Functional Tests completed: ${functionalTestResults.join(', ')}")
                    }
                }
            }
        }
        post {
            always {
                script {
                    // Set default values if stages were skipped
                    if (!env.UNIT_TEST_RESULT) {
                        env.UNIT_TEST_RESULT = 'SKIPPED'
                        stageResults['Unit Test'] = 'SKIPPED'
                        logger.info("Unit testing was disabled - skipped")
                    }
                    if (!env.FUNCTIONAL_TEST_RESULT) {
                        env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                        stageResults['Functional Tests'] = 'SKIPPED'
                        logger.info("Functional testing was disabled - skipped")
                    }
                }
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