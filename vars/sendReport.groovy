/**
 * Report generation and sending utilities
 * Handles Allure reports, lint reports, and detailed notifications
 */

def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Generating and sending comprehensive reports")
    
    try {
        // Generate Allure report if enabled
        if (config.reports?.allure?.enabled) {
            generateAllureReport(config)
        }
        
        // Generate detailed lint report
        if (config.reports?.lint?.enabled && env.LINT_STATUS) {
            generateDetailedLintReport(config)
        }
        
        // Generate detailed test report
        if (config.reports?.allure?.enabled && env.TEST_STATUS) {
            generateDetailedTestReport(config)
        }
        
        // Send comprehensive report via email
        sendDetailedEmailReport(config, stageResults)
        
        // Send summary to Slack (DISABLED)
        // sendSlackSummaryReport(config, stageResults)
        
    } catch (Exception e) {
        logger.error("Failed to generate/send reports: ${e.getMessage()}")
    }
}

def generateAllureReport(Map config) {
    logger.info("Generating Allure report")
    
    try {
        // Check if allure results exist
        if (!fileExists('allure-results')) {
            logger.warning("No allure-results directory found, creating empty one")
            bat 'mkdir allure-results'
        }
        
        // Generate Allure report
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: config.reports?.allure?.results_path ?: 'allure-results']]
        ])
        
        // Archive the report
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: config.reports?.allure?.report_path ?: 'allure-report',
            reportFiles: 'index.html',
            reportName: 'Allure Report',
            reportTitles: ''
        ])
        
        logger.info("Allure report generated successfully")
        
    } catch (Exception e) {
        logger.error("Failed to generate Allure report: ${e.getMessage()}")
    }
}

def generateDetailedLintReport(Map config) {
    logger.info("Generating detailed lint report")
    
    try {
        def lintStatus = env.LINT_STATUS
        def lintMessage = env.LINT_MESSAGE
        
        if (lintStatus in ['FAILED', 'UNSTABLE']) {
            // Create detailed lint report HTML
            def reportHtml = generateLintReportHtml(config)
            
            // Write report to file
            writeFile file: 'lint-report.html', text: reportHtml
            
            // Archive the report
            archiveArtifacts artifacts: 'lint-report.html', allowEmptyArchive: true
            
            // Publish HTML report
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: '.',
                reportFiles: 'lint-report.html',
                reportName: 'Lint Report',
                reportTitles: ''
            ])
            
            logger.info("Detailed lint report generated")
        }
        
    } catch (Exception e) {
        logger.error("Failed to generate detailed lint report: ${e.getMessage()}")
    }
}

def generateDetailedTestReport(Map config) {
    logger.info("Generating detailed test report")
    
    try {
        def testStatus = env.TEST_STATUS
        def testMessage = env.TEST_MESSAGE
        def testDetails = env.TEST_DETAILS
        
        if (testStatus in ['FAILED', 'UNSTABLE']) {
            // Create detailed test report HTML
            def reportHtml = generateTestReportHtml(config)
            
            // Write report to file
            writeFile file: 'test-report.html', text: reportHtml
            
            // Archive the report
            archiveArtifacts artifacts: 'test-report.html', allowEmptyArchive: true
            
            // Publish HTML report
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: '.',
                reportFiles: 'test-report.html',
                reportName: 'Test Report',
                reportTitles: ''
            ])
            
            logger.info("Detailed test report generated")
        }
        
    } catch (Exception e) {
        logger.error("Failed to generate detailed test report: ${e.getMessage()}")
    }
}

private String generateLintReportHtml(Map config) {
    def lintStatus = env.LINT_STATUS
    def buildInfo = [
        jobName: env.JOB_NAME,
        buildNumber: env.BUILD_NUMBER,
        buildUrl: env.BUILD_URL,
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
    ]
    
    def statusColor = lintStatus == 'FAILED' ? 'red' : 'orange'
    def statusIcon = lintStatus == 'FAILED' ? '❌' : '⚠️'
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Lint Report - ${buildInfo.jobName} #${buildInfo.buildNumber}</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; }
            .header { background-color: #f5f5f5; padding: 15px; border-radius: 5px; }
            .status { color: ${statusColor}; font-weight: bold; font-size: 18px; }
            .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
            .violation { background-color: #fff3cd; padding: 10px; margin: 5px 0; border-radius: 3px; }
            .file-path { font-weight: bold; color: #0066cc; }
            .line-number { color: #666; }
            .message { color: #721c24; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background-color: #f2f2f2; }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>${statusIcon} Lint Report</h1>
            <div class="status">Status: ${lintStatus}</div>
            <table>
                <tr><td><strong>Job:</strong></td><td>${buildInfo.jobName}</td></tr>
                <tr><td><strong>Build:</strong></td><td>#${buildInfo.buildNumber}</td></tr>
                <tr><td><strong>Time:</strong></td><td>${buildInfo.timestamp}</td></tr>
                <tr><td><strong>Build URL:</strong></td><td><a href="${buildInfo.buildUrl}">View Build</a></td></tr>
            </table>
        </div>
        
        <div class="section">
            <h2>Summary</h2>
            <p><strong>Message:</strong> ${env.LINT_MESSAGE ?: 'Lint check completed'}</p>
            <p><strong>Tool:</strong> ${config.tool_for_lint_testing?.java ?: 'checkstyle'}</p>
        </div>
        
        <div class="section">
            <h2>Checkstyle Results</h2>
            <p>Detailed checkstyle violations can be found in the archived artifacts.</p>
            <p><a href="${buildInfo.buildUrl}artifact/target/checkstyle-result.xml">Download Checkstyle XML Report</a></p>
        </div>
        
        <div class="section">
            <h2>Next Steps</h2>
            <ul>
                <li>Review the checkstyle violations above</li>
                <li>Fix the code style issues</li>
                <li>Run the build again to verify fixes</li>
                <li>Consider updating checkstyle rules if needed</li>
            </ul>
        </div>
        
        <div class="section">
            <h2>Build Information</h2>
            <p>This report was generated automatically by the CI/CD pipeline.</p>
            <p>For questions, contact the development team.</p>
        </div>
    </body>
    </html>
    """
}

def sendDetailedEmailReport(Map config, Map stageResults) {
    logger.info("Sending detailed email report")
    
    try {
        def buildInfo = [
            jobName: env.JOB_NAME,
            buildNumber: env.BUILD_NUMBER,
            buildUrl: env.BUILD_URL,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
        ]
        
        def overallStatus = notify.determineBuildStatus(stageResults)
        def subject = getDetailedEmailSubject(overallStatus, buildInfo)
        def body = getDetailedEmailBody(overallStatus, buildInfo, stageResults, config)
        
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/html',
            to: config.notifications?.email?.recipients?.join(',') ?: 'team@company.com',
            attachmentsPattern: 'lint-report.html,target/checkstyle-result.xml'
        )
        
        logger.info("Detailed email report sent successfully")
        
    } catch (Exception e) {
        logger.error("Failed to send detailed email report: ${e.getMessage()}")
    }
}

def sendSlackSummaryReport(Map config, Map stageResults) {
    // SLACK NOTIFICATIONS CURRENTLY DISABLED
    logger.info("Sending Slack summary report")
    
    try {
        def overallStatus = notify.determineBuildStatus(stageResults)
        def message = getSlackSummaryMessage(overallStatus, stageResults)
        def color = getSlackColor(overallStatus)
        
        slackSend (
            channel: config.notifications?.slack?.channel ?: '#builds',
            color: color,
            message: message
        )
        
        logger.info("Slack summary report sent successfully")
        
    } catch (Exception e) {
        logger.error("Failed to send Slack summary report: ${e.getMessage()}")
    }
}

private String getDetailedEmailSubject(String status, Map buildInfo) {
    def emoji = status == 'SUCCESS' ? '✅' : (status == 'UNSTABLE' ? '⚠️' : '❌')
    return "${emoji} Build Report: ${buildInfo.jobName} #${buildInfo.buildNumber} - ${status}"
}

private String generateTestReportHtml(Map config) {
    def testStatus = env.TEST_STATUS
    def testMessage = env.TEST_MESSAGE
    def testDetails = env.TEST_DETAILS
    def buildInfo = [
        jobName: env.JOB_NAME,
        buildNumber: env.BUILD_NUMBER,
        buildUrl: env.BUILD_URL,
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
    ]
    
    def statusColor = testStatus == 'FAILED' ? 'red' : 'orange'
    def statusIcon = testStatus == 'FAILED' ? '❌' : '⚠️'
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Unit Test Report - ${buildInfo.jobName} #${buildInfo.buildNumber}</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; }
            .header { background-color: #f5f5f5; padding: 15px; border-radius: 5px; }
            .status { color: ${statusColor}; font-weight: bold; font-size: 18px; }
            .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
            .failure { background-color: #f8d7da; padding: 10px; margin: 5px 0; border-radius: 3px; }
            .test-name { font-weight: bold; color: #721c24; }
            .error-message { color: #721c24; font-family: monospace; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background-color: #f2f2f2; }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>${statusIcon} Unit Test Report</h1>
            <div class="status">Status: ${testStatus}</div>
            <table>
                <tr><td><strong>Job:</strong></td><td>${buildInfo.jobName}</td></tr>
                <tr><td><strong>Build:</strong></td><td>#${buildInfo.buildNumber}</td></tr>
                <tr><td><strong>Time:</strong></td><td>${buildInfo.timestamp}</td></tr>
                <tr><td><strong>Build URL:</strong></td><td><a href="${buildInfo.buildUrl}">View Build</a></td></tr>
            </table>
        </div>
        
        <div class="section">
            <h2>Test Summary</h2>
            <p><strong>Status:</strong> ${testStatus}</p>
            <p><strong>Message:</strong> ${testMessage}</p>
            <p><strong>Details:</strong> ${testDetails}</p>
            <p><strong>Tool:</strong> ${config.tool_for_unit_testing?.java ?: 'junit'}</p>
        </div>
        
        <div class="section">
            <h2>Test Results</h2>
            <p>Detailed test results can be found in the JUnit reports and Allure report.</p>
            <ul>
                <li><a href="${buildInfo.buildUrl}testReport">JUnit Test Results</a></li>
                <li><a href="${buildInfo.buildUrl}allure">Allure Test Report</a></li>
                <li><a href="${buildInfo.buildUrl}artifact/target/surefire-reports/">Surefire Reports</a></li>
            </ul>
        </div>
        
        <div class="section">
            <h2>Next Steps</h2>
            <ul>
                <li>Review the failed test cases above</li>
                <li>Fix the failing unit tests</li>
                <li>Run tests locally: <code>mvn test</code></li>
                <li>Commit and push your fixes</li>
                <li>Monitor the next build for improvements</li>
            </ul>
        </div>
        
        <div class="section">
            <h2>Build Information</h2>
            <p>This report was generated automatically by the CI/CD pipeline.</p>
            <p>For questions about failing tests, contact the development team.</p>
        </div>
    </body>
    </html>
    """
}

private String getDetailedEmailBody(String status, Map buildInfo, Map stageResults, Map config) {
    def lintStatus = env.LINT_STATUS ?: 'UNKNOWN'
    def lintMessage = env.LINT_MESSAGE ?: 'No lint information available'
    def testStatus = env.TEST_STATUS ?: 'UNKNOWN'
    def testMessage = env.TEST_MESSAGE ?: 'No test information available'
    
    return """
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; }
            .header { background-color: #f8f9fa; padding: 20px; border-radius: 5px; }
            .section { margin: 20px 0; padding: 15px; border: 1px solid #dee2e6; border-radius: 5px; }
            .success { color: green; }
            .warning { color: orange; }
            .error { color: red; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background-color: #f2f2f2; }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>Build Report - ${status}</h1>
            <p><strong>Job:</strong> ${buildInfo.jobName}</p>
            <p><strong>Build:</strong> #${buildInfo.buildNumber}</p>
            <p><strong>Time:</strong> ${buildInfo.timestamp}</p>
            <p><strong>URL:</strong> <a href="${buildInfo.buildUrl}">View Build</a></p>
        </div>
        
        <div class="section">
            <h2>Stage Results</h2>
            <table>
                <tr><th>Stage</th><th>Status</th></tr>
                <tr><td>Lint</td><td class="${lintStatus.toLowerCase()}">${lintStatus}</td></tr>
                <tr><td>Unit Tests</td><td class="${testStatus.toLowerCase()}">${testStatus}</td></tr>
            </table>
        </div>
        
        <div class="section">
            <h2>Lint Details</h2>
            <p><strong>Status:</strong> <span class="${lintStatus.toLowerCase()}">${lintStatus}</span></p>
            <p><strong>Message:</strong> ${lintMessage}</p>
            <p><strong>Tool:</strong> ${config.tool_for_lint_testing?.java ?: 'checkstyle'}</p>
            
            ${lintStatus in ['FAILED', 'UNSTABLE'] ? '''
            <h3>Action Required</h3>
            <ul>
                <li>Review the attached lint report</li>
                <li>Fix the code style violations</li>
                <li>Re-run the build to verify fixes</li>
            </ul>
            ''' : ''}
        </div>
        
        <div class="section">
            <h2>Unit Test Details</h2>
            <p><strong>Status:</strong> <span class="${testStatus.toLowerCase()}">${testStatus}</span></p>
            <p><strong>Message:</strong> ${testMessage}</p>
            <p><strong>Tool:</strong> ${config.tool_for_unit_testing?.java ?: 'junit'}</p>
            
            ${testStatus in ['FAILED', 'UNSTABLE'] ? '''
            <h3>Action Required</h3>
            <ul>
                <li>Review the failing test cases</li>
                <li>Fix the failing unit tests</li>
                <li>Run tests locally: <code>mvn test</code></li>
                <li>Re-run the build to verify fixes</li>
            </ul>
            ''' : ''}
        </div>
        
        <div class="section">
            <h2>Reports</h2>
            <ul>
                <li><a href="${buildInfo.buildUrl}allure">Allure Report</a></li>
                <li><a href="${buildInfo.buildUrl}Lint_Report">Lint Report</a></li>
                <li><a href="${buildInfo.buildUrl}artifact/">Build Artifacts</a></li>
            </ul>
        </div>
        
        <div class="section">
            <h2>Next Steps</h2>
            ${getNextStepsHtml(status, lintStatus)}
        </div>
    </body>
    </html>
    """
}

private String getSlackSummaryMessage(String status, Map stageResults) {
    def emoji = status == 'SUCCESS' ? '✅' : (status == 'UNSTABLE' ? '⚠️' : '❌')
    def lintStatus = env.LINT_STATUS ?: 'UNKNOWN'
    def testStatus = env.TEST_STATUS ?: 'UNKNOWN'
    
    def actionItems = []
    if (lintStatus in ['FAILED', 'UNSTABLE']) {
        actionItems.add("• Review lint violations")
        actionItems.add("• Fix code style issues")
    }
    if (testStatus in ['FAILED', 'UNSTABLE']) {
        actionItems.add("• Review failing tests")
        actionItems.add("• Fix unit test failures")
    }
    
    return """${emoji} *Build ${status}*

*Job:* ${env.JOB_NAME}
*Build:* #${env.BUILD_NUMBER}
*Time:* ${new Date().format('yyyy-MM-dd HH:mm:ss')}

*Stage Results:*
• Lint: ${getLintEmoji(lintStatus)} ${lintStatus}
• Tests: ${getLintEmoji(testStatus)} ${testStatus}

${actionItems.size() > 0 ? """
*⚠️ Action Required:*
${actionItems.join('\n')}
• Re-run build after fixes
""" : ''}

<${env.BUILD_URL}|View Build> | <${env.BUILD_URL}allure|Allure Report> | <${env.BUILD_URL}testReport|Test Results>"""
}

private String getLintEmoji(String status) {
    switch(status) {
        case 'SUCCESS': return '✅'
        case 'UNSTABLE': return '⚠️'
        case 'FAILED': return '❌'
        case 'SKIPPED': return '⏭️'
        default: return 'ℹ️'
    }
}

private String getSlackColor(String status) {
    switch(status) {
        case 'SUCCESS': return 'good'
        case 'UNSTABLE': return 'warning'
        case 'FAILED': return 'danger'
        default: return '#439FE0'
    }
}

private String getNextStepsHtml(String overallStatus, String lintStatus) {
    if (lintStatus in ['FAILED', 'UNSTABLE']) {
        return """
        <p><strong>Lint Issues Detected:</strong></p>
        <ol>
            <li>Download and review the checkstyle report</li>
            <li>Fix the code style violations in your IDE</li>
            <li>Run checkstyle locally: <code>mvn checkstyle:check</code></li>
            <li>Commit and push your fixes</li>
            <li>Monitor the next build for improvements</li>
        </ol>
        """
    } else {
        return "<p>All checks passed! No action required.</p>"
    }
}

return this