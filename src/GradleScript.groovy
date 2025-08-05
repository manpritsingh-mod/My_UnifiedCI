/**
 * Gradle Script Generator - Creates Gradle commands for different pipeline operations
 * Uses Gradle wrapper (./gradlew) for consistent build environment across systems
 */
class GradleScript {
    
    /**
     * Generates Gradle build command (compile, test, package)
     * @return String Gradle command for building the project
     * Usage: def cmd = GradleScript.buildCommand() // Returns "./gradlew build"
     */
    static String buildCommand() {
        return "./gradlew build"
    }
    
    /**
     * Generates Gradle test command based on test framework
     * @param testTool Test framework to use ('junit', 'junit5', 'testng', 'spock')
     * @return String Gradle command for running tests
     * Usage: def cmd = GradleScript.testCommand('junit5') // Returns "./gradlew test"
     */
    static String testCommand(String testTool = 'junit') {
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
    
    /**
     * Generates Gradle lint/code quality command
     * @param lintTool Lint tool to use ('checkstyle', 'spotbugs')
     * @return String Gradle command for code quality checks
     * Usage: def cmd = GradleScript.lintCommand('checkstyle') // Returns "./gradlew checkstyleMain"
     */
    static String lintCommand(String lintTool = 'checkstyle') {
        switch(lintTool) {
            case 'checkstyle': return "./gradlew checkstyleMain"
            case 'spotbugs': return "./gradlew spotbugsMain"
            default: throw new IllegalArgumentException("Unknown lint tool: ${lintTool}. Supported: checkstyle, spotbugs")
        }
    }
    
    /**
     * Generates Gradle dependency resolution command
     * @return String Gradle command for resolving and downloading dependencies
     * Usage: def cmd = GradleScript.installDependenciesCommand() // Returns "./gradlew dependencies"
     */
    static String installDependenciesCommand() {
        return "./gradlew dependencies"
    }
    
    // Version check commands
    static String javaVersionCommand() {
        return "java -version"
    }
    
    static String gradleVersionCommand() {
        return "./gradlew --version"
    }
    
    // Functional test commands with Gradle profiles
    static String smokeTestCommand() {
        return "./gradlew test -Psmoke"
    }
    
    static String sanityTestCommand() {
        return "./gradlew test -Psanity"
    }
    
    static String regressionTestCommand() {
        return "./gradlew test -Pregression"
    }
}
