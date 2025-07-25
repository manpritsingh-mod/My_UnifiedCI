/**
 * SIMPLIFIED Notification utilities for Jenkins pipeline
 * Email: Always send (default)
 * Slack: Only if enabled in config
 */

// 1. MAIN METHOD: Send build notification
def notifyBuildStatus(String status, Map config = [:]) {
    logger.info("Sending build notification: ${status}")
    
    try {
        def buildInfo = getBuildInfo()
        def notificationData = [
            status: status,
            buildInfo: buildInfo,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
        ]
        
        // ALWAYS send email notification (default behavior)
        sendEmailNotification(notificationData, config)
        
        // CONDITIONALLY send Slack notification (only if enabled)
        if (config.notifications?.slack?.enabled == true) {
            logger.info("Slack is enabled - sending Slack notification")
            sendSlackNotification(notificationData, config)
        } else {
            logger.info("Slack is disabled - skipping Slack notification")
        }
        
        // Log to console
        logNotification(notificationData)
        
    } catch (Exception e) {
        logger.error("Failed to send notification: ${e.getMessage()}")
    }
}



// 2. GET CURRENT BUILD STATUS (from Jenkins)
def getBuildStatus() {
    def status = currentBuild.result ?: 'SUCCESS'
    logger.info("Current Jenkins build status: ${status}")
    return status
}

// 3. SEND EMAIL NOTIFICATION (ALWAYS)
private def sendEmailNotification(Map notificationData, Map config) {
    logger.info("Sending email notification (always enabled)")
    
    def subject = "${notificationData.status}: ${notificationData.buildInfo.jobName} #${notificationData.buildInfo.buildNumber}"
    def body = generateEmailBody(notificationData)
    
    try {
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/plain',
            to: config.notifications?.email?.recipients?.join(',') ?: 'smanprit022@gmail.com'
        )
        logger.info("Email notification sent successfully")
    } catch (Exception e) {
        logger.error("Failed to send email notification: ${e.getMessage()}")
    }
}

// 4. SEND SLACK NOTIFICATION (CONDITIONAL - COMMENTED FOR NOW)
def sendSlackNotification(Map notificationData, Map config) {
    logger.info("Preparing Slack notification...")
    
    /* SLACK IMPLEMENTATION - COMMENTED UNTIL ACCESS IS AVAILABLE
    
    def slackChannel = config.notifications?.slack?.channel ?: '#builds'
    def message = generateSlackMessage(notificationData)
    
    try {
        // Using Jenkins Slack Plugin (simple setup)
        slackSend (
            channel: slackChannel,
            message: message
        )
        
        logger.info("Slack notification sent successfully")
        
    } catch (Exception e) {
        logger.error("Failed to send Slack notification: ${e.getMessage()}")
    }
    
    END OF COMMENTED SLACK IMPLEMENTATION */
    
    // For now, just log that Slack would be sent
    logger.info("Slack notification would be sent to: ${config.notifications?.slack?.channel ?: '#builds'}")
    logger.info("Slack message: ${generateSlackMessage(notificationData)}")
}

// 5. HELPER METHODS

def getBuildInfo() {
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
    
    return """
        BUILD NOTIFICATION - ${status}

        Job Name:     ${buildInfo.jobName}
        Build #:      ${buildInfo.buildNumber}
        Status:       ${status}
        Timestamp:    ${notificationData.timestamp}
        Branch:       ${buildInfo.gitBranch}
        Build URL:    ${buildInfo.buildUrl}

        Message: ${getStatusMessage(status)}

        ---
        This notification was sent automatically by Jenkins CI/CD pipeline.
    """
}

private String generateSlackMessage(Map notificationData) {
    def status = notificationData.status
    def buildInfo = notificationData.buildInfo
    
    return """
        *Build ${status}*

        *Job:* ${buildInfo.jobName}
        *Build:* #${buildInfo.buildNumber}
        *Branch:* ${buildInfo.gitBranch}
        *Time:* ${notificationData.timestamp}

        <${buildInfo.buildUrl}|View Build>

        ${getStatusMessage(status)}
        """
}

def getStatusMessage(String status) {
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