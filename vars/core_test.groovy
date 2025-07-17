def runUnitTest(Map config = [:]) {
    // Logger.info("Starting unit test execution")
    echo "Starting unit test execution"
    
    try {
        // if (!core_utils.shouldExecuteStage('unittest', config)) { // checking for the UnitTest should be executed or to be skipped
        //     Logger.info("Unit tests are disabled - skipping")
        //     return true
        // }

        def language = config.project_language // 
        def testTool = getUnitTestTool(language, config)
        // Logger.info("Running unit tests for ${language} using ${testTool}")
        echo "Running unit tests for ${language} using ${testTool}"

        def result = false
        switch(language) {
            case ['java-maven', 'java-gradle']:
                result = runJavaUnitTest(language, testTool, config)
                break
            case 'python':
                result = runPythonUnitTest(testTool, config)
                break
            default:
                // Logger.error("Unsupported language for unit tests: ${language}")
                echo "Unsupported language for unit tests: ${language}"
        }

        if (result) {
            // Logger.info("Unit tests completed successfully")
            echo "Unit tests completed successfully"
        } else {
            // Logger.error("Unit tests failed")
            echo "Unit tests failed"
        }
        return result
    } catch (Exception e) {
        // Logger.error("Unit test execution failed: ${e.getMessage()}")
        echo "Unit test execution failed: ${e.getMessage()}"
        return false
    }
}

private Boolean runJavaUnitTest(String language, String testTool, Map config) {
    // Logger.info("Executing Java unit tests with ${testTool}")
    echo "Executing Java unit tests with ${testTool}"
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.testCommand(testTool)
    } else {
        command = GradleScript.testCommand(testTool)
    }

    try {
        // sh script: command
        bat script: command
        return true
    } catch (Exception e) {
        // Logger.error("Java unit test execution failed: ${e.getMessage()}")
        echo "Java unit test execution failed: ${e.getMessage()}"
        return false
    }
}

private Boolean runPythonUnitTest(String testTool, Map config) {
    // Logger.info("Executing Python unit tests with ${testTool}")
    echo "Executing Python unit tests with ${testTool}"
    
    try {
        // sh script: PythonScript.testCommand(testTool)
        bat script: PythonScript.testCommand(testTool)
        return true
    } catch (Exception e) {
        // Logger.error("Python unit test execution failed: ${e.getMessage()}")
        echo "Python unit test execution failed: ${e.getMessage()}"
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
