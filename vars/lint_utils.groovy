def runLint(Map config = [:]) {
    logger.info("Starting lint")
    
    try {
        def language = config.project_language
        def lintTool = getLintTool(language, config)
        logger.info("Running lint for ${language} using ${lintTool}")

        def result = false
        switch(language) {
            case ['java-maven', 'java-gradle']:
                result = runJavaLint(language, lintTool, config)
                break
            case 'python':
                result = runPythonLint(lintTool, config)
                break
            default:
                logger.error("Unsupported language for lint: ${language}")
        }

        if (result) {
            logger.info("Lint completed successfully")
        } else {
            logger.error("Lint failed")
        }
        return result
    } catch (Exception e) {
        logger.error("Lint execution failed: ${e.getMessage()}")
        return false
    }
}

private Boolean runJavaLint(String language, String lintTool, Map config) {
    logger.info("Executing Java lint with ${lintTool}")
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.lintCommand(lintTool)
    } else {
        command = GradleScript.lintCommand(lintTool)
    }

    try {
        bat script: command
        return true
    } catch (Exception e) {
        logger.error("Java lint execution failed: ${e.getMessage()}")
        return false
    }
}

private Boolean runPythonLint(String lintTool, Map config) {
    logger.info("Executing Python lint with ${lintTool}")
    
    try {
        bat script: PythonScript.lintCommand(lintTool)
        return true
    } catch (Exception e) {
        logger.error("Python lint execution failed: ${e.getMessage()}")
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


