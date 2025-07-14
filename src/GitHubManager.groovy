class GitHubManager {
    static boolean validateRepoAccess(String repoUrl) {
        if (!repoUrl?.trim()) {
            Logger.warning("No repo URL is provided: ${repoUrl}")
            return false
        }
        return true 
    }
}