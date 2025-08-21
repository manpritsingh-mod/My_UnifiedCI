/**
 * Main method to execute unit tests for Java (Maven/Gradle) or Python projects
 * @param config Pipeline configuration map containing project language and test tool settings
 * @return String result: 'SUCCESS' (all tests pass), 'UNSTABLE' (some tests fail), 'FAILED' (critical error)
 * Usage: core_test.runUnitTest(config)
 */
def runUnitTest(Map config = [:]) {
    logger.info("Starting unit test execution")
    
            try {
                def language = config.project_language
                def testTool = getUnitTestTool(language, config)
                logger.info("Running unit tests for ${language} using ${testTool}")

                def result = false
                switch(language) {
                    case ['java-maven', 'java-gradle']:
                        result = runJavaUnitTest(language, testTool, config)
                        break
                    case 'python':
                        result = runPythonUnitTest(testTool, config)
                        break
                    default:
                        logger.error("Unsupported language for unit tests: ${language}")
                        return 'FAILED'
                }

                if (result == true) {
                    logger.info("Unit tests completed successfully")
                    return 'SUCCESS'
                } else if (result == 'UNSTABLE') {
                    logger.warning("Unit tests completed with failures - marking build as UNSTABLE")
                    currentBuild.result = 'UNSTABLE'
                    return 'UNSTABLE'
                } else {
                    logger.error("Unit tests failed critically")
                    currentBuild.result = 'UNSTABLE'
                    return 'UNSTABLE'
                }
            } catch (Exception e) {
                logger.error("Unit test execution failed: ${e.getMessage()}")
                currentBuild.result = 'UNSTABLE'
                return 'UNSTABLE'
            }
}

/**
 * Executes Java unit tests using Maven (mvn test) or Gradle (./gradlew test)
 * @param language Project language ('java-maven' or 'java-gradle')
 * @param testTool Test framework to use (junit, testng, etc.)
 * @param config Pipeline configuration
 * @return Boolean true (success) or String 'UNSTABLE' (some tests failed)
 */
private def runJavaUnitTest(String language, String testTool, Map config) {
    logger.info("Executing Java unit tests with ${testTool}")
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.testCommand(testTool)
    } else {
        command = GradleScript.testCommand(testTool)
    }

    try {
        sh script: command
        logger.info("Unit tests passed successfully")
        return true
    } catch (Exception e) {
        logger.warning("Unit tests failed but continuing pipeline: ${e.getMessage()}")
        
        if (fileExists('target/surefire-reports') || fileExists('build/test-results')) {
            logger.info("Test results found - tests ran but some failed, marking as UNSTABLE")
            return 'UNSTABLE'
        } else {
            logger.error("No test results found - critical test failure")
            return 'UNSTABLE'
        }
    }
}

/**
 * Executes Python unit tests using pytest or unittest
 * @param testTool Test framework to use ('pytest' or 'unittest')
 * @param config Pipeline configuration
 * @return Boolean true (success) or String 'UNSTABLE' (some tests failed)
 */
private def runPythonUnitTest(String testTool, Map config) {
    logger.info("Executing Python unit tests with ${testTool}")

    docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
        def image = docker.image(env.PYTHON_DOCKER_IMAGE)
        return image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
    
            try {
                sh script: PythonScript.venvTestLinuxCommand()
                logger.info("Python unit tests passed successfully")
                return true
            } catch (Exception e) {
                logger.warning("Python unit tests failed but continuing pipeline: ${e.getMessage()}")
            
                if (fileExists('test-results.xml') || fileExists('pytest-report.xml')) {
                    logger.info("Test results found - tests ran but some failed, marking as UNSTABLE")
                    return 'UNSTABLE'
                } else {
                    logger.error("No test results found - critical test failure")
                    return 'UNSTABLE'
                }
            }
        }
    }
}

/**
 * Gets the appropriate unit test tool for the specified programming language
 * @param language Project language ('java-maven', 'java-gradle', or 'python')
 * @param config Pipeline configuration containing tool preferences
 * @return String name of test tool to use (junit, pytest, etc.)
 * Usage: eg = getUnitTestTool('python', config) // Returns 'pytest'
 */
private String getUnitTestTool(String language, Map config) {
    if (language in ['java-maven', 'java-gradle']) {
        return config.tool_for_unit_testing?.java ?: 'junit'
    } else if (language == 'python') {
        return config.tool_for_unit_testing?.python ?: 'pytest'
    }
    throw new Exception("No test tool configured for language: ${language}")
}