import Logger

def checkout(String repoURL = '', String branch = '', Map config = [:]) {
    if (!repoURL?.trim()) {
        Logger.warning("No repository URL provided. Using default SCM checkout")
        try {
            checkout scm
            return true
        } catch (Exception e) {
            Logger.error("SCM checkout failed: ${e.getMessage()}")
            return false
        }
    }
    
    def checkoutBranch = branch ?: config.branch ?: 'main'
    Logger.info("Checking out ${repoURL} on branch ${checkoutBranch}")
    
    try {
        // Use GitHubManager to get SCM configuration
        def scmConfig = GitHubManager.getCheckoutScmConfig(repoURL, checkoutBranch)
        checkout(scmConfig)
        return true
    } catch (Exception e) {
        Logger.error("Checkout failed: ${e.getMessage()}")
        return false
    }
}

def validateRepoAccess(String repoUrl) {
    try {
        // Use GitHubManager to get validation configuration
        def validationResult = GitHubManager.validateRepoAccess(repoUrl)
        
        if (!validationResult.valid) {
            Logger.error(validationResult.error)
            return false
        }
        
        def timeout = 5 // seconds
        def status = bat(
            script: validationResult.command,
            returnStatus: true,
            timeout: timeout
        )
        
        if (status == 0) {
            Logger.info("Repository access validated")
            return true
        }
        Logger.error("Repository validation failed (status ${status})")
        return false
    } catch (Exception e) {
        Logger.error("Validation error: ${e.getMessage()}")
        return false
    }
}
