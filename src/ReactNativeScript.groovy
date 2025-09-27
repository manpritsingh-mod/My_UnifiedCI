/**
 * React Native Script - Creates React Native commands for mobile CI/CD operations
 * Provides standardized React Native commands for Android and iOS builds
 * Supports both virtual and real device testing with Detox and Appium
 */
class ReactNativeScript {
    
    // ========== DEPENDENCY MANAGEMENT ==========
    
    /**
     * Install Node.js dependencies
     * @return String npm command for installing dependencies
     */
    static String installDependenciesCommand() {
        return 'npm ci'
    }
    
    /**
     * Install iOS dependencies (CocoaPods)
     * @return String CocoaPods install command
     */
    static String installIOSDependenciesCommand() {
        return 'cd ios && pod install --repo-update && cd ..'
    }
    
    // ========== BUILD COMMANDS ==========
    
    /**
     * Build Android APK (Debug)
     * @return String command to build Android debug APK
     */
    static String buildAndroidDebugCommand() {
        return 'npx react-native build-android --mode=debug'
    }
    
    /**
     * Build Android APK (Release)
     * @return String command to build Android release APK
     */
    static String buildAndroidReleaseCommand() {
        return 'npx react-native build-android --mode=release'
    }
    
    /**
     * Build iOS IPA (Debug) - Simulator
     * @return String command to build iOS for simulator
     */
    static String buildIOSDebugCommand() {
        return 'npx react-native build-ios --mode=Debug --simulator'
    }
    
    /**
     * Build iOS IPA (Release) - Device
     * @return String command to build iOS for device
     */
    static String buildIOSReleaseCommand() {
        return 'npx react-native build-ios --mode=Release'
    }
    
    /**
     * Alternative Android build using Gradle directly
     * @param buildType Build type ('debug' or 'release')
     * @return String Gradle command for Android build
     */
    static String buildAndroidGradleCommand(String buildType = 'debug') {
        return "cd android && ./gradlew assemble${buildType.capitalize()} && cd .."
    }
    
    /**
     * Alternative iOS build using Xcode directly
     * @param scheme Build scheme (usually app name)
     * @param configuration Build configuration ('Debug' or 'Release')
     * @return String Xcode build command
     */
    static String buildIOSXcodeCommand(String scheme = 'YourApp', String configuration = 'Debug') {
        return "cd ios && xcodebuild -workspace ${scheme}.xcworkspace -scheme ${scheme} -configuration ${configuration} -sdk iphonesimulator -derivedDataPath build && cd .."
    }
    
    // ========== TESTING COMMANDS ==========
    
    /**
     * Run unit tests with Jest
     * @param testTool Test framework ('jest', 'detox', 'appium')
     * @return String test command
     */
    static String testCommand(String testTool = 'jest') {
        switch(testTool) {
            case 'jest':
                return "npm test -- --coverage --watchAll=false --testResultsProcessor=jest-junit"
            case 'detox':
                return "npx detox test --configuration ios.sim.debug"
            case 'appium':
                return "npm run test:appium"
            default:
                return "npm test -- --watchAll=false"
        }
    }
    
    /**
     * Run Detox E2E tests on iOS simulator
     * @param configuration Detox configuration ('ios.sim.debug', 'ios.sim.release')
     * @return String Detox iOS test command
     */
    static String detoxIOSTestCommand(String configuration = 'ios.sim.debug') {
        return "npx detox test --configuration ${configuration}"
    }
    
    /**
     * Run Detox E2E tests on Android emulator
     * @param configuration Detox configuration ('android.emu.debug', 'android.emu.release')
     * @return String Detox Android test command
     */
    static String detoxAndroidTestCommand(String configuration = 'android.emu.debug') {
        return "npx detox test --configuration ${configuration}"
    }
    
    /**
     * Run Appium tests
     * @param platform Platform to test ('android' or 'ios')
     * @return String Appium test command
     */
    static String appiumTestCommand(String platform = 'android') {
        return "npm run test:appium:${platform}"
    }
    
    /**
     * Run Maestro tests
     * @param flowFile Maestro flow file path
     * @return String Maestro test command
     */
    static String maestroTestCommand(String flowFile = 'maestro/flows') {
        return "maestro test ${flowFile}"
    }
    
    // ========== LINT COMMANDS ==========
    
    /**
     * Run ESLint for React Native
     * @param lintTool Lint tool ('eslint', 'prettier', 'typescript')
     * @return String lint command
     */
    static String lintCommand(String lintTool = 'eslint') {
        switch(lintTool) {
            case 'eslint': 
                return "npx eslint . --ext .js,.jsx,.ts,.tsx --format junit --output-file eslint-report.xml"
            case 'prettier': 
                return "npx prettier --check ."
            case 'typescript':
                return "npx tsc --noEmit"
            default: 
                throw new IllegalArgumentException("Unknown lint tool: ${lintTool}. Supported: eslint, prettier, typescript")
        }
    }
    
    // ========== DEVICE MANAGEMENT ==========
    
    /**
     * Start Android emulator
     * @param avdName AVD name
     * @return String command to start Android emulator
     */
    static String startAndroidEmulatorCommand(String avdName = 'Pixel_4_API_30') {
        return "emulator -avd ${avdName} -no-window -no-audio -no-boot-anim &"
    }
    
    /**
     * Start iOS simulator
     * @param deviceType iOS device type
     * @return String command to start iOS simulator
     */
    static String startIOSSimulatorCommand(String deviceType = 'iPhone 14') {
        return "xcrun simctl boot '${deviceType}'"
    }
    
    /**
     * List available Android emulators
     * @return String command to list Android AVDs
     */
    static String listAndroidEmulatorsCommand() {
        return "emulator -list-avds"
    }
    
    /**
     * List available iOS simulators
     * @return String command to list iOS simulators
     */
    static String listIOSSimulatorsCommand() {
        return "xcrun simctl list devices available"
    }
    
    // ========== ENVIRONMENT SETUP ==========
    
    /**
     * Setup Android environment
     * @return String command to setup Android SDK
     */
    static String setupAndroidEnvironmentCommand() {
        return '''
            export ANDROID_HOME=/opt/android-sdk
            export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
            sdkmanager --update
            sdkmanager "platform-tools" "platforms;android-30" "build-tools;30.0.3"
        '''
    }
    
    /**
     * Setup iOS environment (macOS only)
     * @return String command to setup iOS development
     */
    static String setupIOSEnvironmentCommand() {
        return '''
            sudo xcode-select --install
            gem install cocoapods
            pod setup
        '''
    }
    
    // ========== VERSION COMMANDS ==========
    
    /**
     * Check React Native and related tool versions
     * @return String command to display versions
     */
    static String versionCommand() {
        return '''
            echo "=== React Native Environment ==="
            node --version
            npm --version
            npx react-native --version
            echo "=== Android Environment ==="
            adb --version || echo "ADB not found"
            echo "=== iOS Environment ==="
            xcodebuild -version || echo "Xcode not found (Linux environment)"
        '''
    }
    
    // ========== BUNDLE ANALYSIS ==========
    
    /**
     * Analyze bundle size
     * @return String command to analyze React Native bundle
     */
    static String bundleAnalyzeCommand() {
        return "npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output android-bundle.js --analyze"
    }
    
    /**
     * Generate bundle for Android
     * @return String command to generate Android bundle
     */
    static String generateAndroidBundleCommand() {
        return "npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output android/app/src/main/assets/index.android.bundle"
    }
    
    /**
     * Generate bundle for iOS
     * @return String command to generate iOS bundle
     */
    static String generateIOSBundleCommand() {
        return "npx react-native bundle --platform ios --dev false --entry-file index.js --bundle-output ios/main.jsbundle"
    }
    
    // ========== FUNCTIONAL TEST COMMANDS ==========
    
    /**
     * Run smoke tests
     * @return String command for smoke tests
     */
    static String smokeTestCommand() {
        return "npm run test:smoke"
    }
    
    /**
     * Run sanity tests
     * @return String command for sanity tests
     */
    static String sanityTestCommand() {
        return "npm run test:sanity"
    }
    
    /**
     * Run regression tests
     * @return String command for regression tests
     */
    static String regressionTestCommand() {
        return "npm run test:regression"
    }
    
    // ========== CLEANUP COMMANDS ==========
    
    /**
     * Clean React Native project
     * @return String command to clean project
     */
    static String cleanCommand() {
        return '''
            npx react-native clean
            cd android && ./gradlew clean && cd ..
            cd ios && xcodebuild clean && cd ..
            rm -rf node_modules/.cache
        '''
    }
    
    /**
     * Reset Metro cache
     * @return String command to reset Metro bundler cache
     */
    static String resetCacheCommand() {
        return "npx react-native start --reset-cache"
    }
}