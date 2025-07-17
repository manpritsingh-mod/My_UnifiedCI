def runLint(Map config = [:]) {
    // Logger.info("Starting lint")
    echo "Starting lint"
    
    try {
        // if (!core_utils.shouldExecuteStage('lint', config)) { // checking for the linting should be executed or to be skipped
        //     Logger.info("Lint tests are disabled - skipping") // will be removed only written for the asking purpose
        //     return true
        // }

        def language = config.project_language
        def lintTool = getLintTool(language, config)
        // Logger.info("Running lint for ${language} using ${lintTool}")
        echo "Running lint for ${language} using ${lintTool}"

        def result = false
        switch(language) {
            case ['java-maven', 'java-gradle']:
                result = runJavaLint(language, lintTool, config) // calling the language specific lintTool
                break
            case 'python':
                result = runPythonLint(lintTool, config)
                break
            default:
                // Logger.error("Unsupported language for lint: ${language}")
                echo "Unsupported language for lint: ${language}"
        }

        if (result) {
            // Logger.info("Lint completed successfully")
            echo "Lint completed successfully"
        } else {
            // Logger.error("Lint failed")
            echo "Lint failed"
        }
        return result
    } catch (Exception e) {
        // Logger.error("Lint execution failed: ${e.getMessage()}")
        echo "Lint execution failed: ${e.getMessage()}"
        return false
    }
}

private Boolean runJavaLint(String language, String lintTool, Map config) {
    // Logger.info("Executing Java lint with ${lintTool}")
    echo "Executing Java lint with ${lintTool}"
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.lintCommand(lintTool)
    } else {
        command = GradleScript.lintCommand(lintTool)
    }

    try {
        // sh script: command
        bat script: command
        return true

    } catch (Exception e) {
        // Logger.error("Java lint execution failed: ${e.getMessage()}")
        echo "Java lint execution failed: ${e.getMessage()}"
        return false
    }
}

private Boolean runPythonLint(String lintTool, Map config) {
    // Logger.info("Executing Python lint with ${lintTool}")
    echo "Executing Python lint with ${lintTool}"
    
    try {
        // sh script: PythonScript.lintCommand(lintTool)
        bat script: PythonScript.lintCommand(lintTool)
        return true
    } catch (Exception e) {
        // Logger.error("Python lint execution failed: ${e.getMessage()}")
        echo "Python lint execution failed: ${e.getMessage()}"
        return false
    }
}

private String getLintTool(String language, Map config) {
    if (language in ['java-maven', 'java-gradle']) {
        return config.tool_for_lint_testing?.java ?: 'checkstyle'
    } else if (language == 'python') {
        return config.tool_for_lint_testing?.python ?: 'pylint'
    }
    throw new Exception("No lint tool configured for language: ${language}")
}
