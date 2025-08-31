/**
 * Main method to run code quality linting for Java or Python projects
 * @param config Pipeline configuration map containing project language and lint tool settings
 * @return String result: 'SUCCESS' (no violations), 'UNSTABLE' (violations found), 'FAILED' (critical error)
 * Usage: def lintResult = lint_utils.runLint(config)
 */
def runLint(Map config = [:]) {
    logger.info("Starting lint execution")
    
    // NOTE: This method is called from INSIDE Docker containers in templates
    // So we don't need to create another Docker container here

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
            case ['react', 'nodejs']:
                result = runReactLint(lintTool, config)
                break
            default:
                logger.error("Unsupported language for lint: ${language}")
                return 'FAILED'
        }

        // Process lint results and set appropriate build status
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
        sh script: command
        logger.info("Lint passed with no violations")
        return true
    } catch (Exception e) {
        logger.warning("Lint found violations but continuing pipeline: ${e.getMessage()}")
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
    logger.info("Executing Python lint with ${lintTool} in virtual environment")
    
    try {
        sh script: PythonScript.venvLintLinuxCommand(lintTool)
        logger.info("Python lint passed with no violations")
        return true
    } catch (Exception e) {
        logger.warning("Python lint found violations but continuing pipeline: ${e.getMessage()}")
        
        if (fileExists('pylint-report.txt') || fileExists('flake8-report.txt')) {
            logger.info("Lint results found - violations detected, marking as UNSTABLE")
            return 'UNSTABLE'
        } else {
            logger.error("No lint results found - critical lint failure")
            return 'UNSTABLE'
        }
    }
}

private def runReactLint(String lintTool, Map config) {
    logger.info("Executing React lint with ${lintTool}")
    
    try {
        sh script: ReactScript.lintCommand(lintTool)
        logger.info("React lint passed with no violations")
        return true
    } catch (Exception e) {
        logger.warning("React lint found violations but continuing pipeline: ${e.getMessage()}")
        
        if (fileExists('eslint-report.xml') || fileExists('tslint-report.xml') || fileExists('lint-results.json')) {
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
    } else if (language == 'react') {
        return config.tool_for_lint_testing?.react ?: 'eslint'
    } else if (language == 'nodejs') {
        return config.tool_for_lint_testing?.nodejs ?: 'eslint'
    }
    throw new Exception("No lint tool configured for language: ${language}")
}