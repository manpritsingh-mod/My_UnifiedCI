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
                return 'FAILED'
        }

        if (result == true) {
            logger.info("Lint completed successfully")
            return 'SUCCESS'
        } else if (result == 'UNSTABLE') {
            logger.warning("Lint found violations - marking build as UNSTABLE")
            currentBuild.result = 'UNSTABLE'
            return 'UNSTABLE'
        } else {
            logger.error("Lint failed critically")
            currentBuild.result = 'UNSTABLE'
            return 'UNSTABLE'
        }
    } catch (Exception e) {
        logger.error("Lint execution failed: ${e.getMessage()}")
        currentBuild.result = 'UNSTABLE'
        return 'UNSTABLE'
    }
}

private def runJavaLint(String language, String lintTool, Map config) {
    logger.info("Executing Java lint with ${lintTool}")
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.lintCommand(lintTool)
    } else {
        command = GradleScript.lintCommand(lintTool)
    }

    try {
        bat script: command
        logger.info("Lint passed with no violations")
        return true
    } catch (Exception e) {
        logger.warning("Lint found violations but continuing pipeline: ${e.getMessage()}")
        
        // Check if lint results exist (lint ran but found violations)
        if (fileExists('target/checkstyle-result.xml') || fileExists('build/reports/checkstyle')) {
            logger.info("Lint results found - violations detected, marking as UNSTABLE")
            return 'UNSTABLE'
        } else {
            logger.error("No lint results found - critical lint failure")
            return 'UNSTABLE'
        }
    }
}

private def runPythonLint(String lintTool, Map config) {
    logger.info("Executing Python lint with ${lintTool}")
    
    try {
        bat script: PythonScript.lintCommand(lintTool)
        logger.info("Python lint passed with no violations")
        return true
    } catch (Exception e) {
        logger.warning("Python lint found violations but continuing pipeline: ${e.getMessage()}")
        
        // Check if lint results exist
        if (fileExists('pylint-report.txt') || fileExists('flake8-report.txt')) {
            logger.info("Lint results found - violations detected, marking as UNSTABLE")
            return 'UNSTABLE'
        } else {
            logger.error("No lint results found - critical lint failure")
            return 'UNSTABLE'
        }
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


