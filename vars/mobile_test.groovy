/**
 * Mobile Test Utilities - Handles React Native testing with Detox, Appium, and Jest
 * Supports both virtual devices (emulators/simulators) and real device testing
 * Provides parallel testing across Android and iOS platforms
 */

/**
 * Main method to run all React Native tests
 * @param config Pipeline configuration map
 * @return Boolean true if tests succeed, false if any fails
 */
def runReactNativeTests(Map config = [:]) {
    logger.info("Starting React Native test execution")
    
    def testResults = [:]
    def parallelTests = [:]
    
    // Get test configuration
    def testTool = getTestTool(config)
    def deviceType = config.device_type ?: 'virtual' // 'virtual' or 'real'
    
    logger.info("Test Tool: ${testTool}, Device Type: ${deviceType}")
    
    // Unit Tests (always run first)
    if (shouldRunUnitTests(config)) {
        parallelTests['Unit Tests'] = {
            testResults['unit'] = runUnitTests(config)
        }
    }
    
    // E2E Tests (platform-specific)
    if (shouldRunE2ETests(config)) {
        if (config.test_platforms?.contains('android') || !config.test_platforms) {
            parallelTests['Android E2E'] = {
                testResults['android_e2e'] = runAndroidE2ETests(config, testTool, deviceType)
            }
        }
        
        if (config.test_platforms?.contains('ios') || !config.test_platforms) {
            parallelTests['iOS E2E'] = {
                testResults['ios_e2e'] = runIOSE2ETests(config, testTool, deviceType)
            }
        }
    }
    
    // Execute parallel tests
    if (parallelTests.size() > 0) {
        parallel parallelTests
    }
    
    // Analyze results
    def overallSuccess = analyzeTestResults(testResults)
    
    logger.info("Test Results Summary: ${testResults}")
    return overallSuccess
}

/**
 * Run Jest unit tests
 * @param config Pipeline configuration map
 * @return Boolean true if tests pass
 */
def runUnitTests(Map config = [:]) {
    logger.info("Running React Native unit tests with Jest")
    
    try {
        // Run Jest with coverage and JUnit output
        sh script: ReactNativeScript.testCommand('jest')
        
        // Verify test results exist
        if (fileExists('coverage/lcov.info')) {
            logger.info("Coverage report generated")
            archiveArtifacts artifacts: 'coverage/**/*', allowEmptyArchive: true
        }
        
        if (fileExists('junit.xml')) {
            logger.info("JUnit test results generated")
            publishTestResults testResultsPattern: 'junit.xml'
        }
        
        env.UNIT_TEST_RESULT = 'SUCCESS'
        return true
        
    } catch (Exception e) {
        logger.error("Unit tests failed: ${e.getMessage()}")
        env.UNIT_TEST_RESULT = 'FAILED'
        return false
    }
}

/**
 * Run Android E2E tests
 * @param config Pipeline configuration map
 * @param testTool Test framework to use
 * @param deviceType 'virtual' or 'real'
 * @return Boolean true if tests pass
 */
def runAndroidE2ETests(Map config, String testTool, String deviceType) {
    logger.info("Running Android E2E tests with ${testTool} on ${deviceType} devices")
    
    try {
        if (deviceType == 'virtual') {
            return runAndroidVirtualDeviceTests(config, testTool)
        } else {
            return runAndroidRealDeviceTests(config, testTool)
        }
    } catch (Exception e) {
        logger.error("Android E2E tests failed: ${e.getMessage()}")
        env.ANDROID_E2E_RESULT = 'FAILED'
        return false
    }
}

/**
 * Run iOS E2E tests
 * @param config Pipeline configuration map
 * @param testTool Test framework to use
 * @param deviceType 'virtual' or 'real'
 * @return Boolean true if tests pass
 */
def runIOSE2ETests(Map config, String testTool, String deviceType) {
    logger.info("Running iOS E2E tests with ${testTool} on ${deviceType} devices")
    
    try {
        // Check if we're on macOS for iOS testing
        def isMacOS = sh(script: "uname", returnStdout: true).trim() == "Darwin"
        
        if (!isMacOS) {
            logger.warning("iOS E2E tests skipped - not running on macOS")
            env.IOS_E2E_RESULT = 'SKIPPED'
            return true
        }
        
        if (deviceType == 'virtual') {
            return runIOSVirtualDeviceTests(config, testTool)
        } else {
            return runIOSRealDeviceTests(config, testTool)
        }
    } catch (Exception e) {
        logger.error("iOS E2E tests failed: ${e.getMessage()}")
        env.IOS_E2E_RESULT = 'FAILED'
        return false
    }
}

/**
 * Run Android tests on virtual devices (emulators)
 * @param config Pipeline configuration map
 * @param testTool Test framework to use
 * @return Boolean true if tests pass
 */
def runAndroidVirtualDeviceTests(Map config, String testTool) {
    logger.info("Setting up Android emulator for testing")
    
    try {
        // Setup and start emulator
        setupAndroidEmulator(config)
        
        // Wait for emulator to be ready
        waitForAndroidEmulator()
        
        // Run tests based on tool
        def testCommand = ""
        switch(testTool) {
            case 'detox':
                testCommand = ReactNativeScript.detoxAndroidTestCommand('android.emu.debug')
                break
            case 'appium':
                testCommand = ReactNativeScript.appiumTestCommand('android')
                break
            case 'maestro':
                testCommand = ReactNativeScript.maestroTestCommand('maestro/android-flows')
                break
            default:
                throw new IllegalArgumentException("Unsupported test tool: ${testTool}")
        }
        
        sh script: testCommand
        
        // Capture screenshots and logs
        captureAndroidTestArtifacts()
        
        env.ANDROID_E2E_RESULT = 'SUCCESS'
        return true
        
    } catch (Exception e) {
        logger.error("Android virtual device tests failed: ${e.getMessage()}")
        captureAndroidTestArtifacts() // Capture artifacts even on failure
        env.ANDROID_E2E_RESULT = 'FAILED'
        return false
    } finally {
        // Cleanup emulator
        cleanupAndroidEmulator()
    }
}

/**
 * Run iOS tests on virtual devices (simulators)
 * @param config Pipeline configuration map
 * @param testTool Test framework to use
 * @return Boolean true if tests pass
 */
def runIOSVirtualDeviceTests(Map config, String testTool) {
    logger.info("Setting up iOS simulator for testing")
    
    try {
        // Setup and start simulator
        setupIOSSimulator(config)
        
        // Wait for simulator to be ready
        waitForIOSSimulator()
        
        // Run tests based on tool
        def testCommand = ""
        switch(testTool) {
            case 'detox':
                testCommand = ReactNativeScript.detoxIOSTestCommand('ios.sim.debug')
                break
            case 'appium':
                testCommand = ReactNativeScript.appiumTestCommand('ios')
                break
            case 'maestro':
                testCommand = ReactNativeScript.maestroTestCommand('maestro/ios-flows')
                break
            default:
                throw new IllegalArgumentException("Unsupported test tool: ${testTool}")
        }
        
        sh script: testCommand
        
        // Capture screenshots and logs
        captureIOSTestArtifacts()
        
        env.IOS_E2E_RESULT = 'SUCCESS'
        return true
        
    } catch (Exception e) {
        logger.error("iOS virtual device tests failed: ${e.getMessage()}")
        captureIOSTestArtifacts() // Capture artifacts even on failure
        env.IOS_E2E_RESULT = 'FAILED'
        return false
    } finally {
        // Cleanup simulator
        cleanupIOSSimulator()
    }
}

/**
 * Run Android tests on real devices
 * @param config Pipeline configuration map
 * @param testTool Test framework to use
 * @return Boolean true if tests pass
 */
def runAndroidRealDeviceTests(Map config, String testTool) {
    logger.info("Running Android tests on real devices")
    
    try {
        // Check for connected devices
        def devices = sh(script: "adb devices | grep -v 'List of devices' | grep 'device\$' | wc -l", returnStdout: true).trim()
        
        if (devices.toInteger() == 0) {
            logger.warning("No Android devices connected - skipping real device tests")
            env.ANDROID_E2E_RESULT = 'SKIPPED'
            return true
        }
        
        logger.info("Found ${devices} Android device(s) connected")
        
        // Run tests on real devices
        def testCommand = ""
        switch(testTool) {
            case 'detox':
                testCommand = ReactNativeScript.detoxAndroidTestCommand('android.device.debug')
                break
            case 'appium':
                testCommand = ReactNativeScript.appiumTestCommand('android')
                break
            case 'maestro':
                testCommand = ReactNativeScript.maestroTestCommand('maestro/android-flows')
                break
            default:
                throw new IllegalArgumentException("Unsupported test tool: ${testTool}")
        }
        
        sh script: testCommand
        
        // Capture test artifacts
        captureAndroidTestArtifacts()
        
        env.ANDROID_E2E_RESULT = 'SUCCESS'
        return true
        
    } catch (Exception e) {
        logger.error("Android real device tests failed: ${e.getMessage()}")
        env.ANDROID_E2E_RESULT = 'FAILED'
        return false
    }
}

/**
 * Run iOS tests on real devices
 * @param config Pipeline configuration map
 * @param testTool Test framework to use
 * @return Boolean true if tests pass
 */
def runIOSRealDeviceTests(Map config, String testTool) {
    logger.info("Running iOS tests on real devices")
    
    try {
        // Check for connected devices
        def devices = sh(script: "xcrun simctl list devices | grep 'Booted' | wc -l", returnStdout: true).trim()
        
        if (devices.toInteger() == 0) {
            logger.warning("No iOS devices connected - skipping real device tests")
            env.IOS_E2E_RESULT = 'SKIPPED'
            return true
        }
        
        logger.info("Found ${devices} iOS device(s) connected")
        
        // Run tests on real devices
        def testCommand = ""
        switch(testTool) {
            case 'detox':
                testCommand = ReactNativeScript.detoxIOSTestCommand('ios.device.debug')
                break
            case 'appium':
                testCommand = ReactNativeScript.appiumTestCommand('ios')
                break
            case 'maestro':
                testCommand = ReactNativeScript.maestroTestCommand('maestro/ios-flows')
                break
            default:
                throw new IllegalArgumentException("Unsupported test tool: ${testTool}")
        }
        
        sh script: testCommand
        
        // Capture test artifacts
        captureIOSTestArtifacts()
        
        env.IOS_E2E_RESULT = 'SUCCESS'
        return true
        
    } catch (Exception e) {
        logger.error("iOS real device tests failed: ${e.getMessage()}")
        env.IOS_E2E_RESULT = 'FAILED'
        return false
    }
}

// ========== DEVICE MANAGEMENT ==========

/**
 * Setup Android emulator
 * @param config Pipeline configuration map
 */
def setupAndroidEmulator(Map config) {
    def avdName = config.android_avd ?: 'Pixel_4_API_30'
    
    logger.info("Starting Android emulator: ${avdName}")
    
    // List available AVDs
    sh script: ReactNativeScript.listAndroidEmulatorsCommand()
    
    // Start emulator
    sh script: ReactNativeScript.startAndroidEmulatorCommand(avdName)
}

/**
 * Setup iOS simulator
 * @param config Pipeline configuration map
 */
def setupIOSSimulator(Map config) {
    def deviceType = config.ios_device ?: 'iPhone 14'
    
    logger.info("Starting iOS simulator: ${deviceType}")
    
    // List available simulators
    sh script: ReactNativeScript.listIOSSimulatorsCommand()
    
    // Start simulator
    sh script: ReactNativeScript.startIOSSimulatorCommand(deviceType)
}

/**
 * Wait for Android emulator to be ready
 */
def waitForAndroidEmulator() {
    logger.info("Waiting for Android emulator to be ready...")
    
    timeout(time: 5, unit: 'MINUTES') {
        sh script: '''
            while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
                echo "Waiting for emulator to boot..."
                sleep 10
            done
            echo "Android emulator is ready!"
        '''
    }
}

/**
 * Wait for iOS simulator to be ready
 */
def waitForIOSSimulator() {
    logger.info("Waiting for iOS simulator to be ready...")
    
    timeout(time: 3, unit: 'MINUTES') {
        sh script: '''
            while ! xcrun simctl list devices | grep "Booted" > /dev/null; do
                echo "Waiting for simulator to boot..."
                sleep 10
            done
            echo "iOS simulator is ready!"
        '''
    }
}

// ========== ARTIFACT CAPTURE ==========

/**
 * Capture Android test artifacts (screenshots, logs)
 */
def captureAndroidTestArtifacts() {
    logger.info("Capturing Android test artifacts")
    
    try {
        // Create artifacts directory
        sh script: "mkdir -p test-artifacts/android"
        
        // Capture screenshots
        sh script: "adb exec-out screencap -p > test-artifacts/android/screenshot.png || true"
        
        // Capture logcat
        sh script: "adb logcat -d > test-artifacts/android/logcat.txt || true"
        
        // Capture Detox artifacts if they exist
        sh script: "cp -r artifacts/* test-artifacts/android/ 2>/dev/null || true"
        
        // Archive artifacts
        archiveArtifacts artifacts: 'test-artifacts/android/**/*', allowEmptyArchive: true
        
    } catch (Exception e) {
        logger.warning("Could not capture all Android artifacts: ${e.getMessage()}")
    }
}

/**
 * Capture iOS test artifacts (screenshots, logs)
 */
def captureIOSTestArtifacts() {
    logger.info("Capturing iOS test artifacts")
    
    try {
        // Create artifacts directory
        sh script: "mkdir -p test-artifacts/ios"
        
        // Capture simulator screenshot
        sh script: "xcrun simctl io booted screenshot test-artifacts/ios/screenshot.png || true"
        
        // Capture system logs
        sh script: "xcrun simctl spawn booted log collect --output test-artifacts/ios/system.logarchive || true"
        
        // Capture Detox artifacts if they exist
        sh script: "cp -r artifacts/* test-artifacts/ios/ 2>/dev/null || true"
        
        // Archive artifacts
        archiveArtifacts artifacts: 'test-artifacts/ios/**/*', allowEmptyArchive: true
        
    } catch (Exception e) {
        logger.warning("Could not capture all iOS artifacts: ${e.getMessage()}")
    }
}

// ========== CLEANUP ==========

/**
 * Cleanup Android emulator
 */
def cleanupAndroidEmulator() {
    logger.info("Cleaning up Android emulator")
    
    try {
        sh script: "adb emu kill || true"
        sh script: "pkill -f emulator || true"
    } catch (Exception e) {
        logger.warning("Emulator cleanup failed: ${e.getMessage()}")
    }
}

/**
 * Cleanup iOS simulator
 */
def cleanupIOSSimulator() {
    logger.info("Cleaning up iOS simulator")
    
    try {
        sh script: "xcrun simctl shutdown all || true"
    } catch (Exception e) {
        logger.warning("Simulator cleanup failed: ${e.getMessage()}")
    }
}

// ========== HELPER METHODS ==========

/**
 * Get test tool from configuration
 * @param config Pipeline configuration map
 * @return String test tool name
 */
def getTestTool(Map config) {
    return config.tool_for_unit_testing?.react_native ?: 'detox'
}

/**
 * Check if unit tests should run
 * @param config Pipeline configuration map
 * @return Boolean true if unit tests should run
 */
def shouldRunUnitTests(Map config) {
    return config.stages?.unittest != false
}

/**
 * Check if E2E tests should run
 * @param config Pipeline configuration map
 * @return Boolean true if E2E tests should run
 */
def shouldRunE2ETests(Map config) {
    return config.stages?.e2e_test != false
}

/**
 * Analyze test results and determine overall success
 * @param testResults Map of test results
 * @return Boolean true if overall tests passed
 */
def analyzeTestResults(Map testResults) {
    def unitSuccess = testResults['unit'] ?: true
    def androidE2ESuccess = testResults['android_e2e'] ?: true
    def iosE2ESuccess = testResults['ios_e2e'] ?: true
    
    // Overall success if unit tests pass and at least one E2E platform passes
    def overallSuccess = unitSuccess && (androidE2ESuccess || iosE2ESuccess)
    
    logger.info("Test Analysis - Unit: ${unitSuccess}, Android E2E: ${androidE2ESuccess}, iOS E2E: ${iosE2ESuccess}")
    logger.info("Overall Test Result: ${overallSuccess ? 'SUCCESS' : 'FAILED'}")
    
    return overallSuccess
}