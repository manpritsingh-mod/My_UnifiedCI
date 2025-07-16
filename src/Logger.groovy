class Logger implements Serializable {
    private static Logger instance
    private def script

    private Logger(def script) {
        this.script = script
    }

    static void init(def script) {
        instance = new Logger(script)
    }

    static void info(String msg) {
        instance?.log("INFO", msg)
    }

    static void warning(String msg) {
        instance?.log("WARNING", msg)
    }

    static void error(String msg) {
        instance?.log("ERROR", msg)
    }

    private void log(String level, String msg) {
        def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
        script.echo "[${level}] [${timestamp}] ${msg}"
    }
}
