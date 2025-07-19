/**
 * Logger utility for Jenkins pipeline
 * Provides consistent logging across all pipeline stages
 */

def info(String message) {
    logMessage('INFO', message)
}

def warning(String message) {
    logMessage('WARNING', message)
}

def error(String message) {
    logMessage('ERROR', message)
}

def debug(String message) {
    logMessage('DEBUG', message)
}

private def logMessage(String level, String message) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    echo "[${level}] [${timestamp}] ${message}"
}

// Allow this to be called as a step
def call(String level, String message) {
    logMessage(level.toUpperCase(), message)
}

return this