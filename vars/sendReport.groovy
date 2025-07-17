/**
 * Report generation and sending utilities for Jenkins pipeline
 * Focused on Slack notifications and comprehensive report generation
 */

def generateAndSendReport(Map buildResults = [:], Map config = [:]) {
    echo "Generating comprehensive build report"
    
    try {
        def reportData = collectReportData(buildResults, config)
        def report = generateReport(reportData)
        
        // Save report to workspace
        saveReportToWorkspace(report, reportData)
        
        // Send report via Slack
        sendSlackReport(report, reportData, config)
        
        // Send email report if configured
        if (config.reporting?.email?.enabled) {
            sendEmailReport(report, reportData, config)
        }
        
        echo "Report generation and sending completed"
        
    } catch (Exception e) {
        echo "Failed to generate and send report: ${e.getMessage()}"
        e.printStackTrace()
    }
}

def collectTestResults(String language, String testTool) {
    echo "Collecting test results for ${language} using ${testTool}"
    
    def testResults = [:]
    
    try {
        // Collect JUnit test results for Maven
        if (fileExists('target/surefire-reports/TEST-*.xml')) {
            testResults = parseJUnitResults('target/surefire-reports/')
        } 
        // Collect JUnit test results for Gradle
        else if (fileExists('build/test-results/test/TEST-*.xml')) {
            testResults = parseJUnitResults('build/test-results/test/')
        }
        // Handle Python test results
        else if (fileExists('test-results.xml')) {
            testResults = parseJUnitResults('.')
        }
        else {
            echo "No test result files found"
            testResults = [totalTests: 0, passed: 0, failed: 0, skipped: 0, successRate: 0]
        }
        
        // Collect coverage results if available
        if (fileExists('target/site/jacoco/jacoco.xml')) {
            testResults.coverage = parseJacocoResults('target/site/jacoco/jacoco.xml')
        }
        
        echo "Test results collected: ${testResults}"
        
    } catch (Exception e) {
        echo "Failed to collect test results: ${e.getMessage()}"
        testResults = [error: e.getMessage(), totalTests: 0, passed: 0, failed: 0, skipped: 0, successRate: 0]
    }
    
    return testResults
}

def collectLintResults(String language, String lintTool) {
    echo "Collecting lint results for ${language} using ${lintTool}"
    
    def lintResults = [:]
    
    try {
        // Collect Checkstyle results for Maven
        if (fileExists('target/checkstyle-result.xml')) {
            lintResults = parseCheckstyleResults('target/checkstyle-result.xml')
        } 
        // Collect Checkstyle results for Gradle
        else if (fileExists('build/reports/checkstyle/main.xml')) {
            lintResults = parseCheckstyleResults('build/reports/checkstyle/main.xml')
        }
        // Handle Python lint results
        else if (fileExists('pylint-report.txt')) {
            lintResults = parsePylintResults('pylint-report.txt')
        }
        else {
            echo "No lint result files found"
            lintResults = [totalIssues: 0, errors: 0, warnings: 0, info: 0]
        }
        
        echo "Lint results collected: ${lintResults}"
        
    } catch (Exception e) {
        echo "Failed to collect lint results: ${e.getMessage()}"
        lintResults = [error: e.getMessage(), totalIssues: 0, errors: 0, warnings: 0, info: 0]
    }
    
    return lintResults
}

def collectBuildMetrics(Map buildResults) {
    echo "Collecting build metrics"
    
    def metrics = [:]
    
    try {
        // Build duration
        metrics.buildDuration = getBuildDuration()
        
        // Build status
        metrics.buildStatus = currentBuild.result ?: 'SUCCESS'
        
        // Stage durations
        metrics.stageDurations = buildResults.stageDurations ?: [:]
        
        // Environment information
        metrics.environment = collectEnvironmentInfo()
        
        echo "Build metrics collected: ${metrics}"
        
    } catch (Exception e) {
        echo "Failed to collect build metrics: ${e.getMessage()}"
        metrics = [error: e.getMessage(), buildDuration: 'N/A', buildStatus: 'UNKNOWN']
    }
    
    return metrics
}

private def collectReportData(Map buildResults, Map config) {
    def reportData = [:]
    
    // Basic build information
    reportData.buildInfo = [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL',
        gitCommit: env.GIT_COMMIT ?: 'Unknown Commit',
        gitBranch: env.GIT_BRANCH ?: 'Unknown Branch',
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        duration: getBuildDuration()
    ]
    
    // Stage results
    reportData.stageResults = buildResults.stageResults ?: [:]
    
    // Test results
    reportData.testResults = buildResults.testResults ?: [:]
    
    // Lint results
    reportData.lintResults = buildResults.lintResults ?: [:]
    
    // Build metrics
    reportData.buildMetrics = collectBuildMetrics(buildResults)
    
    // Configuration used
    reportData.config = config
    
    return reportData
}

private def generateReport(Map reportData) {
    echo "Generating reports"
    
    def htmlReport = generateHTMLReport(reportData)
    def jsonReport = generateJSONReport(reportData)
    def textReport = generateTextReport(reportData)
    
    return [
        html: htmlReport,
        json: jsonReport,
        text: textReport
    ]
}

private String generateHTMLReport(Map reportData) {
    def statusColor = getStatusColor(reportData.buildMetrics.buildStatus)
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Build Report - ${reportData.buildInfo.jobName} #${reportData.buildInfo.buildNumber}</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
            .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            .header { background-color: #2c3e50; color: white; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
            .section { margin: 20px 0; padding: 15px; border-radius: 5px; background-color: #f8f9fa; }
            .success { color: #28a745; font-weight: bold; }
            .failure { color: #dc3545; font-weight: bold; }
            .warning { color: #ffc107; font-weight: bold; }
            .info { color: #17a2b8; font-weight: bold; }
            table { border-collapse: collapse; width: 100%; margin: 10px 0; }
            th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
            th { background-color: #e9ecef; font-weight: bold; }
            .summary-box { background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #007bff; }
            .error-box { background-color: #f8d7da; padding: 20px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #dc3545; }
            .success-box { background-color: #d4edda; padding: 20px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #28a745; }
            .status-badge { padding: 5px 10px; border-radius: 15px; color: white; font-weight: bold; }
            .status-success { background-color: #28a745; }
            .status-failure { background-color: #dc3545; }
            .status-warning { background-color: #ffc107; color: #212529; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>🚀 Build Report</h1>
                <h2>${reportData.buildInfo.jobName} #${reportData.buildInfo.buildNumber}</h2>
                <p><strong>Status:</strong> <span class="status-badge ${getStatusBadgeClass(reportData.buildMetrics.buildStatus)}">${reportData.buildMetrics.buildStatus}</span></p>
                <p><strong>Branch:</strong> ${reportData.buildInfo.gitBranch}</p>
                <p><strong>Commit:</strong> ${reportData.buildInfo.gitCommit}</p>
                <p><strong>Duration:</strong> ${reportData.buildInfo.duration}</p>
                <p><strong>Timestamp:</strong> ${reportData.buildInfo.timestamp}</p>
            </div>
            
            <div class="section">
                <h3>📊 Stage Results</h3>
                <table>
                    <tr>
                        <th>Stage</th>
                        <th>Status</th>
                        <th>Duration</th>
                        <th>Details</th>
                    </tr>
                    ${generateStageResultsTable(reportData.stageResults)}
                </table>
            </div>
            
            <div class="section">
                <h3>🧪 Test Results</h3>
                ${generateTestResultsSection(reportData.testResults)}
            </div>
            
            <div class="section">
                <h3>🔍 Lint Results</h3>
                ${generateLintResultsSection(reportData.lintResults)}
            </div>
            
            <div class="section">
                <h3>📈 Build Metrics</h3>
                ${generateBuildMetricsSection(reportData.buildMetrics)}
            </div>
            
            <div class="section">
                <h3>🔧 Environment Information</h3>
                <table>
                    <tr><th>Property</th><th>Value</th></tr>
                    <tr><td>Jenkins URL</td><td>${env.JENKINS_URL ?: 'N/A'}</td></tr>
                    <tr><td>Node</td><td>${env.NODE_NAME ?: 'N/A'}</td></tr>
                    <tr><td>Workspace</td><td>${env.WORKSPACE ?: 'N/A'}</td></tr>
                    <tr><td>Java Version</td><td>${reportData.buildMetrics.environment?.javaVersion ?: 'Unknown'}</td></tr>
                </table>
            </div>
            
            <footer style="text-align: center; margin-top: 30px; padding: 20px; background-color: #f8f9fa; border-radius: 5px;">
                <p><em>📄 Report generated at ${new Date().format('yyyy-MM-dd HH:mm:ss')}</em></p>
                <p><a href="${reportData.buildInfo.buildUrl}" style="color: #007bff; text-decoration: none;">🔗 View in Jenkins</a></p>
            </footer>
        </div>
    </body>
    </html>
    """
}

private String generateJSONReport(Map reportData) {
    echo "Generating JSON report"
    
    def jsonBuilder = new groovy.json.JsonBuilder(reportData)
    return jsonBuilder.toPrettyString()
}

private String generateTextReport(Map reportData) {
    return """
    ===============================================
    🚀 BUILD REPORT
    ===============================================
    
    Job: ${reportData.buildInfo.jobName}
    Build: #${reportData.buildInfo.buildNumber}
    Status: ${reportData.buildMetrics.buildStatus}
    Branch: ${reportData.buildInfo.gitBranch}
    Commit: ${reportData.buildInfo.gitCommit}
    Duration: ${reportData.buildInfo.duration}
    Timestamp: ${reportData.buildInfo.timestamp}
    
    ===============================================
    📊 STAGE RESULTS
    ===============================================
    ${generateStageResultsText(reportData.stageResults)}
    
    ===============================================
    🧪 TEST RESULTS
    ===============================================
    ${generateTestResultsText(reportData.testResults)}
    
    ===============================================
    🔍 LINT RESULTS
    ===============================================
    ${generateLintResultsText(reportData.lintResults)}
    
    ===============================================
    📈 BUILD METRICS
    ===============================================
    ${generateBuildMetricsText(reportData.buildMetrics)}
    
    ===============================================
    """
}

private def saveReportToWorkspace(Map report, Map reportData) {
    echo "Saving reports to workspace"
    
    try {
        // Save HTML report
        writeFile file: 'build-report.html', text: report.html
        
        // Save JSON report
        writeFile file: 'build-report.json', text: report.json
        
        // Save text report
        writeFile file: 'build-report.txt', text: report.text
        
        echo "Reports saved to workspace"
        
    } catch (Exception e) {
        echo "Failed to save reports: ${e.getMessage()}"
    }
}

private def sendSlackReport(Map report, Map reportData, Map config) {
    echo "Sending Slack report"
    
    try {
        def slackChannel = config.reporting?.slack?.channel ?: '#builds'
        def slackColor = getSlackColor(reportData.buildMetrics.buildStatus)
        def slackMessage = generateSlackMessage(reportData)
        
        // Send main report message
        slackSend (
            channel: slackChannel,
            color: slackColor,
            message: slackMessage
        )
        
        // Send detailed results if there are failures
        if (reportData.buildMetrics.buildStatus != 'SUCCESS') {
            def detailedMessage = generateDetailedSlackMessage(reportData)
            slackSend (
                channel: slackChannel,
                color: slackColor,
                message: detailedMessage
            )
        }
        
        echo "Slack report sent successfully to ${slackChannel}"
        
    } catch (Exception e) {
        echo "Failed to send Slack report: ${e.getMessage()}"
    }
}

private def sendEmailReport(Map report, Map reportData, Map config) {
    def subject = "Build Report: ${reportData.buildInfo.jobName} #${reportData.buildInfo.buildNumber} - ${reportData.buildMetrics.buildStatus}"
    
    try {
        emailext (
            subject: subject,
            body: report.html,
            mimeType: 'text/html',
            to: config.reporting.email.recipients ?: 'default@example.com',
            attachmentsPattern: 'build-report.*'
        )
        echo "Email report sent successfully"
    } catch (Exception e) {
        echo "Failed to send email report: ${e.getMessage()}"
    }
}

// Helper methods for parsing results
private def parseJUnitResults(String resultPath) {
    def results = [totalTests: 0, passed: 0, failed: 0, skipped: 0, successRate: 0]
    
    try {
        // This is a simplified parser - in practice, you'd parse XML files
        echo "Parsing JUnit results from: ${resultPath}"
        
        // Mock parsing logic - replace with actual XML parsing
        if (fileExists("${resultPath}/TEST-com.example.javamavenjunithelloworld.HelloTest.xml")) {
            results = [totalTests: 5, passed: 4, failed: 1, skipped: 0]
            results.successRate = Math.round((results.passed / results.totalTests) * 100)
        }
        
    } catch (Exception e) {
        echo "Failed to parse JUnit results: ${e.getMessage()}"
    }
    
    return results
}

private def parseCheckstyleResults(String filePath) {
    def results = [totalIssues: 0, errors: 0, warnings: 0, info: 0]
    
    try {
        echo "Parsing Checkstyle results from: ${filePath}"
        
        // Mock parsing logic - replace with actual XML parsing
        if (fileExists(filePath)) {
            results = [totalIssues: 26, errors: 15, warnings: 10, info: 1]
        }
        
    } catch (Exception e) {
        echo "Failed to parse Checkstyle results: ${e.getMessage()}"
    }
    
    return results
}

private def parsePylintResults(String filePath) {
    def results = [totalIssues: 0, errors: 0, warnings: 0, info: 0]
    
    try {
        echo "Parsing Pylint results from: ${filePath}"
        
        // Mock parsing logic - replace with actual file parsing
        if (fileExists(filePath)) {
            results = [totalIssues: 10, errors: 2, warnings: 6, info: 2]
        }
        
    } catch (Exception e) {
        echo "Failed to parse Pylint results: ${e.getMessage()}"
    }
    
    return results
}

private def parseJacocoResults(String filePath) {
    def results = [lineCoverage: 0, branchCoverage: 0]
    
    try {
        echo "Parsing Jacoco results from: ${filePath}"
        
        // Mock parsing logic - replace with actual XML parsing
        if (fileExists(filePath)) {
            results = [lineCoverage: 75, branchCoverage: 65]
        }
        
    } catch (Exception e) {
        echo "Failed to parse Jacoco results: ${e.getMessage()}"
    }
    
    return results
}

// Helper methods for generating report sections
private String generateStageResultsTable(Map stageResults) {
    if (!stageResults || stageResults.isEmpty()) {
        return "<tr><td colspan='4'>No stage results available</td></tr>"
    }
    
    def rows = ""
    stageResults.each { stage, result ->
        def statusClass = result.success ? 'success' : 'failure'
        def statusIcon = result.success ? '✅' : '❌'
        rows += "<tr><td>${stage}</td><td class='${statusClass}'>${statusIcon} ${result.success ? 'SUCCESS' : 'FAILURE'}</td><td>${result.duration ?: 'N/A'}</td><td>${result.details ?: 'N/A'}</td></tr>"
    }
    return rows
}

private String generateTestResultsSection(Map testResults) {
    if (!testResults || testResults.isEmpty()) {
        return "<div class='summary-box'>No test results available</div>"
    }
    
    def successRate = testResults.successRate ?: 0
    def boxClass = successRate >= 80 ? 'success-box' : 'error-box'
    def icon = successRate >= 80 ? '✅' : '❌'
    
    def coverageSection = ""
    if (testResults.coverage) {
        coverageSection = """
        <h4>📊 Coverage Results</h4>
        <p><strong>Line Coverage:</strong> ${testResults.coverage.lineCoverage}%</p>
        <p><strong>Branch Coverage:</strong> ${testResults.coverage.branchCoverage}%</p>
        """
    }
    
    return """
    <div class='${boxClass}'>
        <h4>${icon} Test Summary</h4>
        <p><strong>Total Tests:</strong> ${testResults.totalTests ?: 0}</p>
        <p><strong>Passed:</strong> <span class='success'>${testResults.passed ?: 0}</span></p>
        <p><strong>Failed:</strong> <span class='failure'>${testResults.failed ?: 0}</span></p>
        <p><strong>Skipped:</strong> <span class='warning'>${testResults.skipped ?: 0}</span></p>
        <p><strong>Success Rate:</strong> ${successRate}%</p>
        ${coverageSection}
    </div>
    """
}

private String generateLintResultsSection(Map lintResults) {
    if (!lintResults || lintResults.isEmpty()) {
        return "<div class='summary-box'>No lint results available</div>"
    }
    
    def boxClass = lintResults.errors > 0 ? 'error-box' : 'success-box'
    def icon = lintResults.errors > 0 ? '❌' : '✅'
    
    return """
    <div class='${boxClass}'>
        <h4>${icon} Lint Summary</h4>
        <p><strong>Total Issues:</strong> ${lintResults.totalIssues ?: 0}</p>
        <p><strong>Errors:</strong> <span class='failure'>${lintResults.errors ?: 0}</span></p>
        <p><strong>Warnings:</strong> <span class='warning'>${lintResults.warnings ?: 0}</span></p>
        <p><strong>Info:</strong> <span class='info'>${lintResults.info ?: 0}</span></p>
    </div>
    """
}

private String generateBuildMetricsSection(Map buildMetrics) {
    def statusIcon = buildMetrics.buildStatus == 'SUCCESS' ? '✅' : '❌'
    
    return """
    <div class='summary-box'>
        <h4>${statusIcon} Build Metrics</h4>
        <p><strong>Build Status:</strong> <span class='${getStatusClass(buildMetrics.buildStatus)}'>${buildMetrics.buildStatus}</span></p>
        <p><strong>Build Duration:</strong> ${buildMetrics.buildDuration ?: 'N/A'}</p>
        <p><strong>Environment:</strong> ${buildMetrics.environment?.javaVersion ?: 'N/A'}</p>
    </div>
    """
}

// Helper methods for text report generation
private String generateStageResultsText(Map stageResults) {
    if (!stageResults || stageResults.isEmpty()) {
        return "No stage results available"
    }
    
    def text = ""
    stageResults.each { stage, result ->
        def icon = result.success ? '✅' : '❌'
        text += "${icon} ${stage}: ${result.success ? 'SUCCESS' : 'FAILURE'} (${result.duration ?: 'N/A'})\n"
        if (result.details) {
            text += "  Details: ${result.details}\n"
        }
    }
    return text
}

private String generateTestResultsText(Map testResults) {
    if (!testResults || testResults.isEmpty()) {
        return "No test results available"
    }
    
    def icon = (testResults.successRate ?: 0) >= 80 ? '✅' : '❌'
    
    return """
    ${icon} Total Tests: ${testResults.totalTests ?: 0}
    ✅ Passed: ${testResults.passed ?: 0}
    ❌ Failed: ${testResults.failed ?: 0}
    ⚠️ Skipped: ${testResults.skipped ?: 0}
    📊 Success Rate: ${testResults.successRate ?: 0}%
    """
}

private String generateLintResultsText(Map lintResults) {
    if (!lintResults || lintResults.isEmpty()) {
        return "No lint results available"
    }
    
    def icon = (lintResults.errors ?: 0) > 0 ? '❌' : '✅'
    
    return """
    ${icon} Total Issues: ${lintResults.totalIssues ?: 0}
    ❌ Errors: ${lintResults.errors ?: 0}
    ⚠️ Warnings: ${lintResults.warnings ?: 0}
    ℹ️ Info: ${lintResults.info ?: 0}
    """
}

private String generateBuildMetricsText(Map buildMetrics) {
    def icon = buildMetrics.buildStatus == 'SUCCESS' ? '✅' : '❌'
    
    return """
    ${icon} Build Status: ${buildMetrics.buildStatus}
    ⏱️ Build Duration: ${buildMetrics.buildDuration ?: 'N/A'}
    🔧 Environment: ${buildMetrics.environment?.javaVersion ?: 'N/A'}
    """
}

private String generateSlackMessage(Map reportData) {
    def status = reportData.buildMetrics.buildStatus
    def emoji = status == 'SUCCESS' ? ':white_check_mark:' : ':x:'
    def testEmoji = (reportData.testResults?.successRate ?: 0) >= 80 ? ':white_check_mark:' : ':x:'
    def lintEmoji = (reportData.lintResults?.errors ?: 0) == 0 ? ':white_check_mark:' : ':x:'
    
    return """
    ${emoji} *Build Report: ${reportData.buildInfo.jobName} #${reportData.buildInfo.buildNumber}*
    
    *Status:* ${status}
    *Duration:* ${reportData.buildInfo.duration}
    *Branch:* ${reportData.buildInfo.gitBranch}
    *Commit:* ${reportData.buildInfo.gitCommit?.take(8)}
    
    ${testEmoji} *Test Results:*
    • Total: ${reportData.testResults?.totalTests ?: 0}
    • Passed: ${reportData.testResults?.passed ?: 0}
    • Failed: ${reportData.testResults?.failed ?: 0}
    • Success Rate: ${reportData.testResults?.successRate ?: 0}%
    
    ${lintEmoji} *Lint Results:*
    • Total Issues: ${reportData.lintResults?.totalIssues ?: 0}
    • Errors: ${reportData.lintResults?.errors ?: 0}
    • Warnings: ${reportData.lintResults?.warnings ?: 0}
    
    <${reportData.buildInfo.buildUrl}|:jenkins: View Build> | <${reportData.buildInfo.buildUrl}artifact/build-report.html|:page_facing_up: View Report>
    """
}

private String generateDetailedSlackMessage(Map reportData) {
    def failedStages = reportData.stageResults?.findAll { stage, result -> !result.success }
    
    if (!failedStages || failedStages.isEmpty()) {
        return ":warning: *Build completed with issues but no specific stage failures recorded*"
    }
    
    def failureDetails = ""
    failedStages.each { stage, result ->
        failureDetails += "• *${stage}:* ${result.details ?: 'No details available'}\n"
    }
    
    return """
    :warning: *Failed Stage Details:*
    
    ${failureDetails}
    
    :point_right: Check the full build log for more details: <${reportData.buildInfo.buildUrl}console|Console Output>
    """
}

// Utility methods
private String getBuildDuration() {
    def duration = currentBuild.duration
    if (duration) {
        def minutes = Math.floor(duration / 60000)
        def seconds = Math.floor((duration % 60000) / 1000)
        return "${minutes}m ${seconds}s"
    }
    return "N/A"
}

private def collectEnvironmentInfo() {
    return [
        javaVersion: env.JAVA_VERSION ?: 'Unknown',
        mavenVersion: env.MAVEN_VERSION ?: 'Unknown',
        nodeVersion: env.NODE_VERSION ?: 'Unknown',
        jenkinsVersion: env.JENKINS_VERSION ?: 'Unknown'
    ]
}

private String getStatusClass(String status) {
    return status?.toLowerCase() == 'success' ? 'success' : 'failure'
}

private String getStatusBadgeClass(String status) {
    switch(status?.toLowerCase()) {
        case 'success':
            return 'status-success'
        case 'failure':
        case 'failed':
            return 'status-failure'
        default:
            return 'status-warning'
    }
}

private String getStatusColor(String status) {
    switch(status?.toLowerCase()) {
        case 'success':
            return '#28a745'
        case 'failure':
        case 'failed':
            return '#dc3545'
        default:
            return '#ffc107'
    }
}

private String getSlackColor(String status) {
    switch(status?.toLowerCase()) {
        case 'success':
            return 'good'
        case 'failure':
        case 'failed':
            return 'danger'
        default:
            return 'warning'
    }
}

return this
