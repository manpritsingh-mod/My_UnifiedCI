def buildLanguages(String language, Map config = [:]) {
    Logger.info("Starting the build process")

    if (language in ['java-maven', 'java-gradle']) {
        return buildJavaApp(language == 'java-maven' ? 'maven' : 'gradle', config)
    } else if (language == 'python') {
        return buildPythonApp(config)
    } else {
        Logger.error("Unsupported language: ${language}")
        return false
    }
}

def buildJavaApp(String buildTool, Map config = [:]){
    Logger.info("Building Java Project with ${buildTool}")

    try{
        if(!installDependencies('java', buildTool, config)){ // basically this ensure that all the dependencies has been installed before the build process begins
            return false
        }
        if(buildTool == 'maven'){
            return buildMavenApp(config)
        }
        else if(buildTool == 'gradle'){
            return buildGradleApp(config)
        }
        return false
    }
    catch(Exception e){
        Logger.error("Java Build Error: ${e.getMessage()}")
        return false
    }
}

def buildMavenApp(Map config = [:]){
    Logger.info("Executing the Maven App")

    try{
        return task_buildMavenApp(config)
    }
    catch(Exception e){
        Logger.error("Execution has been failed: ${e.getMessage()}")
        return false
    }
}

private Boolean task_buildMavenApp(Map config) {
    Logger.info("Maven build logic execution")
    // sh script: MavenScript.buildCommand()
    bat script: MavenScript.buildCommand()
    Logger.info("Maven build executed successfully")
    return true
}

def buildGradleApp(Map config = [:]){
    Logger.info("Execution of the Gradle App")

    try{
        return task_buildGradleApp(config)
    }
    catch(Exception e){
        Logger.error("Execution has been failed: ${e.getMessage()}")
        return false
    }
}

private Boolean task_buildGradleApp(Map config[:]){
    Logger.info("Gradle build logic execution")
    // sh script: GradleScript.buildCommand()
    bat script: GradleScript.buildCommand()
    Logger.info("Gradle build executed successfully")
    return true
}

def buildPythonApp(Map config = [:]) {
    Logger.info("Building Python app")
    try{
        if (!installDependencies('python', 'pip' config)){
        return false
        }
        return task_buildPythonApp(config)
    }
    catch(Exception e){
        Logger.error("Python Build error")
        return false
    }
    
}

private Boolean task_buildPythonApp(Map config) {
    Logger.info("Python build logic execution")
    // sh script: PythonScript.buildCommand()
    bat script: PythonScript.buildCommand()
    Logger.info("No recognized build file found")
    return true
}

def installDependencies(String language, String buildTool, Map config = [:]){
    Logger.info("Installing the dependencies for the ${language} with ${buildTool}")

    try{
        switch(language){
            case 'java':
                return installJavaDependencies(buildTool, config)
            case 'python':
                return installPythonDependencies(config)
            default:
                Logger.error("Unsupported language for dependencies installation")
                return false
        }
        catch(Exception e){
            Logger.error("Dependenceis installation failed: ${e.getMessage()}")
            return false
        }
    }
}

def installJavaDependencies(String buildTool, Map config = [:]){
    Logger.info("Install Java Dependencies with ${buildTool}")

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
        Logger.error("Java Dependencies insatllation failed ${e.getMessage()}")
        return false
    }
}

def task_mavenDependencies(Map config = [:]){
    Logger.info("Maven Dependencies Logic")

    // sh script: MavenScript.installDependenciesCommand()
    bat script: MavenScript.installDependenciesCommand()

    Logger.info("Maven Dependenceis installed successfully")
    return true
}

def task_gradleDependencies(Map config = [:]){
    Logger.info("Gradle Dependencies Logic")

    // sh script: GradleScript.installDependenciesCommand()
    bat script: GradleScript.installDependenciesCommand()

    Logger.info("Gradle Dependencies isntalled successfully")
    return true
}

def installPythonDependencies(Map config = [:]){
    Logger.info("Installing the Python Dependencies")

    try{
        return task_pythonDependencies(config)
    }
    catch(Exception e){
        Logger.error("Python Dependencies installation failed: ${e.getMessage()}")
        return false
    }
}

def task_pythonDependencies(Map config = [:]){
    Logger.info("Python Dependencies logic")

    if(fileExists('requirement.txt')){
        // sh script: PythonScript.installDependenciesCommand()
        bat script: PythonScript.installDependenciesCommand()
    }

    Logger.info("Python Dependencies installed successfully")
    return true
}
