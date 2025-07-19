def runLint(Map config = [:]) {
    logger.info("Starting lint")
    
    try {
        def language = config.project_language
        def lintTool = getLintTool(language, config)
        logger.info("Running lint for ${language} using ${lintTool}")

        def lintResult = [:]
        def success = false
        
        switch(language) {
            case ['java-maven', 'java-gradle']:
                lintResult = runJavaLint(language, lintTool, config)
                success = lintResult.success
                break
            case 'python':
                lintResult = runPythonLint(lintTool, config)
                success = lintResult.success
                break
            default:
                logger.error("Unsupported language for lint: ${language}")
                return [success: false, status: 'FAILED', message: "Unsupported language: ${language}"]
        }

        // Handle result based on quality gates
        if (success) {
            logger.info("Lint completed successfully")
            return [success: true, status: 'SUCCESS', message: "Lint passed"]
        } else {
            // Check quality gate setting
            def failOnError = config.quality_gates?.lint?.fail_on_error ?: true
            
            if (failOnError) {
                logger.error("Lint failed - marking as FAILED")
                return [success: false, status: 'FAILED', message: "Lint failed", details: lintResult.details]
            } else {
                logger.warning("Lint failed - marking as UNSTABLE (non-critical)")
                return [success: false, status: 'UNSTABLE', message: "Lint failed but marked as non-critical", details: lintResult.details]
            }
        }
        
    } catch (Exception e) {
        logger.error("Lint execution failed: ${e.getMessage()}")
        return [success: false, status: 'FAILED', message: "Lint execution error: ${e.getMessage()}"]
    }
}

private Map runJavaLint(String language, String lintTool, Map config) {
    logger.info("Executing Java lint with ${lintTool}")
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.lintCommand(lintTool)
    } else {
        command = GradleScript.lintCommand(lintTool)
    }

    try {
        // Execute lint command
        def exitCode = bat(script: command, returnStatus: true)
        
        // Generate reports if configured
        if (config.quality_gates?.lint?.generate_report) {
            generateLintReport(config, lintTool)
        }
        
        if (exitCode == 0) {
            return [success: true, details: "Lint passed with no violations"]
        } else {
            // Collect lint violations for reporting
            def violations = collectLintViolations(config, lintTool)
            return [success: false, details: "Lint found ${violations.count} violations", violations: violations]
        }

    } catch (Exception e) {
        logger.error("Java lint execution failed: ${e.getMessage()}")
        return [success: false, details: "Lint execution error: ${e.getMessage()}"]
    }
}

private Map runPythonLint(String lintTool, Map config) {
    logger.info("Executing Python lint with ${lintTool}")
    
    try {
        def exitCode = bat(script: PythonScript.lintCommand(lintTool), returnStatus: true)
        
        // Generate reports if configured
        if (config.quality_gates?.lint?.generate_report) {
            generateLintReport(config, lintTool)
        }
        
        if (exitCode == 0) {
            return [success: true, details: "Lint passed with no violations"]
        } else {
            def violations = collectLintViolations(config, lintTool)
            return [success: false, details: "Lint found ${violations.count} violations", violations: violations]
        }
        
    } catch (Exception e) {
        logger.error("Python lint execution failed: ${e.getMessage()}")
        return [success: false, details: "Lint execution error: ${e.getMessage()}"]
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

// Generate lint reports for Allure
private def generateLintReport(Map config, String lintTool) {
    logger.info("Generating lint report for ${lintTool}")
    
    try {
        // Create allure results directory
        bat 'if not exist "allure-results" mkdir allure-results'
        
        // Convert checkstyle XML to Allure format if needed
        if (lintTool == 'checkstyle' && fileExists('target/checkstyle-result.xml')) {
            // Copy checkstyle results for Allure
            bat 'copy target\\checkstyle-result.xml allure-results\\checkstyle-result.xml'
            logger.info("Checkstyle results copied to allure-results")
        }
        
        // Archive lint reports
        if (fileExists('target/checkstyle-result.xml')) {
            archiveArtifacts artifacts: 'target/checkstyle-result.xml', allowEmptyArchive: true
        }
        
    } catch (Exception e) {
        logger.warning("Failed to generate lint report: ${e.getMessage()}")
    }
}

// Collect lint violations for detailed reporting
private def collectLintViolations(Map config, String lintTool) {
    logger.info("Collecting lint violations from ${lintTool}")
    
    def violations = [count: 0, details: []]
    
    try {
        if (lintTool == 'checkstyle' && fileExists('target/checkstyle-result.xml')) {
            // Parse checkstyle XML to count violations
            def checkstyleXml = readFile('target/checkstyle-result.xml')
            
            // Simple regex to count violations (you can enhance this)
            def errorMatches = (checkstyleXml =~ /<error/)
            violations.count = errorMatches.size()
            
            // Extract first few violations for summary
            def lines = checkstyleXml.split('\n')
            def violationLines = lines.findAll { it.contains('<error') }.take(5)
            violations.details = violationLines.collect { line ->
                // Extract file, line, and message from XML
                def fileMatch = (line =~ /file="([^"]*)"/)
                def lineMatch = (line =~ /line="([^"]*)"/)
                def messageMatch = (line =~ /message="([^"]*)"/)
                
                return [
                    file: fileMatch ? fileMatch[0][1] : 'unknown',
                    line: lineMatch ? lineMatch[0][1] : 'unknown',
                    message: messageMatch ? messageMatch[0][1] : 'unknown'
                ]
            }
            
            logger.info("Found ${violations.count} checkstyle violations")
        }
        
    } catch (Exception e) {
        logger.warning("Failed to collect lint violations: ${e.getMessage()}")
    }
    
    return violations
}
