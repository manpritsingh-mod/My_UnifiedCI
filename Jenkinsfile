@Library('My_UnifiedCI') _

pipeline {
    agent any
    
    environment {
        PROJECT_LANGUAGE = ''
        BUILD_TOOL = ''
        RUN_UNIT_TESTS = ''
        RUN_LINT_TESTS = ''
        RUN_FUNCTIONAL_TESTS = ''
    }
    
    stages {
        stage('Setup and Execution') {
            steps {
                script {
                    logger.info("---- STAGE: SETUP AND EXECUTION ----")
                    
                    // Read project configuration from YAML
                    def config = core_utils.readProjectConfig()
                    logger.info("Config map content: ${config}")
                    
                    if (config && !config.isEmpty()) {
                        // Setup global environment
                        core_utils.setupEnvironment()
                        logger.info("Global environment setup completed")
                        
                        // Call appropriate template based on the project language
                        logger.info("Calling template for: ${config.project_language}")
                        switch (config.project_language) {
                            case 'java-maven':
                                logger.info("Executing Java Maven template")
                                javaMaven_template(config)
                                break
                            case 'java-gradle':
                                logger.info("Executing Java Gradle template")
                                javaGradle_template(config)
                                break
                            case 'python':
                                logger.info("Executing Python template")
                                python_template(config)
                                break
                            default:
                                error("Unsupported project language: ${config.project_language}")
                        }
                        
                        logger.info("Project template execution completed")
                        
                    } else {
                        error("PROJECT_CONFIG is empty or missing")
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                logger.info("=== SENDING NOTIFICATIONS ===")
                
                // Simple notification
                def buildStatus = currentBuild.result ?: 'SUCCESS'
                def config = [
                    notifications: [
                        email: [recipients: ["smanprit022@gmail.com"]]
                    ]
                ]
                
                notify.notifyBuildStatus(buildStatus, config)
                logger.info("Notification sent successfully")
            }
        }
        
        success {
            script {
                logger.info("BUILD SUCCESSFUL!")
            }
        }
        
        failure {
            script {
                logger.error("BUILD FAILED!")
            }
        }
        
        unstable {
            script {
                logger.warning("BUILD UNSTABLE!")
            }
        }
    }
}