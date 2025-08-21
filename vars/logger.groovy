/**
 * Logger for Jenkins pipeline
 * Provides consistent logging across all pipeline stages
 */

/**
 * Custom Step `logMessage.info`
 * @param message Log message
 */
def info(String message) {
    logMessage('INFO', message)
}

/**
 * Custom Step `logMessage.warning`
 * @param message Log message
 */
def warning(String message) {
    logMessage('WARNING', message)
}

/**
 * Custom Step `logMessage.error`
 * @param message Log message
 */
def error(String message) {
    logMessage('ERROR', message)
}

/**
 * Custom Step `logMessage.debug`
 * @param message Log message
 */
def debug(String message) {
    logMessage('DEBUG', message)
}

/**
 * Internal method to format and output log messages with timestamp
 * @param level Log level (INFO, WARNING, ERROR, DEBUG)
 * @param message Message to log
 */
private def logMessage(String level, String message) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    echo "[${level}] [${timestamp}] ${message}"
}

def call(String level, String message) {
    logMessage(level.toUpperCase(), message)
}

return this