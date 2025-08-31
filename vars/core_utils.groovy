def setupEnvironment(){
    logger.info("Setting up Build Environment")

    env.BUILD_TIMESTAMP = new Date().format("yyyy-MM-dd_HH-mm-ss")
    env.BUILD_USER = env.BUILD_USER ?: 'jenkins'

    logger.info("Environment variables set - Timestamp: ${env.BUILD_TIMESTAMP}, User: ${env.BUILD_USER}")
    return true
}

/**
 * Detects project programming language by examining build files
 * @return String detected language ('java-maven', 'java-gradle', 'python', or 'unknown')
 * Usage: def language = core_utils.detectProjectLanguage()
 */
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
    else if (fileExists('package.json')){
        // Check if it's a React project by examining package.json content
        def packageJson = readJSON file: 'package.json'
        def dependencies = packageJson.dependencies ?: [:]
        def devDependencies = packageJson.devDependencies ?: [:]
        
        if (dependencies.react || devDependencies.react || 
            dependencies['@types/react'] || devDependencies['@types/react'] ||
            packageJson.scripts?.start?.contains('react-scripts') ||
            dependencies['react-scripts'] || devDependencies['react-scripts']) {
            detectedLanguage = 'react'
            logger.info("Detected React Project (package.json with React dependencies found)")
        } else {
            detectedLanguage = 'nodejs'
            logger.info("Detected Node.js Project (package.json found)")
        }
    }
    else{
        logger.warning("Could not detect project language !!")
        detectedLanguage = 'unknown'
    }
    return detectedLanguage
}

/**
 * Sets up Jenkins environment variables based on detected project language
 * @param language Project language ('java-maven', 'java-gradle', or 'python')
 * @param config Pipeline configuration map
 * @return Boolean true when setup completes
 * Usage: core_utils.setupProjectEnvironment('java-maven', config)
 */
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
        case 'react':
            env.BUILD_TOOL = 'npm'
            env.BUILD_COMMAND = 'npm'
            env.TEST_COMMAND = 'npm test'
            env.LINT_COMMAND = 'npm run lint'
            env.START_COMMAND = 'npm start'
            break
        case 'nodejs':
            env.BUILD_TOOL = 'npm'
            env.BUILD_COMMAND = 'npm'
            env.TEST_COMMAND = 'npm test'
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
    if(config.runFunctionalTests != null){
        env.RUN_FUNCTIONAL_TESTS = config.runFunctionalTests.toString()
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

/**
 * Sets up Docker configuration with defaults - no hardcoded values
 * @param config Pipeline configuration map
 * @return Updated configuration with Docker defaults
 */
def setupDockerConfiguration(Map config) {
    logger.info("Setting up Docker configuration")
    
    // Ensure nexus configuration exists
    if (!config.nexus) {
        config.nexus = [:]
    }
    
    // Set default Nexus configuration only if not provided
    if (!config.nexus.url) {
        logger.warning("nexus.url not specified, using default")
        config.nexus.url = 'https://nexus.company.com:8082'
    }
    if (!config.nexus.registry) {
        logger.warning("nexus.registry not specified, using default")
        config.nexus.registry = 'nexus.company.com:8082'
    }
    if (!config.nexus.project) {
        logger.warning("nexus.project not specified, using default")
        config.nexus.project = 'dev'
    }
    if (!config.nexus.credentials_id) {
        logger.warning("nexus.credentials_id not specified, using default")
        config.nexus.credentials_id = 'nexus-docker-creds'
    }
    
    // Set default tool versions if not specified
    if (!config.python_version) {
        config.python_version = DockerImageManager.getDefaultVersion('python')
        logger.info("Using default Python version: ${config.python_version}")
    }
    if (!config['java-maven_version']) {
        config['java-maven_version'] = DockerImageManager.getDefaultVersion('maven')
        logger.info("Using default Maven version: ${config['java-maven_version']}")
    }
    if (!config['java-gradle_version']) {
        config['java-gradle_version'] = DockerImageManager.getDefaultVersion('gradle')
        logger.info("Using default Gradle version: ${config['java-gradle_version']}")
    }
    if (!config.react_version) {
        config.react_version = DockerImageManager.getDefaultVersion('node')
        logger.info("Using default Node.js version for React: ${config.react_version}")
    }
    if (!config.nodejs_version) {
        config.nodejs_version = DockerImageManager.getDefaultVersion('node')
        logger.info("Using default Node.js version: ${config.nodejs_version}")
    }
    
    // Validate Docker configuration
    def validation = DockerImageManager.validateDockerConfig(config)
    if (!validation.valid) {
        logger.error("Docker configuration validation failed: ${validation.message}")
        throw new Exception("Invalid Docker configuration: ${validation.message}")
    }
    
    logger.info("Docker configuration setup completed successfully")
    return config
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
    
    // Set default stage execution flags if not specified
    if (config.runUnitTests == null) {
        config.runUnitTests = true
    }
    if (config.runLintTests == null) {
        config.runLintTests = true
    }
    if (config.runFunctionalTests == null) {
        config.runFunctionalTests = true
    }
    if (config.runSmokeTests == null) {
        config.runSmokeTests = true
    }
    if (config.runSanityTests == null) {
        config.runSanityTests = true
    }
    if (config.runRegressionTests == null) {
        config.runRegressionTests = true
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
    // React defaults case
    else if (config.project_language == 'react') {
        config.tool_for_unit_testing.react = config.tool_for_unit_testing.react ?: 'jest'
        config.tool_for_lint_testing.react = config.tool_for_lint_testing.react ?: 'eslint'
    }
    // Node.js defaults case
    else if (config.project_language == 'nodejs') {
        config.tool_for_unit_testing.nodejs = config.tool_for_unit_testing.nodejs ?: 'jest'
        config.tool_for_lint_testing.nodejs = config.tool_for_lint_testing.nodejs ?: 'eslint'
    }
    
    // Setup Docker configuration with validation
    config = setupDockerConfiguration(config)
    
    return config
}


/**
 * Creates default pipeline configuration when no ci-config.yaml file exists
 * @return Map default configuration with all stages enabled and standard tools
 * Usage: def config = core_utils.getDefaultConfig()
 */
def getDefaultConfig(){
    logger.info("Loading default configuration")
    
    def config = [
        project_language: detectProjectLanguage(),
        runUnitTests: true,
        runLintTests: true,
        runFunctionalTests: true,
        runSmokeTests: true,
        runSanityTests: true,
        runRegressionTests: true,
        tool_for_unit_testing: [:],
        tool_for_lint_testing: [:]
    ]
    
    // Set language-specific defaults
    def language = config.project_language
    if (language == 'java-maven' || language == 'java-gradle') {
        config.tool_for_unit_testing = [java: 'junit']
        config.tool_for_lint_testing = [java: 'checkstyle']
    } else if (language == 'python') {
        config.tool_for_unit_testing = [python: 'pytest']
        config.tool_for_lint_testing = [python: 'pylint']
    } else if (language == 'react') {
        config.tool_for_unit_testing = [react: 'jest']
        config.tool_for_lint_testing = [react: 'eslint']
    } else if (language == 'nodejs') {
        config.tool_for_unit_testing = [nodejs: 'jest']
        config.tool_for_lint_testing = [nodejs: 'eslint']
    }
    
    // Setup Docker configuration with defaults
    config = setupDockerConfiguration(config)
    
    logger.info("Default configuration loaded for ${language} with Docker setup")
    return config
}

/**
 * Checks if a pipeline stage should be executed based on configuration
 * @param stageName Name of stage to check ('unittest', 'lint', 'functionaltest', 'smoketest', etc.)
 * @param config Pipeline configuration map
 * @return Boolean true if stage should run, false if disabled
 * Usage: if (core_utils.shouldExecuteStage('smoketest', config)) { runSmokeTests() }
 */
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
        case 'functionaltest':
        case 'functional_test':
        case 'functional-test':
        case 'functionaltests':
        case 'functional_tests':
        case 'functional-tests':
            return config.runFunctionalTests == true
        case 'smoketest':
        case 'smoke_test':
        case 'smoke-test':
        case 'smoketests':
        case 'smoke_tests':
        case 'smoke-tests':
            return config.runSmokeTests == true
        case 'sanitytest':
        case 'sanity_test':
        case 'sanity-test':
        case 'sanitytests':
        case 'sanity_tests':
        case 'sanity-tests':
            return config.runSanityTests == true
        case 'regressiontest':
        case 'regression_test':
        case 'regression-test':
        case 'regressiontests':
        case 'regression_tests':
        case 'regression-tests':
            return config.runRegressionTests == true
        default:
            logger.warning("Unknown stage name: ${stageName}. Defaulting to true.")
            return true
    }
}
