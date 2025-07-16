class Logger {
    static void info(String message) {
        logMessage('INFO', message)
    }
    static void warning(String message) {
        logMessage('WARNING', message)
    }
    static void error(String message) {
        logMessage('ERROR', message)
    }
    private static void logMessage(String level, String message) {
        def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
        println "[${level}] [${timestamp}] ${message}"
    }
}
