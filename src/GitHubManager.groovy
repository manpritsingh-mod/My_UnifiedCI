class GitHubManager {
    /**
     * Validates if a repository is accessible.
     * @param repoUrl The URL of the repository.
     * @return A map with status and command to be executed.
     */
    static Map validateRepoAccess(String repoUrl) {
        if (!repoUrl?.trim()) {
            return [valid: false, error: "Repository URL is empty."]
        }
        // Returns the command to be run by the pipeline script
        return [
            valid: true, 
            command: "git ls-remote --exit-code ${repoUrl}"
        ]
    }

    /**
     * Creates the SCM configuration map for the checkout step.
     * @param repoUrl The URL of the repository.
     * @param branch The branch to check out.
     * @return A map containing the SCM configuration.
     */
    static Map getCheckoutScmConfig(String repoUrl, String branch) {
        return [
            $class: 'GitSCM',
            branches: [[name: branch]],
            extensions: [[$class: 'CloneOption', timeout: 5]],
            userRemoteConfigs: [[url: repoUrl]]
        ]
    }
}
