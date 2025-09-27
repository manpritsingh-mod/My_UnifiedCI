/**
* Main method to build project for different programming languages
* @param language: Project language ('java-maven', 'java-gradle', 'python')
* @param config: pipeline configuration map (optional)
* @return Boolean true if build succeeds, false if it falls 
* Usage: core_build.buildLanguages('python', config)
*/
def buildLanguages(String language, Map config = [:]) {
    logger.info("Starting build for ${language}")

    // NOTE: This method is called from INSIDE Docker containers in templates
    // So we don't need to create another Docker container here
    
    def result = false
    
    if (language in ['maven', 'gradle']) {
        result = buildJavaApp(language, config)
    } else if (language == 'python') {
        result = buildPythonApp(config)
    } else if (language == 'react') {
        result = buildReactApp(config)
    } else if (language == 'react-native') {
        result = buildReactNativeApp(config)
    } else {
        logger.error("Unsupported language: ${language}")
        result = false
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
* Getting call by the buildLanguages & further calling task_buildReactApp()
*/
def buildReactApp(Map config = [:]) {
    logger.info("Building React app")
    try{
        return task_buildReactApp(config)
    }
    catch(Exception e){
        logger.error("React Build error: ${e.getMessage()}")
        return false
    }
}

/**
* Executes React Build command
* @param config Pipeline configuration map
* return Boolean true when build executes successfully
*/
private Boolean task_buildReactApp(Map config) {
    logger.info("React build logic execution")
    sh script: ReactScript.buildCommand()
    logger.info("React build executed successfully")
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
    logger.info("Installing dependencies for ${language} with ${buildTool}")
    
    // NOTE: This method is called from INSIDE Docker containers in templates
    // So we don't need to create another Docker container here
    
    try {
        switch(language) {
            case 'java':
                return installJavaDependencies(buildTool, config)
            case 'python':
                return installPythonDependencies(config)
            case 'react':
                return installReactDependencies(config)
            case 'react-native':
                return installReactNativeDependencies(config)
            default:
                logger.error("Unsupported language for dependencies installation: ${language}")
                return false
        }
    } catch (Exception e) {
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
/**

 * Installs React/Node.js dependencies using npm or yarn
 * @param config Pipeline configuration map
 * @return Boolean true if installation succeeds, false if it fails
 */
def installReactDependencies(Map config = [:]){
    logger.info("Installing React/Node.js Dependencies")

    try {
        return task_reactDependencies(config)
    } catch (Exception e) {
        logger.error("React Dependencies installation failed: ${e.getMessage()}")
        return false
    }
}

/**
 * Handles the logic for installing React dependencies using npm or yarn
 * Assumes `package.json` is present in the root directory
 * @param config Optional configuration map for React dependency logic
 * @return Boolean true after successful installation, false otherwise
 */
def task_reactDependencies(Map config = [:]){
    logger.info("React Dependencies logic")

    if (fileExists('package.json')) {
        sh script: ReactScript.installDependenciesCommand()
        logger.info("React Dependencies installed successfully")
        return true
    } else {
        logger.warning("No package.json found - skipping dependency installation")
        return true
    }
}

/**
 * Build React Native app using mobile_build utilities
 * @param config Pipeline configuration map
 * @return Boolean true if build succeeds
 */
def buildReactNativeApp(Map config = [:]) {
    logger.info("Building React Native app")
    try {
        return mobile_build.buildReactNativeApp(config)
    } catch (Exception e) {
        logger.error("React Native Build error: ${e.getMessage()}")
        return false
    }
}

/**
 * Install React Native dependencies (npm + CocoaPods)
 * @param config Pipeline configuration map
 * @return Boolean true if installation succeeds
 */
def installReactNativeDependencies(Map config = [:]) {
    logger.info("Installing React Native dependencies")
    try {
        return mobile_build.installReactNativeDependencies(config)
    } catch (Exception e) {
        logger.error("React Native Dependencies installation failed: ${e.getMessage()}")
        return false
    }
}