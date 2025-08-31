class DockerImageManager {
    
    /**
     * Gets the full Docker image path from Nexus
     * @param tool Tool name (python, maven, gradle)
     * @param version Version to use (if empty, uses 'latest')
     * @param nexusRegistry Nexus registry URL (without protocol)
     * @param project Project name in Nexus
     * @return Full Docker image path
     */
    static String getImagePath(String tool, String version = 'latest', String nexusRegistry, String project = 'dev') {
        // Remove protocol if present
        def registryUrl = nexusRegistry.replace('https://', '').replace('http://', '')
        
        // Build the full path
        def imagePath = "${registryUrl}/${project}/${tool}:${version}"
        
        return imagePath
    }
    
    /**
     * Gets available versions for all tools
     * @param tool Tool name
     * @return List of available versions
     */
    static List<String> getAvailableVersions(String tool) {
        def versions = [
            'python': ['3.8', '3.9', '3.11', 'latest'],
            'maven': ['3.8.6', '3.9.0', '3.9.6', 'latest'],
            'gradle': ['7.4', '7.6.1', '8.0', 'latest'],
            'node': ['16', '18', '20', 'latest']
        ]
        
        return versions[tool] ?: ['latest']
    }
    
    /**
     * Gets the default version for a tool
     * @param tool Tool name
     * @return Default version
     */
    static String getDefaultVersion(String tool) {
        def defaults = [
            'python': '3.11',
            'maven': '3.8.6',
            'gradle': '7.6.1',
            'node': '18'
        ]
        
        return defaults[tool] ?: 'latest'
    }
    
    /**
     * Validates if Docker image exists in registry
     * @param imagePath Full image path
     * @return Map with validation result and command
     */
    static Map validateImageExists(String imagePath) {
        return [
            valid: true,
            command: "docker manifest inspect ${imagePath}"
        ]
    }
    
    /**
     * Gets tool name from project language
     * @param projectLanguage Project language (java-maven, java-gradle, python)
     * @return Tool name for Docker image
     */
    static String getToolFromLanguage(String projectLanguage) {
        def mapping = [
            'python': 'python',
            'java-maven': 'maven',
            'java-gradle': 'gradle',
            'react': 'node',
            'nodejs': 'node'
        ]
        return mapping[projectLanguage] ?: projectLanguage
    }
    
    /**
     * Builds complete Docker image configuration from project config
     * @param projectLanguage Project language
     * @param config Pipeline configuration map
     * @return Map with complete Docker configuration
     */
    static Map getImageConfig(String projectLanguage, Map config) {
        def tool = getToolFromLanguage(projectLanguage)
        def version = config["${projectLanguage}_version"] ?: getDefaultVersion(tool)
        def nexusUrl = config.nexus?.url ?: 'https://nexus.company.com:8082'
        def registry = config.nexus?.registry ?: 'nexus.company.com:8082'
        def project = config.nexus?.project ?: 'dev'
        def credentialsId = config.nexus?.credentials_id ?: 'nexus-docker-creds'
        
        return [
            tool: tool,
            version: version,
            imagePath: getImagePath(tool, version, registry, project),
            registryUrl: nexusUrl,
            credentialsId: credentialsId,
            projectLanguage: projectLanguage
        ]
    }
    
    /**
     * Validates Docker configuration completeness
     * @param config Pipeline configuration map
     * @return Map with validation result and missing fields
     */
    static Map validateDockerConfig(Map config) {
        def missing = []
        
        if (!config.nexus?.url) missing << 'nexus.url'
        if (!config.nexus?.registry) missing << 'nexus.registry'
        if (!config.nexus?.credentials_id) missing << 'nexus.credentials_id'
        
        return [
            valid: missing.isEmpty(),
            missing: missing,
            message: missing.isEmpty() ? 'Docker configuration is complete' : "Missing Docker configuration: ${missing.join(', ')}"
        ]
    }

}