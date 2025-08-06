class PythonScript {
    static String buildCommand() {
        return "python setup.py build"
    }

    static String installDependenciesCommand() {
        return "pip install -r requirements.txt"
    }
    
    // Virtual environment versions of commands
    static String venvTestCommand(String testTool = 'pytest', String venvName = 'venv') {
        switch(testTool) {
            case 'pytest': return "${venvName}\\Scripts\\pytest --verbose --tb=short"
            case 'unittest': return "${venvName}\\Scripts\\python -m unittest discover -v"
            default: throw new IllegalArgumentException("Unknown test tool: $testTool")
        }
    }
    
    static String venvTestLinuxCommand(String testTool = 'pytest', String venvName = 'venv') {
        switch(testTool) {
            case 'pytest': return "${venvName}/bin/pytest --verbose --tb=short"
            case 'unittest': return "${venvName}/bin/python -m unittest discover -v"
            default: throw new IllegalArgumentException("Unknown test tool: $testTool")
        }
    }
    
    static String testCommand(String testTool = 'pytest') {
        switch(testTool) {
            case 'pytest': return "pytest --verbose --tb=short"
            case 'unittest': return "python -m unittest discover -v"
            default: throw new IllegalArgumentException("Unknown test tool: $testTool")
        }
    }
    
    static String venvLintCommand(String lintTool = 'pylint', String venvName = 'venv') {
        switch(lintTool) {
            case 'pylint': return "${venvName}\\Scripts\\pylint **/*.py --output-format=text"
            case 'flake8': return "${venvName}\\Scripts\\flake8 ."
            case 'black': return "${venvName}\\Scripts\\black --check ."
            default: throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
    
    static String venvLintLinuxCommand(String lintTool = 'pylint', String venvName = 'venv') {
        switch(lintTool) {
            case 'pylint': return "${venvName}/bin/pylint **/*.py --output-format=text"
            case 'flake8': return "${venvName}/bin/flake8 ."
            case 'black': return "${venvName}/bin/black --check ."
            default: throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
    
    static String lintCommand(String lintTool = 'pylint') {
        switch(lintTool) {
            case 'pylint': return "pylint **/*.py --output-format=text"
            case 'flake8': return "flake8 ."
            case 'black': return "black --check ."
            default: throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
    
    // Version check commands
    static String pythonVersionCommand() {
        return "python --version"
    }
    
    static String pipVersionCommand() {
        return "pip --version"
    }
    
    // Virtual environment commands
    static String createVirtualEnvCommand(String venvName = 'venv') {
        return "python -m venv ${venvName}"
    }
    
    static String activateVirtualEnvCommand(String venvName = 'venv') {
        // Windows activation command
        return "${venvName}\\Scripts\\activate.bat"
    }
    
    static String activateVirtualEnvLinuxCommand(String venvName = 'venv') {
        // Linux activation command
        return "source ${venvName}/bin/activate"
    }
    
    static String venvPipInstallCommand(String venvName = 'venv') {
        // Install dependencies in virtual environment
        return "${venvName}\\Scripts\\pip install -r requirements.txt"
    }
    
    static String venvPipInstallLinuxCommand(String venvName = 'venv') {
        // Install dependencies in virtual environment (Linux)
        return "${venvName}/bin/pip install -r requirements.txt"
    }
    
    // Virtual environment cleanup commands
    static String deactivateVirtualEnvCommand() {
        // Deactivate virtual environment (Windows)
        return "deactivate"
    }
    
    static String deactivateVirtualEnvLinuxCommand() {
        // Deactivate virtual environment (Linux)
        return "deactivate"
    }
    
    static String removeVirtualEnvCommand(String venvName = 'venv') {
        // Remove virtual environment directory (Windows)
        return "rmdir /s /q ${venvName}"
    }
    
    static String removeVirtualEnvLinuxCommand(String venvName = 'venv') {
        // Remove virtual environment directory (Linux)
        return "rm -rf ${venvName}"
    }
    
    static String cleanupVirtualEnvCommand(String venvName = 'venv') {
        // Complete cleanup command for Windows
        return "if exist ${venvName} rmdir /s /q ${venvName}"
    }
    
    static String cleanupVirtualEnvLinuxCommand(String venvName = 'venv') {
        // Complete cleanup command for Linux
        return "[ -d ${venvName} ] && rm -rf ${venvName} || echo 'Virtual environment not found'"
    }
    
    // Functional test commands
    static String smokeTestCommand() {
        return "pytest tests/smoke/ -v --tb=short"
    }
    
    static String sanityTestCommand() {
        return "pytest tests/sanity/ -v --tb=short"
    }
    
    static String regressionTestCommand() {
        return "pytest tests/regression/ -v --tb=short"
    }
    
    // Virtual environment functional test commands
    static String venvSmokeTestCommand(String venvName = 'venv') {
        return "${venvName}\\Scripts\\pytest tests/smoke/ -v --tb=short"
    }
    
    static String venvSanityTestCommand(String venvName = 'venv') {
        return "${venvName}\\Scripts\\pytest tests/sanity/ -v --tb=short"
    }
    
    static String venvRegressionTestCommand(String venvName = 'venv') {
        return "${venvName}\\Scripts\\pytest tests/regression/ -v --tb=short"
    }
    
    // Linux versions
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