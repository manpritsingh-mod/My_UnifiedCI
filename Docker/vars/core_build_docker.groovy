def buildLanguages(String language, Map config = [:]) {
    logger.info("Starting Docker-based build for ${language}")
    
    // Get the exact image path from Nexus
    def version = config.versions?."${language}" ?: DockerImageManager.getDefaultVersion(language)
    def imagePath = DockerImageManager.getImagePath(language, version)
    
    logger.info("Using Docker image: ${imagePath}")
    
    // Use the image from Nexus
    docker.withRegistry('https://nexus.company.com:8082', 'nexus-docker-creds') {
        def image = docker.image(imagePath)
        
        return image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
            // Your build logic here
            if (language == 'python') {
                sh 'python --version'
                sh 'pip install -r requirements.txt'
                sh 'python setup.py build'
            } else if (language == 'maven') {
                sh 'mvn --version'
                sh 'mvn clean install'
            } else if (language == 'gradle') {
                sh 'gradle --version'
                sh './gradlew build'
            }
            return true
        }
    }
}
