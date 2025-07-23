def setupEnvironment(){
    logger.info("Setting up Build Environment")

    env.BUILD_TIMESTAMP = new Date().format("yyyy-MM-dd_HH-mm-ss")
    env.BUILD_USER = env.BUILD_USER ?: 'jenkins'

    logger.info("Environment variables set - Timestamp: ${env.BUILD_TIMESTAMP}, User: ${env.BUILD_USER}")
    return true
}

def detectProjectLanguage(){
    logger.info("Detecting project language")

    return task_languageDetection()
}

def task_languageDetection(){
    logger.info("Executing language detection logic")

    def detectedLanguage = null

    if(fileExists('pom.xml')){
        detectedLanguage = 'java-maven'
        logger.info("Detected Java-Maven project (pom.xml found)")
    }
    else if (fileExists('build.gradle') || fileExists('build.gradle.kts')){
        detectedLanguage = 'java-gradle'
        logger.info("Detected Java-gradle project (build.gradle found)")
    }
    else if (fileExists('requirements.txt') || fileExists('setup.py') || fileExists('pyproject.toml')){
        detectedLanguage = 'python'
        logger.info("Detected Python Project (requirements.txt found)")
    }
    else{
        logger.warning("Could not detect project language !!")
        detectedLanguage = 'unknown'
    }
    return detectedLanguage
}

def setupProjectEnvironment(String language, Map config = [:]){
    logger.info("Setting up project environment for language: ${language}")
    switch(language){
        case 'java-maven':
            env.BUILD_TOOL = 'maven'
            env.BUILD_COMMAND = 'mvn'
            env.TEST_COMMAND = 'mvn test'
            break
        case 'java-gradle':
            env.BUILD_TOOL = 'gradle'
            env.BUILD_COMMAND = './gradlew'
            env.TEST_COMMAND = 'gradlew test'
            break
        case 'python':
            env.BUILD_TOOL = 'pip'
            env.BUILD_COMMAND = 'pip'
            env.TEST_COMMAND = 'pytest'
            break
        default:
            logger.warning("Unknown Language ${language}")
    }

    if(config.runUnitTests != null){
        env.RUN_UNIT_TESTS = config.runUnitTests.toString()
    }
    if(config.runLintTests != null){
        env.RUN_LINT_TESTS = config.runLintTests.toString()
    }
    logger.info("Project environment setup completed")
    return true
}

def readProjectConfig() {
    logger.info("Reading project configuration")

    def configFile = fileExists('ci-config.yaml') ? 'ci-config.yaml' : 
                     (fileExists('ci-config.yml') ? 'ci-config.yml' : null)

    if (configFile == null) {
        echo "No config file found, using default configuration"
        return getDefaultConfig()
    }

    logger.info("Config file found: ${configFile}")

    def fileContent = readFile(configFile)
    logger.info("File content:\n${fileContent}")

    def config = [:]

    try {
        // Try parsing YAML directly
        config = readYaml text: fileContent
        logger.info("Parsed config: ${config}")
    } catch (Exception e) {
        logger.warning("readYaml failed: ${e.getMessage()}")
        logger.info("Using default configuration instead")
        config = getDefaultConfig()
    }
    return validateAndSetDefaults(config)
}


def validateAndSetDefaults(Map config){
    if (!config.project_language){
        config.project_language = detectProjectLanguage()
        logger.warning("No language specified, detected: ${config.project_language}")
    }
    
    // Set default tools checks for any value has been set or not 
    if (!config.tool_for_unit_testing){
        config.tool_for_unit_testing = [:] 
    }
    if (!config.tool_for_lint_testing){
        config.tool_for_lint_testing = [:]
    }
    
    // Java defaults case
    if (['java-maven', 'java-gradle'].contains(config.project_language)) {
        config.tool_for_unit_testing.java = config.tool_for_unit_testing.java ?: 'junit'
        config.tool_for_lint_testing.java = config.tool_for_lint_testing.java ?: 'checkstyle'
    } 
    // Python defaults case
    else if (config.project_language == 'python') {
        config.tool_for_unit_testing.python = config.tool_for_unit_testing.python ?: 'pytest'
        config.tool_for_lint_testing.python = config.tool_for_lint_testing.python ?: 'pylint'
    }
    
    return config
}



def getDefaultConfig(){
    logger.info("Using Default Configuration -> All the stages will run by default")

    def config =[
        project_language: detectProjectLanguage(),
        runUnitTests: true,
        runLintTests: true,
        tool_for_unit_testing: [:],
        tool_for_lint_testing: [:]
    ]

    def language = config.project_language
    if (language == 'java-maven' || language == 'java-gradle') {
        config.tool_for_unit_testing = [java: 'junit']
        config.tool_for_lint_testing = [java: 'checkstyle']
    } else if (language == 'python') {
        config.tool_for_unit_testing = [python: 'pytest']
        config.tool_for_lint_testing = [python: 'pylint']
    }
    return config
}

// Helper method to check the stage should be executed
def shouldExecuteStage(String stageName, Map config){
    switch(stageName.toLowerCase()) {
        case 'unittest':
        case 'unit_test':
        case 'unit-test':
            return config.runUnitTests == true
        case 'lint':
        case 'linttest':
        case 'lint_test':
        case 'lint-test':
            return config.runLintTests == true
        default:
            return true
    }
}
