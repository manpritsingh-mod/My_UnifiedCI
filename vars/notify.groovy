/**
 * Notification utilities for Jenkins pipeline
 * Handles various notification types: email, slack, teams, etc.
 */

def notifyBuildStatus(String status, Map config = [:]) {
    echo "Sending build notification: ${status}"
    
    try {
        def buildInfo = getBuildInfo()
        def notificationData = [
            status: status,
            buildInfo: buildInfo,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            config: config
        ]
        
        // Send notifications based on configured channels
        if (config.notifications?.email?.enabled) {
            sendEmailNotification(notificationData)
        }
        
        if (config.notifications?.slack?.enabled) {
            sendSlackNotification(notificationData)
        }
        
        if (config.notifications?.teams?.enabled) {
            sendTeamsNotification(notificationData)
        }
        
        // Always log to console
        logNotification(notificationData)
        
    } catch (Exception e) {
        echo "Failed to send notification: ${e.getMessage()}"
    }
}

def notifyStageStatus(String stage, String status, Map stageResults = [:], Map config = [:]) {
    echo "Sending stage notification: ${stage} - ${status}"
    
    try {
        def stageInfo = [
            stage: stage,
            status: status,
            results: stageResults,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            buildNumber: env.BUILD_NUMBER,
            jobName: env.JOB_NAME
        ]
        
        // Send stage-specific notifications
        if (config.notifications?.stageUpdates?.enabled) {
            sendStageUpdateNotification(stageInfo, config)
        }
        
    } catch (Exception e) {
        echo "Failed to send stage notification: ${e.getMessage()}"
    }
}

private def getBuildInfo() {
    return [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL',
        gitCommit: env.GIT_COMMIT ?: 'Unknown Commit',
        gitBranch: env.GIT_BRANCH ?: 'Unknown Branch',
        jenkinsUrl: env.JENKINS_URL ?: 'Unknown Jenkins URL',
        node: env.NODE_NAME ?: 'Unknown Node'
    ]
}

private def sendEmailNotification(Map notificationData) {
    def subject = "Build ${notificationData.status}: ${notificationData.buildInfo.jobName} #${notificationData.buildInfo.buildNumber}"
    def body = generateEmailBody(notificationData)
    
    try {
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/html',
            to: notificationData.config.notifications.email.recipients ?: 'default@example.com'
        )
        echo "Email notification sent successfully"
    } catch (Exception e) {
        echo "Failed to send email notification: ${e.getMessage()}"
    }
}

private def sendSlackNotification(Map notificationData) {
    def color = getStatusColor(notificationData.status)
    def message = generateSlackMessage(notificationData)
    
    try {
        slackSend (
            channel: notificationData.config.notifications.slack.channel ?: '#builds',
            color: color,
            message: message
        )
        echo "Slack notification sent successfully"
    } catch (Exception e) {
        echo "Failed to send Slack notification: ${e.getMessage()}"
    }
}

private def sendTeamsNotification(Map notificationData) {
    def message = generateTeamsMessage(notificationData)
    
    try {
        // office365ConnectorSend or custom Teams webhook implementation
        echo "Teams notification would be sent: ${message}"
    } catch (Exception e) {
        echo "Failed to send Teams notification: ${e.getMessage()}"
    }
}

private def sendStageUpdateNotification(Map stageInfo, Map config) {
    def message = "Stage ${stageInfo.stage} completed with status: ${stageInfo.status}"
    
    if (stageInfo.results) {
        message += "\nResults: ${stageInfo.results}"
    }
    
    echo message
}

private def logNotification(Map notificationData) {
    echo "=== BUILD NOTIFICATION ==="
    echo "Status: ${notificationData.status}"
    echo "Job: ${notificationData.buildInfo.jobName}"
    echo "Build: #${notificationData.buildInfo.buildNumber}"
    echo "Time: ${notificationData.timestamp}"
    echo "URL: ${notificationData.buildInfo.buildUrl}"
    echo "============================="
}

private String generateEmailBody(Map notificationData) {
    return """
    <html>
    <body>
        <h2>Build ${notificationData.status}</h2>
        <table border="1" style="border-collapse: collapse;">
            <tr><td><strong>Job Name:</strong></td><td>${notificationData.buildInfo.jobName}</td></tr>
            <tr><td><strong>Build Number:</strong></td><td>${notificationData.buildInfo.buildNumber}</td></tr>
            <tr><td><strong>Status:</strong></td><td>${notificationData.status}</td></tr>
            <tr><td><strong>Timestamp:</strong></td><td>${notificationData.timestamp}</td></tr>
            <tr><td><strong>Build URL:</strong></td><td><a href="${notificationData.buildInfo.buildUrl}">${notificationData.buildInfo.buildUrl}</a></td></tr>
            <tr><td><strong>Git Branch:</strong></td><td>${notificationData.buildInfo.gitBranch}</td></tr>
            <tr><td><strong>Git Commit:</strong></td><td>${notificationData.buildInfo.gitCommit}</td></tr>
        </table>
    </body>
    </html>
    """
}

private String generateSlackMessage(Map notificationData) {
    return """
    :jenkins: *Build ${notificationData.status}*
    
    *Job:* ${notificationData.buildInfo.jobName}
    *Build:* #${notificationData.buildInfo.buildNumber}
    *Branch:* ${notificationData.buildInfo.gitBranch}
    *Time:* ${notificationData.timestamp}
    
    <${notificationData.buildInfo.buildUrl}|View Build>
    """
}

private String generateTeamsMessage(Map notificationData) {
    return """
    **Build ${notificationData.status}**
    
    - **Job:** ${notificationData.buildInfo.jobName}
    - **Build:** #${notificationData.buildInfo.buildNumber}
    - **Branch:** ${notificationData.buildInfo.gitBranch}
    - **Time:** ${notificationData.timestamp}
    
    [View Build](${notificationData.buildInfo.buildUrl})
    """
}

private String getStatusColor(String status) {
    switch(status.toLowerCase()) {
        case 'success':
            return 'good'
        case 'failure':
        case 'failed':
            return 'danger'
        case 'unstable':
            return 'warning'
        default:
            return '#439FE0'
    }
}

def notifyOnFailure(String stage, Exception error, Map config = [:]) {
    echo "Sending failure notification for stage: ${stage}"
    
    def failureData = [
        stage: stage,
        error: error.getMessage(),
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        buildInfo: getBuildInfo()
    ]
    
    // Send immediate failure notification
    if (config.notifications?.immediateFailure?.enabled) {
        sendFailureNotification(failureData, config)
    }
}

private def sendFailureNotification(Map failureData, Map config) {
    def subject = "❌ Build Failed: ${failureData.stage} - ${failureData.buildInfo.jobName}"
    def message = """
    Build failed in stage: ${failureData.stage}
    
    Error: ${failureData.error}
    Job: ${failureData.buildInfo.jobName}
    Build: #${failureData.buildInfo.buildNumber}
    Time: ${failureData.timestamp}
    
    Build URL: ${failureData.buildInfo.buildUrl}
    """
    
    echo message
    
    // Send to configured channels
    if (config.notifications?.email?.enabled) {
        try {
            emailext (
                subject: subject,
                body: message,
                to: config.notifications.email.recipients ?: 'default@example.com'
            )
        } catch (Exception e) {
            echo "Failed to send failure email: ${e.getMessage()}"
        }
    }
}

return this
