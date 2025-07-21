def notifyBuildStatus(String status, Map config = [:]) {
    logger.info("Sending build notification: ${status}")
    
    try {
        def buildInfo = getBuildInfo()
        def notificationData = [
            status: status,
            buildInfo: buildInfo,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
        ]
        
        // Send email notification
        sendEmailNotification(notificationData, config)
        
        // Log to console
        logNotification(notificationData)
        
    } catch (Exception e) {
        logger.error("Failed to send notification: ${e.getMessage()}")
    }
}

// 2. DETERMINE OVERALL BUILD STATUS
def getOverallBuildStatus(Map stageResults = [:]) {
    logger.info("Determining overall build status from stage results: ${stageResults}")
    
    if (!stageResults || stageResults.isEmpty()) {
        return 'SUCCESS'
    }
    
    def hasFailures = stageResults.values().any { it == 'FAILED' }
    def hasUnstable = stageResults.values().any { it == 'UNSTABLE' }
    
    if (hasFailures) {
        return 'FAILED'
    } else if (hasUnstable) {
        return 'UNSTABLE'
    } else {
        return 'SUCCESS'
    }
}

// 3. GET CURRENT BUILD STATUS (from Jenkins)
def getBuildStatus() {
    def status = currentBuild.result ?: 'SUCCESS'
    logger.info("Current Jenkins build status: ${status}")
    return status
}

// 4. SEND EMAIL NOTIFICATION
private def sendEmailNotification(Map notificationData, Map config) {
    logger.info("Sending email notification")
    
    def subject = "${notificationData.status}: ${notificationData.buildInfo.jobName} #${notificationData.buildInfo.buildNumber}"
    def body = generateEmailBody(notificationData)
    
    try {
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/html',
            to: config.notifications?.email?.recipients?.join(',') ?: 'team@company.com'
        )
        logger.info("Email notification sent successfully")
    } catch (Exception e) {
        logger.error("Failed to send email notification: ${e.getMessage()}")
    }
}

// 5. SEND SLACK NOTIFICATION (DISABLED)
def sendSlackNotification(Map notificationData, Map config) {
    logger.info("Slack notification is DISABLED")
    // TODO: Enable when Slack access is available
    // Implementation ready but commented out
}

// 6. HELPER METHODS

private def getBuildInfo() {
    return [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL',
        gitBranch: env.GIT_BRANCH ?: 'Unknown Branch'
    ]
}

private def logNotification(Map notificationData) {
    logger.info("=== BUILD NOTIFICATION ===")
    logger.info("Status: ${notificationData.status}")
    logger.info("Job: ${notificationData.buildInfo.jobName}")
    logger.info("Build: #${notificationData.buildInfo.buildNumber}")
    logger.info("Time: ${notificationData.timestamp}")
    logger.info("==========================")
}

private String generateEmailBody(Map notificationData) {
    def status = notificationData.status
    def buildInfo = notificationData.buildInfo
    def statusColor = getStatusHtmlColor(status)
    
    return """
    <html>
    <body style="font-family: Arial, sans-serif;">
        <h2 style="color: ${statusColor};">Build ${status}</h2>
        <table border="1" style="border-collapse: collapse; margin: 10px 0; width: 100%;">
            <tr><td><strong>Job:</strong></td><td>${buildInfo.jobName}</td></tr>
            <tr><td><strong>Build:</strong></td><td>#${buildInfo.buildNumber}</td></tr>
            <tr><td><strong>Status:</strong></td><td><strong style="color: ${statusColor};">${status}</strong></td></tr>
            <tr><td><strong>Time:</strong></td><td>${notificationData.timestamp}</td></tr>
            <tr><td><strong>Branch:</strong></td><td>${buildInfo.gitBranch}</td></tr>
            <tr><td><strong>Build URL:</strong></td><td><a href="${buildInfo.buildUrl}">View Build</a></td></tr>
        </table>
        
        <p><strong>Message:</strong> ${getStatusMessage(status)}</p>
    </body>
    </html>
    """
}

private String getStatusHtmlColor(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS': return 'green'
        case 'FAILED':
        case 'FAILURE': return 'red'
        case 'UNSTABLE': return 'orange'
        default: return 'blue'
    }
}

private String getStatusMessage(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS':
            return 'All stages completed successfully!'
        case 'FAILED':
        case 'FAILURE':
            return 'Build failed. Please check the logs and fix the issues.'
        case 'UNSTABLE':
            return 'Build completed with warnings. Some tests may have failed.'
        case 'ABORTED':
            return 'Build was manually aborted or timed out.'
        default:
            return 'Build completed with unknown status.'
    }
}

return this