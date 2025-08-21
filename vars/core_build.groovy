/**
* Main method to build project for different programming languages
* @param language: Project language ('java-maven', 'java-gradle', 'python')
* @param config: pipeline configuration map (optional)
* @return Boolean true if build succeeds, false if it falls 
* Usage: core_build.buildLanguages('python', config)
*/
def buildLanguages(String language, Map config = [:]) {
    logger.info("Starting Docker-based build for ${language}")

    def version = config["${language}_version"] ?: DockerImageManager.getDefaultVersion(language)
    def imagePath = DockerImageManager.getImagePath(
        language,
        version,
        config.nexus?.registry ?: 'localhost:9092',
        config.nexus?.project ?: 'dev'
    )

    logger.info("Using Docker image: ${imagePath}")

    def result = false

    docker.withRegistry(config.nexus?.url ?: 'http://localhost:9092', config.nexus?.credentials_id ?: 'nexus-docker-creds') {
        def image = docker.image(imagePath)
        image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
            if (language in ['java-maven', 'java-gradle']) {
                result = buildJavaApp(language == 'java-maven' ? 'maven' : 'gradle', config)
            } else if (language == 'python') {
                result = buildPythonApp(config)
            } else {
                logger.error("Unsupported language: ${language}")
                result = false
            }
        }
    }

    return result
}

/**
* Build Java application using Maven or Gradle build tools
* @param buildTool Build tool to use ('maven' or 'gradle')
* @param config: pipeline configuration map
* @return Boolean true if build succeeds, false if it falls 
* Usage: buildJavaApp('python', config)
*/
def buildJavaApp(String buildTool, Map config = [:]){
    logger.info("Building Java Project with ${buildTool}")

    try{
        if(buildTool == 'maven'){
            return buildMavenApp(config)
        }
        else if(buildTool == 'gradle'){
            return buildGradleApp(config)
        }
        return false
    }
    catch(Exception e){
        logger.error("Java Build Error: ${e.getMessage()}")
        return false
    }
}

/**
* Getting call by the buildJavaApp & further calling task_buildMavenApp()
*/
def buildMavenApp(Map config = [:]){
    logger.info("Executing the Maven App")

    try{
        return task_buildMavenApp(config)
    }
    catch(Exception e){
        logger.error("Execution has been failed: ${e.getMessage()}")
        return false
    }
}

/**
* Execute Maven build commands (mvn compile package)
* @param config: pipeline configuration map
* return Boolean true when build executes successfully
*/
private Boolean task_buildMavenApp(Map config) {
    logger.info("Maven build logic execution")
    sh script: MavenScript.buildCommand()
    logger.info("Maven build executed successfully")
    return true
}

/**
* Getting call by the buildJavaApp & further calling task_buildGradleApp()
*/
def buildGradleApp(Map config = [:]){
    logger.info("Execution of the Gradle App")

    try{
        return task_buildGradleApp(config)
    }
    catch(Exception e){
        logger.error("Execution has been failed: ${e.getMessage()}")
        return false
    }
}

/**
* Execute Gradle build commands
* @param config: pipeline configuration map
* return Boolean true when build executes successfully
*/
private Boolean task_buildGradleApp(Map config = [:]){
    logger.info("Gradle build logic execution")
    sh script: GradleScript.buildCommand()
    logger.info("Gradle build executed successfully")
    return true
}

/**
* Getting call by the buildLanguages & further calling task_buildPythonApp()
*/
def buildPythonApp(Map config = [:]) {
    logger.info("Building Python app")
    try{
        return task_buildPythonApp(config)
    }
    catch(Exception e){
        logger.error("Python Build error")
        return false
    }
    
}

/**
* Executes Python Build command
* @param config Pipeline configuration map
* return Boolean true when build executes successfully
*/
private Boolean task_buildPythonApp(Map config) {
    logger.info("Python build logic execution")
    sh script: PythonScript.buildCommand()
    logger.info("No recognized build file found")
    return true
}

/**
 * Installs project dependencies for different programming languages and build tools
 * @param language Programming language ('java' or 'python')
 * @param buildTool Build tool ('maven', 'gradle', or 'pip')
 * @param config Pipeline configuration map
 * @return Boolean true if installation succeeds, false if it fails
 * Usage: core_build.installDependencies('java', 'maven', config)
 */
def installDependencies(String language, String buildTool, Map config = [:]){
    logger.info("Installing the dependencies for the ${language} with ${buildTool}")

    try{
        switch(language){
            case 'java':
                return installJavaDependencies(buildTool, config)
            case 'python':
                return installPythonDependencies(config)
            default:
                logger.error("Unsupported language for dependencies installation")
                return false
        }
    }
    catch(Exception e){
        logger.error("Dependencies installation failed: ${e.getMessage()}")
        return false
    }
}

/**
 * Installs Java dependencies based on the specified build tool (Maven or Gradle).
 *
 * @param buildTool The Java build tool to use for dependency installation ('maven' or 'gradle').
 * @param config Optional pipeline configuration map passed to dependency installation tasks.
 * @return true if dependencies were installed successfully, false otherwise.
 */
def installJavaDependencies(String buildTool, Map config = [:]){
    logger.info("Install Java Dependencies with ${buildTool}")

    try {
        if (buildTool == 'maven') {
            return task_mavenDependencies(config)
        } else if (buildTool == 'gradle') {
            return task_gradleDependencies(config)
        }
        return false
    } catch (Exception e) {
        logger.error("Java Dependencies installation failed ${e.getMessage()}")
        return false
    }
}

/**
 * Handles the logic for installing dependencies using Maven.
 *
 * @param config Optional configuration map for Maven dependency logic.
 * @return true after successfully running the Maven dependency installation command.
 */
def task_mavenDependencies(Map config = [:]) {
    logger.info("Maven Dependencies Logic")

    sh script: MavenScript.installDependenciesCommand()

    logger.info("Maven Dependencies installed successfully")
    return true
}

/**
 * Handles the logic for installing dependencies using Gradle.
 *
 * @param config Optional configuration map for Gradle dependency logic.
 * @return true after successfully running the Gradle dependency installation command.
 */
def task_gradleDependencies(Map config = [:]) {
    logger.info("Gradle Dependencies Logic")

    sh script: GradleScript.installDependenciesCommand()

    logger.info("Gradle Dependencies installed successfully")
    return true
}

/**
 * Installs Python dependencies by executing pip install on requirements.txt.
 *
 * @param config Optional configuration map for Python dependency logic.
 * @return true if dependencies were installed successfully, false otherwise.
 */
def installPythonDependencies(Map config = [:]){
    logger.info("Installing the Python Dependencies")

    try {
        return task_pythonDependencies(config)
    } catch (Exception e) {
        logger.error("Python Dependencies installation failed: ${e.getMessage()}")
        return false
    }
}

/**
 * Handles the logic for installing Python dependencies using pip.
 * Assumes `requirements.txt` is present in the root directory.
 *
 * @param config Optional configuration map for Python dependency logic.
 * @return true after successful installation, false otherwise.
 */
def task_pythonDependencies(Map config = [:]){
    logger.info("Python Dependencies logic")

    if (fileExists('requirements.txt')) {
        sh script: PythonScript.venvPipInstallLinuxCommand()
    }

    logger.info("Python Dependencies installed successfully")
    return true
}