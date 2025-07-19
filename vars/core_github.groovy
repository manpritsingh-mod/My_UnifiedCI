def checkout(String repoURL = '', String branch = '', Map config = [:]) {
    if (!repoURL?.trim()) {
        logger.warning("No repository URL provided. Using default SCM checkout")
        try {
            checkout scm
            return true
        } catch (Exception e) {
            logger.error("SCM checkout failed: ${e.getMessage()}")
            return false
        }
    }
    
    def checkoutBranch = branch ?: config.branch ?: 'main'
    logger.info("Checking out ${repoURL} on branch ${checkoutBranch}")
    
    try {
        // Use GitHubManager to get SCM configuration
        def scmConfig = GitHubManager.getCheckoutScmConfig(repoURL, checkoutBranch)
        checkout(scmConfig)
        return true
    } catch (Exception e) {
        logger.error("Checkout failed: ${e.getMessage()}")
        return false
    }
}

def validateRepoAccess(String repoUrl) {
    try {
        // Use GitHubManager to get validation configuration
        def validationResult = GitHubManager.validateRepoAccess(repoUrl)
        
        if (!validationResult.valid) {
            logger.error(validationResult.error)
            return false
        }
        
        def timeout = 5 // seconds
        def status = bat(
            script: validationResult.command,
            returnStatus: true,
            timeout: timeout
        )
        
        if (status == 0) {
            logger.info("Repository access validated")
            return true
        }
        logger.error("Repository validation failed (status ${status})")
        return false
    } catch (Exception e) {
        logger.error("Validation error: ${e.getMessage()}")
        return false
    }
}
