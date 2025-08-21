/**
 * Java Maven Pipeline Template - Executes complete Maven-based Java CI/CD pipeline
 * Handles Maven project build, test, lint, and reporting with comprehensive error handling
 * @param config Pipeline configuration map (optional, uses defaults if not provided)
 * Usage: javaMaven_template() or javaMaven_template([project_language: 'java-maven', runLintTests: false]etc)
 */
def call(Map config = [:]) {
    logger.info("Starting Java Maven Template Pipeline")
    
    if (!config) {
        logger.info("No config provided, using default configuration")
        config = core_utils.getDefaultConfig()  // Use default configuration if not passed
    }

    if (!config.nexus?.registry || !config.nexus?.credentials_id || !config.nexus?.url) {
        logger.error("Missing Nexus configuration. Please give nexus.registry, nexus.url and nexus.credentials_id")
    }

    // Initialize stage results tracking for email reporting
    def stageResults = [:]

    stage('Checkout') {
        script {
            logger.info("CHECKOUT STAGE")
            core_github.checkout()
            stageResults['Checkout'] = 'SUCCESS'
        }
    }

    stage("Pull Java Maven Image") {
        script {
            def toolName = config.project_language ?: 'java-maven'
            def toolVersion = config["${toolName}_version"] ?: DockerImageManager.getDefaultVersion(toolName)

            echo "Tool: ${toolName}, Version: ${toolVersion}"

            def imagePath = DockerImageManager.getImagePath(
                toolName,
                toolVersion,
                config.nexus.registry,
                config.nexus.project
            )

            echo "Pulling Docker image from Nexus: ${imagePath}"

            docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
                def image = docker.image(imagePath)

                echo "Pulling image"
                image.pull()

                image.inside {
                    sh 'java -version || true'
                    sh 'mvn -v || true'
                }

                echo "Java Maven image ready"
                env.JAVA_MAVEN_DOCKER_IMAGE = imagePath
            }
        }
    }

    stage('Setup') {
        script {
            try{
            logger.info("SETUP STAGE")
            core_utils.setupProjectEnvironment(config.project_language, config)
            sh script: MavenScript.javaVersionCommand()
            sh script: MavenScript.mavenVersionCommand()
            stageResults['Setup'] = 'SUCCESS'
            } catch (Exception e) {
                logger.error("Setup failed: ${e.message}")
                stageResults['Setup'] = 'FAILED'
                logger.error("Setup stage failed")
            }
        }
    }

    
    stage('Install Dependencies') {
        script {
            logger.info("INSTALL DEPENDENCIES STAGE")

            docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
                    def image = docker.image(env.JAVA_MAVEN_DOCKER_IMAGE)
                    
                image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
                    def result = core_build.installDependencies('java', 'maven', config)

                    if (result) {
                            stageResults['Install Dependencies'] = 'SUCCESS'
                    } else {
                        stageResults['Install Dependencies'] = 'FAILED'
                        logger.error("Dependency installation failed inside core_build")
                    }
                }
            }
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
            def result = core_build.buildLanguages(config.project_language, config)

            if(result) {
            stageResults['Build'] = 'SUCCESS'
            } else {
                stageResults['Build'] = 'FAILED'
                logger.error("Build failed")
            }
        }
    }

    stage('Test Execution') {
        script {
            def parallelTests = [:]
            
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

            if (core_utils.shouldExecuteStage('functionaltest', config) || 
                core_utils.shouldExecuteStage('smoketest', config) || 
                core_utils.shouldExecuteStage('sanitytest', config) || 
                core_utils.shouldExecuteStage('regressiontest', config)) {
                
                parallelTests['Functional Tests'] = {
                    logger.info("Starting Functional Tests with Individual Stages")
                    
                    stage('Smoke Tests') {
                        if (core_utils.shouldExecuteStage('smoketest', config)) {
                            logger.info("Running Smoke Tests")
                            sh script: MavenScript.smokeTestCommand()
                            env.SMOKE_TEST_RESULT = 'SUCCESS'
                            stageResults['Smoke Tests'] = 'SUCCESS'
                            logger.info("Smoke Tests completed successfully")
                        } else {
                            logger.info("Smoke Tests are disabled - skipping")
                            env.SMOKE_TEST_RESULT = 'SKIPPED'
                            stageResults['Smoke Tests'] = 'SKIPPED'
                        }
                    }
                    
                    stage('Sanity Tests') {
                        if (core_utils.shouldExecuteStage('sanitytest', config)) {
                            logger.info("Running Sanity Tests")
                            sh script: MavenScript.sanityTestCommand()
                            env.SANITY_TEST_RESULT = 'SUCCESS'
                            stageResults['Sanity Tests'] = 'SUCCESS'
                            logger.info("Sanity Tests completed successfully")
                        } else {
                            logger.info("Sanity Tests are disabled - skipping")
                            env.SANITY_TEST_RESULT = 'SKIPPED'
                            stageResults['Sanity Tests'] = 'SKIPPED'
                        }
                    }
                    
                    stage('Regression Tests') {
                        if (core_utils.shouldExecuteStage('regressiontest', config)) {
                            logger.info("Running Regression Tests")
                            sh script: MavenScript.regressionTestCommand()
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
            sendReport.generateAndSendReports(config, stageResults)
            stageResults['Generate Reports'] = 'SUCCESS'
        }
    }
}