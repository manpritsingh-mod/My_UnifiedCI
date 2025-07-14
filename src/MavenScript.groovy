class MavenScript {
    static String buildCommand() {
        return "mvn clean install -B"
    }
    
    static String testCommand(String testTool = 'junit') {
        // Now testTool is actually USED in the command
        switch(testTool) {
            case 'junit':
                return "mvn test -B"
            case 'testng':
                return "mvn test -B -Dtest.framework=testng"
            case 'surefire':
                return "mvn surefire:test -B"
            default:
                return "mvn test -B -Dtest.tool=${testTool}"
        }
    }
    
    static String lintCommand(String lintTool = 'checkstyle') { // doubt in this here how to give dynamically??? I have give static value for now!!
        switch(lintTool) {
            case 'checkstyle': return "mvn checkstyle:check -B"
            case 'spotbugs': return "mvn spotbugs:check -B"
            default: throw new IllegalArgumentException("Unknown lint tool: ${lintTool}")
        }
    }
    
    static String installDependenciesCommand() {
        return "mvn dependency:resolve -B"
    }
}


