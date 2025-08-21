/**
 * Gradle Script - Creates Gradle commands for different pipeline operations
 * Uses Gradle wrapper (./gradlew) for consistent build environment across systems
 */
class GradleScript {
    
    /**
     * Generates Gradle build command (compile, test, package)
     * @return String Gradle command for building the project
     * Usage: def cmd = GradleScript.buildCommand() // returns "./gradlew build"
     */
    static String buildCommand() {
        return "gradle build"
    }
    
    /**
     * Generates Gradle test command based on test framework
     * @param testTool Test framework to use ('junit', 'junit5', 'spock')
     * @return String Gradle command for running tests
     * Usage: def cmd = GradleScript.testCommand('junit5') // returns "./gradlew test"
     */
    static String testCommand(String testTool = 'junit') {
        switch(testTool) {
            case 'junit':
            case 'junit5':
                return "gradle test"
            case 'spock':
                return "gradle test --tests '*Spec'"
            default:
                return "gradle test -Dtest.framework=${testTool}"
        }
    }
    
    /**
     * Generates Gradle lint/code quality command
     * @param lintTool Lint tool to use ('checkstyle', 'spotbugs')
     * @return String Gradle command for code quality checks
     * Usage: def cmd = GradleScript.lintCommand('checkstyle') // returns "./gradlew checkstyleMain"
     */
    static String lintCommand(String lintTool = 'checkstyle') {
        switch(lintTool) {
            case 'checkstyle': 
                return "gradle checkstyleMain"
            case 'spotbugs': 
                return "gradle spotbugsMain"
            default: 
                throw new IllegalArgumentException("Unknown lint tool: ${lintTool}. Supported: checkstyle, spotbugs")
        }
    }
    
    /**
     * Generates Gradle dependency resolution command
     * @return String Gradle command for resolving and downloading dependencies
     * Usage: def cmd = GradleScript.installDependenciesCommand() // returns "./gradlew dependencies"
     */
    static String installDependenciesCommand() {
        return "gradle dependencies"
    }
    
    // Java Version check commands
    static String javaVersionCommand() {
        return "java -version"
    }
    
    // Gradle Version check commands
    static String gradleVersionCommand() {
        return "gradle --version"
    }
    
    // Functional test commands for (Smoke, Sanity, Regression)
    static String smokeTestCommand() {
        return "gradle test -Psmoke"
    }
    
    static String sanityTestCommand() {
        return "gradle test -Psanity"
    }
    
    static String regressionTestCommand() {
        return "gradle test -Pregression"
    }
}