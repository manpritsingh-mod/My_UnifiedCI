/**
 * Maven Script Generator - Creates Maven commands for different pipeline operations
 * Provides standardized Maven commands with batch mode (-B) for CI/CD environments
 */
class MavenScript {
    
    /**
     * Generates Maven build command (clean, compile, package)
     * @return String Maven command for building the project
     * Usage: def cmd = MavenScript.buildCommand() // Returns "mvn clean install -B"
     */
    static String buildCommand() {
        return "mvn clean install -B"
    }
    
    /**
     * Generates Maven test command based on test framework
     * @param testTool Test framework to use ('junit', 'testng', 'surefire')
     * @return String Maven command for running tests
     * Usage: def cmd = MavenScript.testCommand('junit') // Returns "mvn test -B"
     */
    static String testCommand(String testTool = 'junit') {
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
    
    /**
     * Generates Maven lint/code quality command
     * @param lintTool Lint tool to use ('checkstyle', 'spotbugs')
     * @return String Maven command for code quality checks
     * Usage: def cmd = MavenScript.lintCommand('checkstyle') // Returns "mvn checkstyle:check -B"
     */
    static String lintCommand(String lintTool = 'checkstyle') {
        switch(lintTool) {
            case 'checkstyle': return "mvn checkstyle:check -B"
            case 'spotbugs': return "mvn spotbugs:check -B"
            default: throw new IllegalArgumentException("Unknown lint tool: ${lintTool}. Supported: checkstyle, spotbugs")
        }
    }
    
    /**
     * Generates Maven dependency installation command
     * @return String Maven command for resolving and downloading dependencies
     * Usage: def cmd = MavenScript.installDependenciesCommand() // Returns "mvn dependency:resolve -B"
     */
    static String installDependenciesCommand() {
        return "mvn dependency:resolve -B"
    }
}


