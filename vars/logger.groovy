/**
 * Logger utility for Jenkins pipeline
 * Provides consistent logging across all pipeline stages
 */

/**
 * Logs informational messages with timestamp
 * @param message Message to log
 * Usage: logger.info("Build started successfully")
 */
def info(String message) {
    logMessage('INFO', message)
}

/**
 * Logs warning messages with timestamp
 * @param message Warning message to log
 * Usage: logger.warning("Configuration file not found, using defaults")
 */
def warning(String message) {
    logMessage('WARNING', message)
}

/**
 * Logs error messages with timestamp
 * @param message Error message to log
 * Usage: logger.error("Build failed: ${e.getMessage()}")
 */
def error(String message) {
    logMessage('ERROR', message)
}

/**
 * Logs debug messages with timestamp
 * @param message Debug message to log
 * Usage: logger.debug("Processing file: ${fileName}")
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

/**
 * Alternative way to call logger with custom level
 * @param level Log level string
 * @param message Message to log
 * Usage: logger('CUSTOM', 'Custom message')
 */
def call(String level, String message) {
    logMessage(level.toUpperCase(), message)
}

return this