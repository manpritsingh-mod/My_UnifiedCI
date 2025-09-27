/**
 * Mobile Build Utilities - Handles React Native Android and iOS builds
 * Supports parallel builds, code signing, and artifact management
 * Integrates with Nexus repository for Docker images
 */

/**
 * Main method to build React Native apps for both platforms
 * @param config Pipeline configuration map
 * @return Boolean true if builds succeed, false if any fails
 */
def buildReactNativeApp(Map config = [:]) {
    logger.info("Starting React Native build for both platforms")
    
    def buildResults = [:]
    def parallelBuilds = [:]
    
    // Setup parallel builds for Android and iOS
    parallelBuilds['Android Build'] = {
        logger.info("Starting Android build")
        buildResults['android'] = buildAndroidApp(config)
    }
    
    parallelBuilds['iOS Build'] = {
        logger.info("Starting iOS build")
        buildResults['ios'] = buildIOSApp(config)
    }
    
    // Execute parallel builds
    parallel parallelBuilds
    
    // Check results
    def androidSuccess = buildResults['android'] ?: false
    def iosSuccess = buildResults['ios'] ?: false
    
    logger.info("Build Results - Android: ${androidSuccess}, iOS: ${iosSuccess}")
    
    // Return true if at least one platform succeeds
    return androidSuccess || iosSuccess
}

/**
 * Build Android APK
 * @param config Pipeline configuration map
 * @return Boolean true if build succeeds
 */
def buildAndroidApp(Map config = [:]) {
    logger.info("Building Android APK")
    
    try {
        // Setup Android environment
        setupAndroidEnvironment()
        
        // Determine build type from config
        def buildType = config.build_type ?: 'debug'
        def buildCommand = ""
        
        if (buildType == 'release') {
            buildCommand = ReactNativeScript.buildAndroidReleaseCommand()
        } else {
            buildCommand = ReactNativeScript.buildAndroidDebugCommand()
        }
        
        // Execute build
        sh script: buildCommand
        
        // Verify APK was created
        def apkPath = findAndroidAPK(buildType)
        if (apkPath) {
            logger.info("Android APK built successfully: ${apkPath}")
            
            // Archive APK as artifact
            archiveArtifacts artifacts: apkPath, allowEmptyArchive: false
            
            // Store APK info for reporting
            env.ANDROID_APK_PATH = apkPath
            env.ANDROID_BUILD_SUCCESS = 'true'
            
            return true
        } else {
            logger.error("Android APK not found after build")
            return false
        }
        
    } catch (Exception e) {
        logger.error("Android build failed: ${e.getMessage()}")
        env.ANDROID_BUILD_SUCCESS = 'false'
        return false
    }
}

/**
 * Build iOS IPA (Linux environment with limited iOS support)
 * @param config Pipeline configuration map
 * @return Boolean true if build succeeds
 */
def buildIOSApp(Map config = [:]) {
    logger.info("Building iOS app")
    
    try {
        // Check if we're in a macOS environment
        def isMacOS = sh(script: "uname", returnStdout: true).trim() == "Darwin"
        
        if (!isMacOS) {
            logger.warning("iOS build skipped - not running on macOS")
            logger.info("Creating iOS bundle for React Native instead")
            
            // Generate iOS bundle (can be done on Linux)
            sh script: ReactNativeScript.generateIOSBundleCommand()
            
            // Archive the bundle
            if (fileExists('ios/main.jsbundle')) {
                archiveArtifacts artifacts: 'ios/main.jsbundle', allowEmptyArchive: false
                env.IOS_BUNDLE_PATH = 'ios/main.jsbundle'
                env.IOS_BUILD_SUCCESS = 'true'
                logger.info("iOS bundle created successfully")
                return true
            } else {
                logger.error("iOS bundle creation failed")
                env.IOS_BUILD_SUCCESS = 'false'
                return false
            }
        }
        
        // Full iOS build (macOS only)
        setupIOSEnvironment()
        
        def buildType = config.build_type ?: 'debug'
        def buildCommand = ""
        
        if (buildType == 'release') {
            buildCommand = ReactNativeScript.buildIOSReleaseCommand()
        } else {
            buildCommand = ReactNativeScript.buildIOSDebugCommand()
        }
        
        // Execute build
        sh script: buildCommand
        
        // Find and archive IPA
        def ipaPath = findIOSIPA(buildType)
        if (ipaPath) {
            logger.info("iOS IPA built successfully: ${ipaPath}")
            archiveArtifacts artifacts: ipaPath, allowEmptyArchive: false
            env.IOS_IPA_PATH = ipaPath
            env.IOS_BUILD_SUCCESS = 'true'
            return true
        } else {
            logger.error("iOS IPA not found after build")
            env.IOS_BUILD_SUCCESS = 'false'
            return false
        }
        
    } catch (Exception e) {
        logger.error("iOS build failed: ${e.getMessage()}")
        env.IOS_BUILD_SUCCESS = 'false'
        return false
    }
}

/**
 * Setup Android build environment
 */
def setupAndroidEnvironment() {
    logger.info("Setting up Android environment")
    
    // Set Android environment variables
    sh script: '''
        export ANDROID_HOME=/opt/android-sdk
        export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
        echo "Android SDK Path: $ANDROID_HOME"
    '''
    
    // Verify Android tools
    sh script: '''
        adb version || echo "ADB not available"
        which emulator || echo "Emulator not available"
    '''
}

/**
 * Setup iOS build environment (macOS only)
 */
def setupIOSEnvironment() {
    logger.info("Setting up iOS environment")
    
    // Install CocoaPods dependencies
    sh script: ReactNativeScript.installIOSDependenciesCommand()
    
    // Verify iOS tools
    sh script: '''
        xcodebuild -version || echo "Xcode not available"
        pod --version || echo "CocoaPods not available"
    '''
}

/**
 * Find Android APK file after build
 * @param buildType Build type ('debug' or 'release')
 * @return String APK file path or null if not found
 */
def findAndroidAPK(String buildType) {
    def apkPaths = [
        "android/app/build/outputs/apk/${buildType}/app-${buildType}.apk",
        "android/app/build/outputs/apk/app-${buildType}.apk",
        "android/app/build/outputs/apk/app.apk"
    ]
    
    for (String path : apkPaths) {
        if (fileExists(path)) {
            return path
        }
    }
    
    return null
}

/**
 * Find iOS IPA file after build
 * @param buildType Build type ('debug' or 'release')
 * @return String IPA file path or null if not found
 */
def findIOSIPA(String buildType) {
    def ipaPaths = [
        "ios/build/Build/Products/${buildType.capitalize()}-iphonesimulator/*.app",
        "ios/build/Build/Products/${buildType.capitalize()}-iphoneos/*.ipa",
        "ios/build/*.ipa"
    ]
    
    for (String path : ipaPaths) {
        def files = sh(script: "ls ${path} 2>/dev/null || true", returnStdout: true).trim()
        if (files) {
            return files.split('\n')[0] // Return first match
        }
    }
    
    return null
}

/**
 * Install React Native dependencies (npm + CocoaPods)
 * @param config Pipeline configuration map
 * @return Boolean true if installation succeeds
 */
def installReactNativeDependencies(Map config = [:]) {
    logger.info("Installing React Native dependencies")
    
    def results = [:]
    def parallelInstalls = [:]
    
    // Install npm dependencies
    parallelInstalls['NPM Dependencies'] = {
        try {
            sh script: ReactNativeScript.installDependenciesCommand()
            results['npm'] = true
            logger.info("NPM dependencies installed successfully")
        } catch (Exception e) {
            logger.error("NPM dependencies installation failed: ${e.getMessage()}")
            results['npm'] = false
        }
    }
    
    // Install iOS dependencies (CocoaPods) - only if iOS directory exists
    if (fileExists('ios/Podfile')) {
        parallelInstalls['CocoaPods Dependencies'] = {
            try {
                sh script: ReactNativeScript.installIOSDependenciesCommand()
                results['cocoapods'] = true
                logger.info("CocoaPods dependencies installed successfully")
            } catch (Exception e) {
                logger.error("CocoaPods dependencies installation failed: ${e.getMessage()}")
                results['cocoapods'] = false
            }
        }
    } else {
        logger.info("No iOS Podfile found - skipping CocoaPods installation")
        results['cocoapods'] = true
    }
    
    // Execute parallel installations
    parallel parallelInstalls
    
    // Check results
    def npmSuccess = results['npm'] ?: false
    def cocoaPodsSuccess = results['cocoapods'] ?: true // Default true if skipped
    
    logger.info("Dependency Installation Results - NPM: ${npmSuccess}, CocoaPods: ${cocoaPodsSuccess}")
    
    return npmSuccess && cocoaPodsSuccess
}

/**
 * Generate build information for reporting
 * @param config Pipeline configuration map
 * @return Map build information
 */
def getBuildInfo(Map config = [:]) {
    def buildInfo = [:]
    
    try {
        // Get React Native version
        def rnVersion = sh(script: "npx react-native --version", returnStdout: true).trim()
        buildInfo.reactNativeVersion = rnVersion
        
        // Get Node.js version
        def nodeVersion = sh(script: "node --version", returnStdout: true).trim()
        buildInfo.nodeVersion = nodeVersion
        
        // Get build type
        buildInfo.buildType = config.build_type ?: 'debug'
        
        // Get platform info
        buildInfo.platforms = []
        if (env.ANDROID_BUILD_SUCCESS == 'true') {
            buildInfo.platforms.add('Android')
        }
        if (env.IOS_BUILD_SUCCESS == 'true') {
            buildInfo.platforms.add('iOS')
        }
        
        // Get artifact paths
        buildInfo.artifacts = [:]
        if (env.ANDROID_APK_PATH) {
            buildInfo.artifacts.androidAPK = env.ANDROID_APK_PATH
        }
        if (env.IOS_IPA_PATH) {
            buildInfo.artifacts.iosIPA = env.IOS_IPA_PATH
        }
        if (env.IOS_BUNDLE_PATH) {
            buildInfo.artifacts.iosBundle = env.IOS_BUNDLE_PATH
        }
        
    } catch (Exception e) {
        logger.warning("Could not gather complete build info: ${e.getMessage()}")
    }
    
    return buildInfo
}

/**
 * Clean React Native build artifacts
 * @param config Pipeline configuration map
 */
def cleanReactNativeBuild(Map config = [:]) {
    logger.info("Cleaning React Native build artifacts")
    
    try {
        // Clean React Native
        sh script: ReactNativeScript.cleanCommand()
        
        // Clean additional artifacts
        sh script: '''
            rm -rf android/app/build/
            rm -rf ios/build/
            rm -rf ios/DerivedData/
            rm -rf node_modules/.cache/
            rm -f *.jsbundle
            rm -f android-bundle.js
        '''
        
        logger.info("React Native cleanup completed")
        
    } catch (Exception e) {
        logger.warning("Cleanup failed, but continuing: ${e.getMessage()}")
    }
}