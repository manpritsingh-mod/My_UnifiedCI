class PythonScript {
    static String buildCommand() {
        return "python setup.py build"
    }

    static String installDependenciesCommand() {
        return "pip install -r requirements.txt"
    }
    
    static String testCommand(String testTool = 'pytest') {
        switch(testTool) {
            case 'pytest': return "pytest --verbose --tb=short"
            case 'unittest': return "python -m unittest discover -v"
            default: throw new IllegalArgumentException("Unknown test tool: $testTool")
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
}