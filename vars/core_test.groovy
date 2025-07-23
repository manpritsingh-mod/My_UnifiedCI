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
        }

        if (result) {
            logger.info("Unit tests completed successfully")
        } else {
            logger.error("Unit tests failed")
        }
        return result
    } catch (Exception e) {
        logger.error("Unit test execution failed: ${e.getMessage()}")
        return false
    }
}

private Boolean runJavaUnitTest(String language, String testTool, Map config) {
    logger.info("Executing Java unit tests with ${testTool}")
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.testCommand(testTool)
    } else {
        command = GradleScript.testCommand(testTool)
    }

    try {
        bat script: command
        return true
    } catch (Exception e) {
        logger.error("Java unit test execution failed: ${e.getMessage()}")
        return false
    }
}

private Boolean runPythonUnitTest(String testTool, Map config) {
    logger.info("Executing Python unit tests with ${testTool}")
    
    try {
        bat script: PythonScript.testCommand(testTool)
        return true
    } catch (Exception e) {
        logger.error("Python unit test execution failed: ${e.getMessage()}")
        return false
    }
}

private String getUnitTestTool(String language, Map config) {
    if (language in ['java-maven', 'java-gradle']) {
        return config.tool_for_unit_testing?.java ?: 'junit'
    } else if (language == 'python') {
        return config.tool_for_unit_testing?.python ?: 'pytest'
    }
    throw new Exception("No test tool configured for language: ${language}")
}


