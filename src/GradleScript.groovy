class GradleScript {
    static String buildCommand() {
        return "./gradlew build"
    }
    
    static String testCommand(String testTool = 'junit') { // doubt in this here how to give dynamically??? I have give static value for now!!
        return "./gradlew test" 
    }
    
    static String lintCommand(String lintTool = 'checkstyle') { // doubt in this here how to give dynamically??? I have give static value for now!!
        def command = "./gradlew ${lintTool}Main"
        if (lintTool != 'checkstyle' && lintTool != 'spotbugs') {
            throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
        return command
    }
    
    static String installDependenciesCommand() {
        return "./gradlew dependencies"
    }
}