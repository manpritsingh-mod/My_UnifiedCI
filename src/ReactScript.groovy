/**
 * React Script - Creates React/Node.js commands for different pipeline operations
 * Provides standardized React commands for npm/yarn package managers
 */
class ReactScript {
    
    /**
     * Generates React build command (npm run build)
     * @return String React command for building the project
     * Usage: def cmd = ReactScript.buildCommand() // returns "npm run build"
     */
    static String buildCommand() {
        return 'npm run build'
    }
    
    /**
     * Generates React test command based on test framework
     * @param testTool Test framework to use ('jest', 'cypress')
     * @return String React command for running tests
     * Usage: def cmd = ReactScript.testCommand('jest') // returns "npm test"
     */
    static String testCommand(String testTool = 'jest') {
        switch(testTool) {
            case 'jest':
                return "npm test -- --coverage --watchAll=false"
            case 'cypress':
                return "npm run test:e2e"
            default:
                return "npm test -- --watchAll=false"
        }
    }
    
    /**
     * Generates React lint/code quality command
     * @param lintTool Lint tool to use ('eslint', 'prettier')
     * @return String React command for code quality checks
     * Usage: def cmd = ReactScript.lintCommand('eslint') // returns "npm run lint"
     */
    static String lintCommand(String lintTool = 'eslint') {
        switch(lintTool) {
            case 'eslint': return "npm run lint"
            case 'prettier': return "npm run format:check"
            default: throw new IllegalArgumentException("Unknown lint tool: ${lintTool}. Supported: eslint, prettier")
        }
    }
    
    /**
     * @return String React command for installing dependencies
     * Usage: def cmd = ReactScript.installDependenciesCommand() // returns "npm ci"
     */
    static String installDependenciesCommand() {
        return 'npm ci'
    }
    
    // Node.js Version check commands
    static String nodeVersionCommand() {
        return "node --version && npm --version"
    }
    
    // Functional test commands (Smoke, Sanity, Regression)
    static String smokeTestCommand() {
        return "npm run test:smoke"
    }
    
    static String sanityTestCommand() {
        return "npm run test:sanity"
    }
    
    static String regressionTestCommand() {
        return "npm run test:regression"
    }
}