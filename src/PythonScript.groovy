class PythonScript {
    static String buildCommand() {
        return "python setup.py build"
    }

    static String installDependenciesCommand() {
        return "pip install -r requirements.txt"
    }
    
    static String testCommand(String testTool = 'pytest') { // doubt in this here how to give dynamically??? I have give static value for now!!
        switch(testTool) {
            case 'pytest': return "pytest --verbose --tb=short"
            case 'unittest': return "python -m unittest discover -v"
            default: throw new IllegalArgumentException("Unknown test tool: $testTool")
        }
    }
    
    static String lintCommand(String lintTool = 'pylint') { // doubt in this here how to give dynamically??? I have give static value for now!!
        switch(lintTool) {
            case 'pylint': return "pylint **/*.py --output-format=text"
            case 'flake8': return "flake8 ."
            case 'black': return "black --check ."
            default: throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
    
}