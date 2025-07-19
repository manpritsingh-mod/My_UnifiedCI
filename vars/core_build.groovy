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

private Boolean task_buildMavenApp(Map config) {
    logger.info("Maven build logic execution")
    // sh script: MavenScript.buildCommand()
    bat script: MavenScript.buildCommand()
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
    // sh script: GradleScript.buildCommand()
    bat script: GradleScript.buildCommand()
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

private Boolean task_buildPythonApp(Map config) {
    logger.info("Python build logic execution")
    // sh script: PythonScript.buildCommand()
    bat script: PythonScript.buildCommand()
    logger.info("No recognized build file found")
    return true
}


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

    // sh script: MavenScript.installDependenciesCommand()
    bat script: MavenScript.installDependenciesCommand()

    logger.info("Maven Dependencies installed successfully")
    return true
}

def task_gradleDependencies(Map config = [:]){
    logger.info("Gradle Dependencies Logic")

    // sh script: GradleScript.installDependenciesCommand()
    bat script: GradleScript.installDependenciesCommand()

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
        // sh script: PythonScript.installDependenciesCommand()
        bat script: PythonScript.installDependenciesCommand()
    }

    logger.info("Python Dependencies installed successfully")
    return true
}
