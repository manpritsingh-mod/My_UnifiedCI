/**
 * Python Pipeline Template - Executes complete Python CI/CD pipeline
 * Handles Python project detection, dependency installation, linting, testing, and reporting
 * @param config Pipeline configuration map (optional, uses defaults if not provided)
 * Usage: python_template() or python_template([project_language: 'python', lint: true])
 */
def call(Map config = [:]) {
    logger.info("Starting Python Template Pipeline")
    
    // Use default configuration if not passed
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()
    }
    
    // Initialize stage results tracking for email reporting
    def stageResults = [:]
    
    // Execute Python-specific pipeline stages
    /**
     * STAGE 1: Checkout - Downloads source code from Git repository
     * Uses core_github.checkout() to pull latest code from configured branch
     */
    stage('Checkout') {
        script {
            logger.info("CHECKOUT STAGE")
            core_github.checkout()
            stageResults['Checkout'] = 'SUCCESS'
        }
    }
    
    /**
     * STAGE 2: Setup - Detects Python and pip installations, sets up environment
     * Tries multiple Python commands (python, python3, py) to find working installation
     * Sets PYTHON_CMD and PIP_CMD environment variables for later stages
     */
    stage('Setup') {
        script {
            logger.info("SETUP STAGE")
            core_utils.setupProjectEnvironment(config.project_language, config)
            
            bat script: PythonScript.pythonVersionCommand()
            // sh script: PythonScript.pythonVersionCommand()  // Linux equivalent
            bat script: PythonScript.pipVersionCommand()
            // sh script: PythonScript.pipVersionCommand()     // Linux equivalent
            
            stageResults['Setup'] = 'SUCCESS'
        }
    }
    
    /**
     * STAGE 3: Install Dependencies - Installs Python packages from requirements.txt
     * Uses pip to install all project dependencies needed for build and test
     */
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE")
            core_build.installDependencies('python', 'pip', config)
            stageResults['Install Dependencies'] = 'SUCCESS'
        }
    }
    
    /**
     * STAGE 4: Lint - Runs code quality checks using pylint (optional stage)
     * Checks Python code for style violations, errors, and code quality issues
     * Can be disabled in config, returns SUCCESS/UNSTABLE/FAILURE based on violations
     */
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
                        
                        // Run Smoke Tests
                        logger.info("Running Smoke Tests")
                        bat script: PythonScript.smokeTestCommand()
                        // sh script: PythonScript.smokeTestCommand()  // Linux equivalent
                        
                        // Run Sanity Tests
                        logger.info("Running Sanity Tests")
                        bat script: PythonScript.sanityTestCommand()
                        // sh script: PythonScript.sanityTestCommand()  // Linux equivalent
                        
                        // Run Regression Tests
                        logger.info("Running Regression Tests")
                        bat script: PythonScript.regressionTestCommand()
                        // sh script: PythonScript.regressionTestCommand()  // Linux equivalent
                        
                        env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                        stageResults['Functional Tests'] = 'SUCCESS'
                        logger.info("Functional Tests completed successfully")
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