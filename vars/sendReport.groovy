/**
 * SIMPLIFIED Report generation utilities
 * Basic structure: Allure + Lint + Test reports only
 */

// 1. MAIN METHOD: Generate and send all reports
def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Generating basic reports")
    
    try {
        // Generate Allure report
        generateAllureReport(config)
        
        // Always generate lint report
        generateLintReport(config)
        
        // Always generate test report
        generateTestReport(config)
        
        // Send email with reports
        sendEmailWithReports(config, stageResults)
        
    } catch (Exception e) {
        logger.error("Failed to generate reports: ${e.getMessage()}")
    }
}

// 2. GENERATE ALLURE REPORT
def generateAllureReport(Map config) {
    logger.info("Generating Allure report")
    
    try {
        // Create allure results directory if not exists
        if (!fileExists('allure-results')) {
            bat 'mkdir allure-results'
        }
        
        // Generate Allure report
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: 'allure-results']]
        ])
        
        logger.info("Allure report generated successfully")
        
    } catch (Exception e) {
        logger.warning("Failed to generate Allure report: ${e.getMessage()}")
    }
}

// 3. GENERATE LINT REPORT
def generateLintReport(Map config) {
    logger.info("Generating lint report")
    
    try {
        // Archive checkstyle XML if exists
        if (fileExists('target/checkstyle-result.xml')) {
            archiveArtifacts artifacts: 'target/checkstyle-result.xml', allowEmptyArchive: true
            logger.info("Checkstyle XML archived")
        }
        
        // Create simple HTML lint report
        def reportHtml = createLintReportHtml()
        writeFile file: 'lint-report.html', text: reportHtml
        archiveArtifacts artifacts: 'lint-report.html', allowEmptyArchive: true
        
        logger.info("Lint report generated successfully")
        
    } catch (Exception e) {
        logger.warning("Failed to generate lint report: ${e.getMessage()}")
    }
}

// 4. GENERATE TEST REPORT
def generateTestReport(Map config) {
    logger.info("Generating test report")
    
    try {
        // Archive JUnit XML if exists
        if (fileExists('target/surefire-reports')) {
            archiveArtifacts artifacts: 'target/surefire-reports/**/*.xml', allowEmptyArchive: true
            logger.info("JUnit XML archived")
        }
        
        // Create simple HTML test report
        def reportHtml = createTestReportHtml()
        writeFile file: 'test-report.html', text: reportHtml
        archiveArtifacts artifacts: 'test-report.html', allowEmptyArchive: true
        
        logger.info("Test report generated successfully")
        
    } catch (Exception e) {
        logger.warning("Failed to generate test report: ${e.getMessage()}")
    }
}

// 5. SEND EMAIL WITH REPORTS
def sendEmailWithReports(Map config, Map stageResults) {
    logger.info("Sending email with reports")
    
    try {
        def buildStatus = notify.getBuildStatus()
        def subject = "Build Report: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${buildStatus}"
        def body = createEmailBody(buildStatus, stageResults)
        
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/html',
            to: config.notifications?.email?.recipients?.join(',') ?: 'team@company.com',
            attachmentsPattern: 'lint-report.html,test-report.html,target/checkstyle-result.xml'
        )
        
        logger.info("Email with reports sent successfully")
        
    } catch (Exception e) {
        logger.error("Failed to send email with reports: ${e.getMessage()}")
    }
}

// 6. HELPER METHODS

private String createLintReportHtml() {
    def lintStatus = env.LINT_STATUS ?: 'SUCCESS'
    def buildInfo = getBuildInfo()
    def statusColor = lintStatus == 'SUCCESS' ? 'green' : (lintStatus == 'UNSTABLE' ? 'orange' : 'red')
    
    return """
    <html>
    <head><title>Lint Report</title></head>
    <body style="font-family: Arial;">
        <h1>Lint Report - <span style="color: ${statusColor};">${lintStatus}</span></h1>
        <table border="1" style="border-collapse: collapse;">
            <tr><td><b>Job:</b></td><td>${buildInfo.jobName}</td></tr>
            <tr><td><b>Build:</b></td><td>#${buildInfo.buildNumber}</td></tr>
            <tr><td><b>Status:</b></td><td style="color: ${statusColor};"><b>${lintStatus}</b></td></tr>
            <tr><td><b>Time:</b></td><td>${new Date().format('yyyy-MM-dd HH:mm:ss')}</td></tr>
        </table>
        
        <h2>Summary</h2>
        <p><b>Lint Status:</b> ${lintStatus}</p>
        <p><b>Tool:</b> checkstyle</p>
        <p><b>Message:</b> ${getLintMessage(lintStatus)}</p>
        
        <h2>Next Steps</h2>
        ${getLintNextSteps(lintStatus)}
    </body>
    </html>
    """
}

private String createTestReportHtml() {
    def testStatus = env.TEST_STATUS ?: 'SUCCESS'
    def buildInfo = getBuildInfo()
    def statusColor = testStatus == 'SUCCESS' ? 'green' : (testStatus == 'UNSTABLE' ? 'orange' : 'red')
    
    return """
    <html>
    <head><title>Test Report</title></head>
    <body style="font-family: Arial;">
        <h1>Test Report - <span style="color: ${statusColor};">${testStatus}</span></h1>
        <table border="1" style="border-collapse: collapse;">
            <tr><td><b>Job:</b></td><td>${buildInfo.jobName}</td></tr>
            <tr><td><b>Build:</b></td><td>#${buildInfo.buildNumber}</td></tr>
            <tr><td><b>Status:</b></td><td style="color: ${statusColor};"><b>${testStatus}</b></td></tr>
            <tr><td><b>Time:</b></td><td>${new Date().format('yyyy-MM-dd HH:mm:ss')}</td></tr>
        </table>
        
        <h2>Summary</h2>
        <p><b>Test Status:</b> ${testStatus}</p>
        <p><b>Tool:</b> junit</p>
        <p><b>Message:</b> ${getTestMessage(testStatus)}</p>
        
        <h2>Next Steps</h2>
        ${getTestNextSteps(testStatus)}
    </body>
    </html>
    """
}

private String createEmailBody(String buildStatus, Map stageResults) {
    def buildInfo = getBuildInfo()
    def lintStatus = env.LINT_STATUS ?: 'SUCCESS'
    def testStatus = env.TEST_STATUS ?: 'SUCCESS'
    
    return """
    <html>
    <body style="font-family: Arial;">
        <h1>Build Report - ${buildStatus}</h1>
        
        <h2>Build Information</h2>
        <table border="1" style="border-collapse: collapse;">
            <tr><td><b>Job:</b></td><td>${buildInfo.jobName}</td></tr>
            <tr><td><b>Build:</b></td><td>#${buildInfo.buildNumber}</td></tr>
            <tr><td><b>Status:</b></td><td>${buildStatus}</td></tr>
            <tr><td><b>Time:</b></td><td>${new Date().format('yyyy-MM-dd HH:mm:ss')}</td></tr>
            <tr><td><b>URL:</b></td><td><a href="${buildInfo.buildUrl}">View Build</a></td></tr>
        </table>
        
        <h2>Stage Results</h2>
        <table border="1" style="border-collapse: collapse;">
            <tr><th>Stage</th><th>Status</th></tr>
            <tr><td>Lint</td><td>${lintStatus}</td></tr>
            <tr><td>Unit Tests</td><td>${testStatus}</td></tr>
        </table>
        
        <h2>Reports</h2>
        <ul>
            <li><a href="${buildInfo.buildUrl}allure">Allure Report</a></li>
            <li>Lint Report (attached)</li>
            <li>Test Report (attached)</li>
        </ul>
        
        <h2>Action Required</h2>
        ${getActionItems(lintStatus, testStatus)}
    </body>
    </html>
    """
}

private def getBuildInfo() {
    return [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL'
    ]
}

private String getActionItems(String lintStatus, String testStatus) {
    def actions = []
    
    if (lintStatus in ['FAILED', 'UNSTABLE']) {
        actions.add("Fix lint violations")
    }
    if (testStatus in ['FAILED', 'UNSTABLE']) {
        actions.add("Fix failing tests")
    }
    
    if (actions.isEmpty()) {
        return "<p>✅ No action required - all checks passed!</p>"
    } else {
        return "<ul><li>${actions.join('</li><li>')}</li></ul>"
    }
}

private String getLintMessage(String status) {
    switch(status) {
        case 'SUCCESS': return 'All lint checks passed successfully!'
        case 'UNSTABLE': return 'Lint found issues but marked as non-critical'
        case 'FAILED': return 'Lint checks failed - action required'
        default: return 'Lint status unknown'
    }
}

private String getTestMessage(String status) {
    switch(status) {
        case 'SUCCESS': return 'All unit tests passed successfully!'
        case 'UNSTABLE': return 'Some tests failed but marked as non-critical'
        case 'FAILED': return 'Unit tests failed - action required'
        default: return 'Test status unknown'
    }
}

private String getLintNextSteps(String status) {
    if (status == 'SUCCESS') {
        return "<p>✅ Great! All lint checks passed. No action needed.</p>"
    } else {
        return """
        <ul>
            <li>Review checkstyle violations</li>
            <li>Fix code style issues</li>
            <li>Run locally: <code>mvn checkstyle:check</code></li>
            <li>Commit and push fixes</li>
        </ul>
        """
    }
}

private String getTestNextSteps(String status) {
    if (status == 'SUCCESS') {
        return "<p>✅ Excellent! All unit tests passed. No action needed.</p>"
    } else {
        return """
        <ul>
            <li>Review failing test cases</li>
            <li>Fix unit test failures</li>
            <li>Run locally: <code>mvn test</code></li>
            <li>Commit and push fixes</li>
        </ul>
        """
    }
}

return this