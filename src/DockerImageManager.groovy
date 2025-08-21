class DockerImageManager {
    
    /**
     * Gets the full Docker image path from Nexus
     * @param tool Tool name (python, maven, gradle)
     * @param version Version to use (if empty, uses 'latest')
     * @param nexusUrl Nexus registry URL
     * @param project Project name in Nexus
     * @return Full Docker image path
     */
    static String getImagePath(String tool, String version = 'latest', String nexusUrl = 'localhost:5000', String project = 'dev') {
        // Remove https:
        def registryUrl = nexusUrl.replace('https://', '').replace('http://', '')
        
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
            'gradle': ['7.4', '7.6.1', '8.0', 'latest']
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
            'gradle': '7.6.1'
        ]
        
        return defaults[tool] ?: 'latest'
    }

}