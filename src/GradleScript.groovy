class GradleScript {
    static String buildCommand() {
        return "./gradlew build"
    }
    
    static String testCommand(String testTool = 'junit') {
        // Now testTool is actually USED in the command
        switch(testTool) {
            case 'junit':
            case 'junit5':
                return "./gradlew test"
            case 'testng':
                return "./gradlew test --tests '*TestNG*'"
            case 'spock':
                return "./gradlew test --tests '*Spec'"
            default:
                return "./gradlew test -Dtest.framework=${testTool}"
        }
    }
    
    static String lintCommand(String lintTool = 'checkstyle') {
        switch(lintTool) {
            case 'checkstyle': return "./gradlew checkstyleMain"
            case 'spotbugs': return "./gradlew spotbugsMain"
            default: throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
    
    static String installDependenciesCommand() {
        return "./gradlew dependencies"
    }
}
