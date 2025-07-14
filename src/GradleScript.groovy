class GradleScript {
    static String buildCommand() {
        return "./gradlew build"
    }
    
    static String testCommand(String testTool = 'junit') { // doubt in this here how to give dynamically??? I have give static value for now!!
        return "./gradlew test" 
    }
    
    static String lintCommand(String lintTool = 'checkstyle') { // doubt in this here how to give dynamically??? I have give static value for now!!
        def command = "./gradlew ${tool}Main"
        if (tool != 'checkstyle' && tool != 'spotbugs') {
            throw new IllegalArgumentException("Unknown lint tool: $tool")
        }
        return command
    }
    
    static String installDependenciesCommand() {
        return "./gradlew dependencies"
    }
}
