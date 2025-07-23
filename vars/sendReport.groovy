/**
 * SIMPLE Report generation utilities
 * - Collect basic test and lint data
 * - Send plain text email summary
 */

// MAIN METHOD: Generate Allure report and send email summary
def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Generating Allure report and build summary")
    
    try {
        // Generate Allure report first
        generateAllureReport()
        
        // Then collect summary and send email
        def buildSummary = collectBuildSummary(config, stageResults)
        sendBuildSummaryEmail(config, buildSummary)
        
    } catch (Exception e) {
        logger.error("Failed to generate reports: ${e.getMessage()}")
    }
}

// GENERATE ALLURE REPORT
def generateAllureReport() {
    logger.info("Generating Allure Report")
    
    try {
        // Create allure-results directory
        if (!fileExists('allure-results')) {
            bat 'mkdir allure-results'
        }
        
        // Copy test results to allure-results
        copyTestResultsToAllure()
        
        // Generate Allure report
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: 'allure-results']]
        ])
        
        // Publish HTML report
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'allure-report',
            reportFiles: 'index.html',
            reportName: 'Allure Report',
            reportTitles: ''
        ])
        
        logger.info("Allure report generated successfully")
        
    } catch (Exception e) {
        logger.warning("Failed to generate Allure report: ${e.getMessage()}")
    }
}

private def copyTestResultsToAllure() {
    try {
        // Copy JUnit results (Maven/Gradle)
        if (fileExists('target/surefire-reports')) {
            bat 'if exist "target\\surefire-reports" xcopy /s /y target\\surefire-reports\\*.xml allure-results\\'
        }
        
        // Copy pytest results (Python)
        if (fileExists('test-results.xml')) {
            bat 'copy test-results.xml allure-results\\'
        }
        
    } catch (Exception e) {
        logger.warning("Failed to copy test results: ${e.getMessage()}")
    }
}

// COLLECT BUILD SUMMARY
def collectBuildSummary(Map config, Map stageResults) {
    logger.info("Collecting build summary data")
    
    def summary = [
        buildInfo: getBuildInfo(),
        stageResults: stageResults ?: getDefaultStageResults(),
        testSummary: getTestSummary(),
        lintSummary: getLintSummary(),
        overallStatus: notify.getBuildStatus()
    ]
    
    return summary
}

// SEND EMAIL SUMMARY
def sendBuildSummaryEmail(Map config, Map buildSummary) {
    logger.info("Sending build summary email")
    
    try {
        def subject = "Build Summary: ${buildSummary.buildInfo.jobName} #${buildSummary.buildInfo.buildNumber} - ${buildSummary.overallStatus}"
        def body = generateEmailBody(buildSummary)
        
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/plain',
            to: config.notifications?.email?.recipients?.join(',') ?: 'smanprit022@gmail.com'
        )
        
        logger.info("Build summary email sent successfully")
        
    } catch (Exception e) {
        logger.error("Failed to send email: ${e.getMessage()}")
    }
}

// HELPER METHODS

private def getBuildInfo() {
    return [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL',
        gitBranch: env.GIT_BRANCH ?: 'Unknown Branch',
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        duration: currentBuild.durationString ?: 'Unknown'
    ]
}

private def getDefaultStageResults() {
    return [
        'Checkout': 'SUCCESS',
        'Build': 'SUCCESS',
        'Test': 'SUCCESS',
        'Lint': 'SUCCESS'
    ]
}

private def getTestSummary() {
    def summary = [total: 0, passed: 0, failed: 0, skipped: 0]
    
    try {
        // Check Maven/Gradle test results
        if (fileExists('target/surefire-reports')) {
            def testFiles = findFiles(glob: 'target/surefire-reports/TEST-*.xml')
            testFiles.each { file ->
                def xml = readFile(file.path)
                summary.total += parseNumber(xml, /tests="(\d+)"/)
                summary.failed += parseNumber(xml, /failures="(\d+)"/)
                summary.failed += parseNumber(xml, /errors="(\d+)"/)
                summary.skipped += parseNumber(xml, /skipped="(\d+)"/)
            }
        }
        
        // Check pytest results
        if (fileExists('test-results.xml')) {
            def xml = readFile('test-results.xml')
            summary.total += parseNumber(xml, /tests="(\d+)"/)
            summary.failed += parseNumber(xml, /failures="(\d+)"/)
            summary.skipped += parseNumber(xml, /skipped="(\d+)"/)
        }
        
        summary.passed = summary.total - summary.failed - summary.skipped
        
    } catch (Exception e) {
        logger.warning("Failed to get test summary: ${e.getMessage()}")
    }
    
    return summary
}

private def getLintSummary() {
    def summary = [violations: 0, tool: 'none']
    
    try {
        // Check checkstyle (Java)
        if (fileExists('target/checkstyle-result.xml')) {
            def xml = readFile('target/checkstyle-result.xml')
            summary.violations = (xml =~ /<error/).size()
            summary.tool = 'checkstyle'
        }
        
        // Check pylint (Python)
        if (fileExists('pylint-report.txt')) {
            def report = readFile('pylint-report.txt')
            summary.violations = report.split('\n').findAll { it.contains(':') && !it.contains('*') }.size()
            summary.tool = 'pylint'
        }
        
    } catch (Exception e) {
        logger.warning("Failed to get lint summary: ${e.getMessage()}")
    }
    
    return summary
}

private def parseNumber(String text, String pattern) {
    try {
        def match = (text =~ pattern)
        return match ? Integer.parseInt(match[0][1]) : 0
    } catch (Exception e) {
        return 0
    }
}

private String generateEmailBody(Map buildSummary) {
    def status = buildSummary.overallStatus
    def buildInfo = buildSummary.buildInfo
    def stageResults = buildSummary.stageResults
    def testSummary = buildSummary.testSummary
    def lintSummary = buildSummary.lintSummary
    
    return """
===============================================
BUILD SUMMARY - ${status}
===============================================

BUILD INFO:
-----------
Job:      ${buildInfo.jobName}
Build:    #${buildInfo.buildNumber}
Status:   ${status}
Duration: ${buildInfo.duration}
Branch:   ${buildInfo.gitBranch}
Time:     ${buildInfo.timestamp}
URL:      ${buildInfo.buildUrl}

STAGES:
-------
${generateStageText(stageResults)}

TESTS:
------
Total:    ${testSummary.total}
Passed:   ${testSummary.passed}
Failed:   ${testSummary.failed}
Skipped:  ${testSummary.skipped}

LINT:
-----
Tool:       ${lintSummary.tool}
Violations: ${lintSummary.violations}

===============================================
${getStatusMessage(status)}
===============================================
    """
}

private String generateStageText(Map stageResults) {
    def text = ""
    stageResults.each { stage, result ->
        def emoji = result == 'SUCCESS' ? '✓' : '✗'
        text += "${stage.padRight(15)} ${emoji} ${result}\n"
    }
    return text
}

private String getStatusMessage(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS': return 'All stages completed successfully! 🎉'
        case 'FAILED': return 'Build failed. Please check the logs.'
        case 'UNSTABLE': return 'Build completed with warnings.'
        default: return 'Build completed.'
    }
}

return this