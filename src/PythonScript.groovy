class PythonScript {
    static String buildCommand() {
        def pythonCmd = System.getenv('PYTHON_CMD') ?: 'python'
        return "${pythonCmd} setup.py build"
    }

    static String installDependenciesCommand() {
        def pipCmd = System.getenv('PIP_CMD') ?: 'pip'
        return "${pipCmd} install -r requirements.txt"
    }
    
    static String testCommand(String testTool = 'pytest') {
        def pythonCmd = System.getenv('PYTHON_CMD') ?: 'python'
        switch(testTool) {
            case 'pytest': 
                return "${pythonCmd} -m pytest --verbose --tb=short --junit-xml=test-results.xml"
            case 'unittest': 
                return "${pythonCmd} -m unittest discover -v"
            default: 
                throw new IllegalArgumentException("Unknown test tool: $testTool")
        }
    }
    
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
                throw new IllegalArgumentException("Unknown lint tool: $lintTool")
        }
    }
}