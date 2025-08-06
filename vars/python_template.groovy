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
     * Creates virtual environment and sets up isolated Python environment
     * Sets PYTHON_CMD and PIP_CMD environment variables for later stages
     */
    stage('Setup') {
        script {
            logger.info("SETUP STAGE")
            core_utils.setupProjectEnvironment(config.project_language, config)
            
            // Check Python and Pip versions
            bat script: PythonScript.pythonVersionCommand()
            // sh script: PythonScript.pythonVersionCommand()  // Linux equivalent
            bat script: PythonScript.pipVersionCommand()
            // sh script: PythonScript.pipVersionCommand()     // Linux equivalent
            
            // Create virtual environment
            logger.info("Creating virtual environment...")
            bat script: PythonScript.createVirtualEnvCommand()
            // sh script: PythonScript.createVirtualEnvCommand()  // Linux equivalent
            
            logger.info("Virtual environment created successfully")
            stageResults['Setup'] = 'SUCCESS'
        }
    }
    
    /**
     * STAGE 3: Install Dependencies - Installs Python packages from requirements.txt
     * Uses pip in virtual environment to install all project dependencies
     */
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE - Installing in virtual environment")
            
            // Install dependencies in virtual environment
            bat script: PythonScript.venvPipInstallCommand()
            // sh script: PythonScript.venvPipInstallLinuxCommand()  // Linux equivalent
            
            logger.info("Dependencies installed successfully in virtual environment")
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
        script {
            def parallelTests = [:]
            
            // Add Unit Test as first parallel branch
            if (core_utils.shouldExecuteStage('unittest', config)) {
                parallelTests['Unit Test'] = {
                    logger.info("Running Unit Tests in virtual environment")
                    
                    // Run unit tests using virtual environment
                    bat script: PythonScript.venvTestCommand()
                    // sh script: PythonScript.venvTestLinuxCommand()  // Linux equivalent
                    
                    env.UNIT_TEST_RESULT = 'SUCCESS'
                    stageResults['Unit Test'] = 'SUCCESS'
                    logger.info("Unit test stage completed with result: SUCCESS")
                }
            } else {
                logger.info("Unit testing is disabled - skipping")
                env.UNIT_TEST_RESULT = 'SKIPPED'
                stageResults['Unit Test'] = 'SKIPPED'
            }
            
            // Add Functional Tests as second parallel branch with individual stages
            if (core_utils.shouldExecuteStage('functionaltest', config) || 
                core_utils.shouldExecuteStage('smoketest', config) || 
                core_utils.shouldExecuteStage('sanitytest', config) || 
                core_utils.shouldExecuteStage('regressiontest', config)) {
                
                parallelTests['Functional Tests'] = {
                    logger.info("Starting Functional Tests with Individual Stages")
                    
                    // Individual Stage: Smoke Tests
                    stage('Smoke Tests') {
                        if (core_utils.shouldExecuteStage('smoketest', config)) {
                            logger.info("Running Smoke Tests in virtual environment")
                            bat script: PythonScript.venvSmokeTestCommand()
                            // sh script: PythonScript.venvSmokeTestLinuxCommand()  // Linux equivalent
                            env.SMOKE_TEST_RESULT = 'SUCCESS'
                            stageResults['Smoke Tests'] = 'SUCCESS'
                            logger.info("Smoke Tests completed successfully")
                        } else {
                            logger.info("Smoke Tests are disabled - skipping")
                            env.SMOKE_TEST_RESULT = 'SKIPPED'
                            stageResults['Smoke Tests'] = 'SKIPPED'
                        }
                    }
                    
                    // Individual Stage: Sanity Tests (runs after Smoke)
                    stage('Sanity Tests') {
                        if (core_utils.shouldExecuteStage('sanitytest', config)) {
                            logger.info("Running Sanity Tests in virtual environment")
                            bat script: PythonScript.venvSanityTestCommand()
                            // sh script: PythonScript.venvSanityTestLinuxCommand()  // Linux equivalent
                            env.SANITY_TEST_RESULT = 'SUCCESS'
                            stageResults['Sanity Tests'] = 'SUCCESS'
                            logger.info("Sanity Tests completed successfully")
                        } else {
                            logger.info("Sanity Tests are disabled - skipping")
                            env.SANITY_TEST_RESULT = 'SKIPPED'
                            stageResults['Sanity Tests'] = 'SKIPPED'
                        }
                    }
                    
                    // Individual Stage: Regression Tests (runs after Sanity)
                    stage('Regression Tests') {
                        if (core_utils.shouldExecuteStage('regressiontest', config)) {
                            logger.info("Running Regression Tests in virtual environment")
                            bat script: PythonScript.venvRegressionTestCommand()
                            // sh script: PythonScript.venvRegressionTestLinuxCommand()  // Linux equivalent
                            env.REGRESSION_TEST_RESULT = 'SUCCESS'
                            stageResults['Regression Tests'] = 'SUCCESS'
                            logger.info("Regression Tests completed successfully")
                        } else {
                            logger.info("Regression Tests are disabled - skipping")
                            env.REGRESSION_TEST_RESULT = 'SKIPPED'
                            stageResults['Regression Tests'] = 'SKIPPED'
                        }
                    }
                    
                    env.FUNCTIONAL_TEST_RESULT = 'SUCCESS'
                    logger.info("All Functional Test Stages completed")
                }
            } else {
                logger.info("All functional tests are disabled - skipping")
                env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                stageResults['Functional Tests'] = 'SKIPPED'
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
    
    stage('Cleanup') {
        script {
            logger.info("CLEANUP STAGE - Cleaning up virtual environment")
            
            try {
                // Cleanup virtual environment
                bat script: PythonScript.cleanupVirtualEnvCommand()
                // sh script: PythonScript.cleanupVirtualEnvLinuxCommand()  // Linux equivalent
                
                logger.info("Virtual environment cleaned up successfully")
                stageResults['Cleanup'] = 'SUCCESS'
            } catch (Exception e) {
                logger.warning("Virtual environment cleanup failed, but continuing: ${e.getMessage()}")
                stageResults['Cleanup'] = 'WARNING'
            }
        }
    }
}