/**
 * Main method to build projects for different programming languages
 * @param language Project language ('java-maven', 'java-gradle', or 'python')
 * @param config Pipeline configuration map (optional)
 * @return Boolean true if build succeeds, false if it fails
 * Usage: def success = core_build.buildLanguages('java-maven', config)
 */
def buildLanguages(String language, Map config = [:]) {
    logger.info("Starting the build process")

    if (language in ['java-maven', 'java-gradle']) {
        return buildJavaApp(language == 'java-maven' ? 'maven' : 'gradle', config)
    } else if (language == 'python') {
        return buildPythonApp(config)
    } else {
        logger.error("Unsupported language: ${language}")
        return false
    }
}

/**
 * Builds Java applications using Maven or Gradle build tools
 * @param buildTool Build tool to use ('maven' or 'gradle')
 * @param config Pipeline configuration map
 * @return Boolean true if build succeeds, false if it fails
 * Usage: def success = buildJavaApp('maven', config)
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
 * Executes Maven build command (mvn compile package)
 * @param config Pipeline configuration map
 * @return Boolean true when build completes successfully
 */
private Boolean task_buildMavenApp(Map config) {
    logger.info("Maven build logic execution")
    bat script: MavenScript.buildCommand()
    // sh script: MavenScript.buildCommand()  // Linux equivalent
    logger.info("Maven build executed successfully")
    return true
}

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

private Boolean task_buildGradleApp(Map config = [:]){
    logger.info("Gradle build logic execution")
    bat script: GradleScript.buildCommand()
    // sh script: GradleScript.buildCommand()  // Linux equivalent
    logger.info("Gradle build executed successfully")
    return true
}

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
 * Executes Python build command (python setup.py build)
 * @param config Pipeline configuration map
 * @return Boolean true when build completes successfully
 */
private Boolean task_buildPythonApp(Map config) {
    logger.info("Python build logic execution")
    bat script: PythonScript.buildCommand()
    // sh script: PythonScript.buildCommand()  // Linux equivalent
    logger.info("Python build executed successfully")
    return true
}


/**
 * Installs project dependencies for different programming languages and build tools
 * @param language Programming language ('java' or 'python')
 * @param buildTool Build tool ('maven', 'gradle', or 'pip')
 * @param config Pipeline configuration map
 * @return Boolean true if installation succeeds, false if it fails
 * Usage: def success = core_build.installDependencies('java', 'maven', config)
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

def installJavaDependencies(String buildTool, Map config = [:]){
    logger.info("Install Java Dependencies with ${buildTool}")

    try{
        if(buildTool == 'maven'){
            return task_mavenDependencies(config)
        }
        else if(buildTool == 'gradle'){
            return task_gradleDependencies(config)
        }
        return false
    }
    catch(Exception e){
        logger.error("Java Dependencies installation failed ${e.getMessage()}")
        return false
    }
}

def task_mavenDependencies(Map config = [:]){
    logger.info("Maven Dependencies Logic")

    bat script: MavenScript.installDependenciesCommand()
    // sh script: MavenScript.installDependenciesCommand()  // Linux equivalent

    logger.info("Maven Dependencies installed successfully")
    return true
}

def task_gradleDependencies(Map config = [:]){
    logger.info("Gradle Dependencies Logic")

    bat script: GradleScript.installDependenciesCommand()
    // sh script: GradleScript.installDependenciesCommand()  // Linux equivalent

    logger.info("Gradle Dependencies installed successfully")
    return true
}

def installPythonDependencies(Map config = [:]){
    logger.info("Installing the Python Dependencies")

    try{
        return task_pythonDependencies(config)
    }
    catch(Exception e){
        logger.error("Python Dependencies installation failed: ${e.getMessage()}")
        return false
    }
}

def task_pythonDependencies(Map config = [:]){
    logger.info("Python Dependencies logic")

    if(fileExists('requirements.txt')){
        bat script: PythonScript.installDependenciesCommand()
        // sh script: PythonScript.installDependenciesCommand()  // Linux equivalent
    }

    logger.info("Python Dependencies installed successfully")
    return true
}
