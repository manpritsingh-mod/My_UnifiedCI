def runUnitTest(Map config = [:]) {
    logger.info("Starting unit test execution")
    
    try {
        def language = config.project_language
        def testTool = getUnitTestTool(language, config)
        logger.info("Running unit tests for ${language} using ${testTool}")

        def testResult = [:]
        def success = false
        
        switch(language) {
            case ['java-maven', 'java-gradle']:
                testResult = runJavaUnitTest(language, testTool, config)
                success = testResult.success
                break
            case 'python':
                testResult = runPythonUnitTest(testTool, config)
                success = testResult.success
                break
            default:
                logger.error("Unsupported language for unit tests: ${language}")
                return [success: false, status: 'FAILED', message: "Unsupported language: ${language}"]
        }

        // Handle result based on quality gates
        if (success) {
            logger.info("Unit tests completed successfully")
            return [success: true, status: 'SUCCESS', message: "All unit tests passed", details: testResult.details]
        } else {
            // Check quality gate setting
            def failOnError = config.quality_gates?.unit_test?.fail_on_error ?: true
            
            if (failOnError) {
                logger.error("Unit tests failed - marking as FAILED")
                return [success: false, status: 'FAILED', message: "Unit tests failed", details: testResult.details]
            } else {
                logger.warning("Unit tests failed - marking as UNSTABLE (non-critical)")
                return [success: false, status: 'UNSTABLE', message: "Unit tests failed but marked as non-critical", details: testResult.details]
            }
        }
        
    } catch (Exception e) {
        logger.error("Unit test execution failed: ${e.getMessage()}")
        return [success: false, status: 'FAILED', message: "Unit test execution error: ${e.getMessage()}"]
    }
}

private Map runJavaUnitTest(String language, String testTool, Map config) {
    logger.info("Executing Java unit tests with ${testTool}")
    
    def command
    if (language == 'java-maven') {
        command = MavenScript.testCommand(testTool)
    } else {
        command = GradleScript.testCommand(testTool)
    }

    try {
        // Execute test command
        def exitCode = bat(script: command, returnStatus: true)
        
        // Generate test reports if configured
        if (config.quality_gates?.unit_test?.generate_report) {
            generateTestReport(config, testTool, language)
        }
        
        if (exitCode == 0) {
            def testResults = collectTestResults(config, language)
            return [success: true, details: "All tests passed. ${testResults.summary}"]
        } else {
            // Collect test failure details
            def testResults = collectTestResults(config, language)
            return [success: false, details: "Tests failed. ${testResults.summary}", failures: testResults.failures]
        }

    } catch (Exception e) {
        logger.error("Java unit test execution failed: ${e.getMessage()}")
        return [success: false, details: "Test execution error: ${e.getMessage()}"]
    }
}

private Map runPythonUnitTest(String testTool, Map config) {
    logger.info("Executing Python unit tests with ${testTool}")
    
    try {
        def exitCode = bat(script: PythonScript.testCommand(testTool), returnStatus: true)
        
        // Generate test reports if configured
        if (config.quality_gates?.unit_test?.generate_report) {
            generateTestReport(config, testTool, 'python')
        }
        
        if (exitCode == 0) {
            def testResults = collectTestResults(config, 'python')
            return [success: true, details: "All tests passed. ${testResults.summary}"]
        } else {
            def testResults = collectTestResults(config, 'python')
            return [success: false, details: "Tests failed. ${testResults.summary}", failures: testResults.failures]
        }
        
    } catch (Exception e) {
        logger.error("Python unit test execution failed: ${e.getMessage()}")
        return [success: false, details: "Test execution error: ${e.getMessage()}"]
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

// Generate test reports for Allure and archiving
private def generateTestReport(Map config, String testTool, String language) {
    logger.info("Generating test report for ${testTool}")
    
    try {
        // Create allure results directory
        bat 'if not exist "allure-results" mkdir allure-results'
        
        // Handle different test report formats
        if (language in ['java-maven', 'java-gradle']) {
            // Archive JUnit XML reports
            if (fileExists('target/surefire-reports')) {
                archiveArtifacts artifacts: 'target/surefire-reports/**/*.xml', allowEmptyArchive: true
                
                // Copy test results for Allure
                bat 'if exist "target\\surefire-reports" xcopy /s /y target\\surefire-reports\\*.xml allure-results\\'
            }
            
            // Publish JUnit test results
            publishTestResults testResultsPattern: 'target/surefire-reports/TEST-*.xml'
            
        } else if (language == 'python') {
            // Handle pytest results
            if (fileExists('test-results.xml')) {
                archiveArtifacts artifacts: 'test-results.xml', allowEmptyArchive: true
                publishTestResults testResultsPattern: 'test-results.xml'
            }
        }
        
        logger.info("Test report generated successfully")
        
    } catch (Exception e) {
        logger.warning("Failed to generate test report: ${e.getMessage()}")
    }
}

// Collect test results for detailed reporting
private def collectTestResults(Map config, String language) {
    logger.info("Collecting test results for ${language}")
    
    def results = [summary: "No test results available", failures: []]
    
    try {
        if (language in ['java-maven', 'java-gradle']) {
            // Parse JUnit XML results
            if (fileExists('target/surefire-reports')) {
                def testFiles = findFiles(glob: 'target/surefire-reports/TEST-*.xml')
                def totalTests = 0
                def failedTests = 0
                def errors = []
                
                testFiles.each { file ->
                    try {
                        def testXml = readFile(file.path)
                        
                        // Extract test counts (simple regex parsing)
                        def testsMatch = (testXml =~ /tests="(\d+)"/)
                        def failuresMatch = (testXml =~ /failures="(\d+)"/)
                        def errorsMatch = (testXml =~ /errors="(\d+)"/)
                        
                        if (testsMatch) totalTests += Integer.parseInt(testsMatch[0][1])
                        if (failuresMatch) failedTests += Integer.parseInt(failuresMatch[0][1])
                        if (errorsMatch) failedTests += Integer.parseInt(errorsMatch[0][1])
                        
                        // Extract failure details
                        def failurePattern = /<failure[^>]*message="([^"]*)"[^>]*>([^<]*)<\/failure>/
                        def failureMatches = (testXml =~ failurePattern)
                        failureMatches.each { match ->
                            errors.add([
                                message: match[1],
                                details: match[2].take(200) // Limit details length
                            ])
                        }
                        
                    } catch (Exception e) {
                        logger.warning("Failed to parse test file ${file.path}: ${e.getMessage()}")
                    }
                }
                
                results.summary = "Total: ${totalTests}, Failed: ${failedTests}, Passed: ${totalTests - failedTests}"
                results.failures = errors.take(10) // Limit to first 10 failures
            }
        }
        
    } catch (Exception e) {
        logger.warning("Failed to collect test results: ${e.getMessage()}")
    }
    
    return results
}
