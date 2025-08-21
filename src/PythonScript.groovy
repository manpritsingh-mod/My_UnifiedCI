/*
* Python Script - Creates Python commands for different pipeline operations
* Provides standardized Python commands with batch mode (-B)
*/
class PythonScript {
    static String buildCommand() {
        return "python setup.py build"
    }

    /*
    * @return String Python command for resolving and download dependencies
    */
    static String installDependenciesCommand() {
        return "python3 -m pip install -r requirements.txt"
    }

    /*
    * Created a virtual env & Generate Python test commad based on test framework
    * @param testTool Test framework to use ('pytest', 'unittest')
    * return String Python command for running tests
    */
    static String venvTestLinuxCommand(String testTool = 'pytest', String venvName = 'venv') {
        switch(testTool) {
            case 'pytest': 
                return "${venvName}/bin/pytest --verbose --tb=short"
            case 'unittest': 
                return "${venvName}/bin/python -m unittest discover -v"
            default: 
                throw new IllegalArgumentException("Unknown test tool: $testTool")
        }
    }
    
    /**
    * Creare Virtual env & Generate Python lint/code quality commad
    * @param lintTool Lint tool is use ('pylint', 'flake8', 'black')
    * @param venvName Virtual env ('venv')
    * @return String Python commad for code quality checks
    * Usage: def cmd = PythonScript.lintCommand('pylint')
    */
    static String venvLintLinuxCommand(String lintTool = 'pylint', String venvName = 'venv') {
        switch(lintTool) {
            case 'pylint': 
                return "${venvName}/bin/pylint **/*.py --output-format=text"
            case 'flake8': 
                return "${venvName}/bin/flake8 ."
            case 'black': 
                return "${venvName}/bin/black --check ."
            default: 
                throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
    
    // Python Version check commands
    static String pythonVersionCommand() {
        return "python --version"
    }
    
    // pip version check commads
    static String pipVersionCommand() {
        return "pip --version"
    }

    // Virtual environment commands
    static String createVirtualEnvCommand(String venvName = 'venv') {
        return "python3 -m venv ${venvName}"
         
    }
    
    // Activation of the Virtual Env 
    static String activateVirtualEnvLinuxCommand(String venvName = 'venv') {
        return "source ${venvName}/bin/activate"
    }

    static String venvPipInstallLinuxCommand(String venvName = 'venv') {
        // Install dependencies in virtual environment
        return "${venvName}/bin/pip install -r requirements.txt"
    }
    
    static String deactivateVirtualEnvLinuxCommand() {
        // Deactivate virtual environment
        return "deactivate"
    }

    static String removeVirtualEnvLinuxCommand(String venvName = 'venv') {
        // Remove virtual environment directory
        return "rm -rf ${venvName}"
    }

    static String cleanupVirtualEnvLinuxCommand(String venvName = 'venv') {
        // Complete cleanup command
        return "[ -d ${venvName} ] && rm -rf ${venvName} || echo 'Virtual environment not found'"
    }

    // Commands for checking Functional Test for (Smoke, Sanity, Regression)
    static String venvSmokeTestLinuxCommand(String venvName = 'venv') {
        return "${venvName}/bin/pytest tests/smoke/ -v --tb=short"
    }
    
    static String venvSanityTestLinuxCommand(String venvName = 'venv') {
        return "${venvName}/bin/pytest tests/sanity/ -v --tb=short"
    }
    
    static String venvRegressionTestLinuxCommand(String venvName = 'venv') {
        return "${venvName}/bin/pytest tests/regression/ -v --tb=short"
    }
}