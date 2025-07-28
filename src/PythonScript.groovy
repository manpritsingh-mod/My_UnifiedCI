/**
 * Python Script Generator - Creates Python commands for different pipeline operations
 * Uses environment variables (PYTHON_CMD, PIP_CMD) for flexible Python installation support
 */
class PythonScript {
    
    /**
     * Generates Python build command using setup.py
     * @return String Python command for building the project
     * Usage: def cmd = PythonScript.buildCommand() // Returns "python setup.py build"
     */
    static String buildCommand() {
        def pythonCmd = System.getenv('PYTHON_CMD') ?: 'python'
        return "${pythonCmd} setup.py build"
    }

    /**
     * Generates pip install command for project dependencies
     * @return String Pip command for installing requirements.txt
     * Usage: def cmd = PythonScript.installDependenciesCommand() // Returns "pip install -r requirements.txt"
     */
    static String installDependenciesCommand() {
        def pipCmd = System.getenv('PIP_CMD') ?: 'pip'
        return "${pipCmd} install -r requirements.txt"
    }
    
    /**
     * Generates Python test command based on test framework
     * @param testTool Test framework to use ('pytest' or 'unittest')
     * @return String Python command for running tests with XML output
     * Usage: def cmd = PythonScript.testCommand('pytest') // Returns "python -m pytest --verbose --tb=short --junit-xml=test-results.xml"
     */
    static String testCommand(String testTool = 'pytest') {
        def pythonCmd = System.getenv('PYTHON_CMD') ?: 'python'
        switch(testTool) {
            case 'pytest': 
                return "${pythonCmd} -m pytest --verbose --tb=short --junit-xml=test-results.xml"
            case 'unittest': 
                return "${pythonCmd} -m unittest discover -v"
            default: 
                throw new IllegalArgumentException("Unknown test tool: ${testTool}. Supported: pytest, unittest")
        }
    }
    
    /**
     * Generates Python lint command based on linting tool
     * @param lintTool Lint tool to use ('pylint', 'flake8', or 'black')
     * @return String Python command for code quality checks with report output
     * Usage: def cmd = PythonScript.lintCommand('pylint') // Returns "python -m pylint src/**/*.py --output-format=text > pylint-report.txt 2>&1"
     */
    static String lintCommand(String lintTool = 'pylint') {
        def pythonCmd = System.getenv('PYTHON_CMD') ?: 'python'
        switch(lintTool) {
            case 'pylint': 
                return "${pythonCmd} -m pylint src/**/*.py --output-format=text > pylint-report.txt 2>&1"
            case 'flake8': 
                return "${pythonCmd} -m flake8 src/ > flake8-report.txt 2>&1"
            case 'black': 
                return "${pythonCmd} -m black --check src/"
            default: 
                throw new IllegalArgumentException("Unknown lint tool: ${lintTool}. Supported: pylint, flake8, black")
        }
    }
}