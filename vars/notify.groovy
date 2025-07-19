/**
 * Basic Notification utilities for Jenkins pipeline
 * Handles SUCCESS, FAILURE, and UNSTABLE build notifications
 */

def notifyBuildStatus(String status, Map config = [:]) {
    logger.info("Sending build notification: ${status}")
    
    try {
        def buildInfo = getBuildInfo()
        def notificationData = [
            status: status,
            buildInfo: buildInfo,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
        ]
        
        // Send email notification (Slack disabled)
        sendEmailNotification(notificationData, config)
        // sendSlackNotification(notificationData, config)  // DISABLED
        
        // Always log to console
        logNotification(notificationData)
        
    } catch (Exception e) {
        logger.error("Failed to send notification: ${e.getMessage()}")
    }
}

// Determine overall build status based on stage results
def determineBuildStatus(Map stageResults) {
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

private def getBuildInfo() {
    return [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL',
        gitBranch: env.GIT_BRANCH ?: 'Unknown Branch',
        node: env.NODE_NAME ?: 'Unknown Node'
    ]
}

private def sendEmailNotification(Map notificationData, Map config) {
    def subject = getEmailSubject(notificationData.status, notificationData.buildInfo)
    def body = getEmailBody(notificationData)
    
    try {
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/html',
            to: config.notifications.email.recipients?.join(',') ?: 'team@company.com'
        )
        logger.info("Email notification sent successfully")
    } catch (Exception e) {
        logger.error("Failed to send email notification: ${e.getMessage()}")
    }
}

private def sendSlackNotification(Map notificationData, Map config) {
    // SLACK NOTIFICATIONS CURRENTLY DISABLED
    def color = getStatusColor(notificationData.status)
    def message = getSlackMessage(notificationData)
    
    try {
        slackSend (
            channel: config.notifications.slack.channel ?: '#builds',
            color: color,
            message: message
        )
        logger.info("Slack notification sent successfully")
    } catch (Exception e) {
        logger.error("Failed to send Slack notification: ${e.getMessage()}")
    }
}



private def logNotification(Map notificationData) {
    logger.info("=== BUILD NOTIFICATION ===")
    logger.info("Status: ${notificationData.status}")
    logger.info("Job: ${notificationData.buildInfo.jobName}")
    logger.info("Build: #${notificationData.buildInfo.buildNumber}")
    logger.info("Time: ${notificationData.timestamp}")
    logger.info("URL: ${notificationData.buildInfo.buildUrl}")
    logger.info("==========================")
}

// Message generators for different statuses
private String getEmailSubject(String status, Map buildInfo) {
    def emoji = getStatusEmoji(status)
    return "${emoji} Build ${status}: ${buildInfo.jobName} #${buildInfo.buildNumber}"
}

private String getEmailBody(Map notificationData) {
    def status = notificationData.status
    def buildInfo = notificationData.buildInfo
    
    return """
    <html>
    <body>
        <h2>${getStatusEmoji(status)} Build ${status}</h2>
        <table border="1" style="border-collapse: collapse; margin: 10px 0;">
            <tr><td><strong>Job Name:</strong></td><td>${buildInfo.jobName}</td></tr>
            <tr><td><strong>Build Number:</strong></td><td>#${buildInfo.buildNumber}</td></tr>
            <tr><td><strong>Status:</strong></td><td><span style="color: ${getStatusHtmlColor(status)}"><strong>${status}</strong></span></td></tr>
            <tr><td><strong>Time:</strong></td><td>${notificationData.timestamp}</td></tr>
            <tr><td><strong>Branch:</strong></td><td>${buildInfo.gitBranch}</td></tr>
            <tr><td><strong>Build URL:</strong></td><td><a href="${buildInfo.buildUrl}">View Build</a></td></tr>
        </table>
        
        ${getStatusMessage(status)}
    </body>
    </html>
    """
}

private String getSlackMessage(Map notificationData) {
    def status = notificationData.status
    def buildInfo = notificationData.buildInfo
    def emoji = getStatusEmoji(status)
    
    return """${emoji} *Build ${status}*

*Job:* ${buildInfo.jobName}
*Build:* #${buildInfo.buildNumber}
*Branch:* ${buildInfo.gitBranch}
*Time:* ${notificationData.timestamp}

<${buildInfo.buildUrl}|View Build>

${getStatusMessage(status)}"""
}



// Status-specific helpers
private String getStatusEmoji(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS':
            return 'Success'
        case 'FAILED':
        case 'FAILURE':
            return 'Failure'
        case 'UNSTABLE':
            return 'Unstable'
        case 'ABORTED':
            return 'Aborted'
        default:
            return 'i'
    }
}

private String getStatusColor(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS':
            return 'good'
        case 'FAILED':
        case 'FAILURE':
            return 'danger'
        case 'UNSTABLE':
            return 'warning'
        default:
            return '#439FE0'
    }
}

private String getStatusHtmlColor(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS':
            return 'green'
        case 'FAILED':
        case 'FAILURE':
            return 'red'
        case 'UNSTABLE':
            return 'orange'
        default:
            return 'blue'
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
            return 'Build completed with warnings. Some tests may have failed or quality gates not met.'
        case 'ABORTED':
            return 'Build was manually aborted or timed out.'
        default:
            return 'Build completed with unknown status.'
    }
}

return this