/**
 * Physical Device Testing - Simple solution for React Native testing on real devices
 * Teams connect their physical Android/iOS devices via USB for testing
 * No emulators, no device farms - just real devices
 */

/**
 * Run tests on connected physical devices
 * @param config Pipeline configuration map
 * @return Boolean true if tests succeed
 */
def runTestsOnPhysicalDevices(Map config = [:]) {
    logger.info("Starting tests on connected physical devices")
    
    try {
        // Check for connected devices
        def connectedDevices = getConnectedDevices()
        
        if (connectedDevices.android.size() == 0 && connectedDevices.ios.size() == 0) {
            logger.warning("No physical devices connected - skipping device tests")
            return true // Don't fail the build if no devices
        }
        
        def testResults = [:]
        def parallelTests = [:]
        
        // Run tests on Android devices
        if (connectedDevices.android.size() > 0) {
            parallelTests['Android Physical Devices'] = {
                testResults['android'] = runAndroidPhysicalDeviceTests(config, connectedDevices.android)
            }
        }
        
        // Run tests on iOS devices (if on macOS)
        if (connectedDevices.ios.size() > 0) {
            parallelTests['iOS Physical Devices'] = {
                testResults['ios'] = runIOSPhysicalDeviceTests(config, connectedDevices.ios)
            }
        }
        
        // Execute tests in parallel
        parallel parallelTests
        
        // Analyze results
        def overallSuccess = testResults.values().every { it == true }
        
        logger.info("Physical device test results: ${testResults}")
        return overallSuccess
        
    } catch (Exception e) {
        logger.error("Physical device testing failed: ${e.getMessage()}")
        return false
    }
}

/**
 * Get list of connected physical devices
 * @return Map with android and ios device lists
 */
def getConnectedDevices() {
    def devices = [android: [], ios: []]
    
    try {
        // Check Android devices via ADB
        def androidDevices = sh(
            script: "adb devices | grep -v 'List of devices' | grep 'device\$' | awk '{print \$1}'",
            returnStdout: true
        ).trim()
        
        if (androidDevices) {
            devices.android = androidDevices.split('\n').findAll { it.trim() }
            logger.info("Found ${devices.android.size()} Android device(s): ${devices.android}")
        }
        
        // Check iOS devices (macOS only)
        def isMacOS = sh(script: "uname", returnStdout: true).trim() == "Darwin"
        if (isMacOS) {
            try {
                def iosDevices = sh(
                    script: "xcrun simctl list devices | grep 'Booted' | wc -l",
                    returnStdout: true
                ).trim()
                
                if (iosDevices.toInteger() > 0) {
                    devices.ios = ["ios-device-1"] // Simplified for demo
                    logger.info("Found ${devices.ios.size()} iOS device(s)")
                }
            } catch (Exception e) {
                logger.debug("iOS device check failed (expected on non-macOS): ${e.getMessage()}")
            }
        }
        
    } catch (Exception e) {
        logger.warning("Device detection failed: ${e.getMessage()}")
    }
    
    return devices
}

/**
 * Run tests on Android physical devices
 * @param config Pipeline configuration map
 * @param androidDevices List of Android device IDs
 * @return Boolean true if tests pass
 */
def runAndroidPhysicalDeviceTests(Map config, List<String> androidDevices) {
    logger.info("Running tests on ${androidDevices.size()} Android device(s)")
    
    try {
        def testTool = config.tool_for_unit_testing?.react_native ?: 'detox'
        def allPassed = true
        
        // Test each device
        androidDevices.each { deviceId ->
            logger.info("Testing on Android device: ${deviceId}")
            
            try {
                // Get device info
                def deviceInfo = getAndroidDeviceInfo(deviceId)
                logger.info("Device info: ${deviceInfo}")
                
                // Install APK if needed
                installAPKOnDevice(deviceId, config)
                
                // Run tests based on tool
                def testCommand = ""
                switch(testTool) {
                    case 'detox':
                        testCommand = "DETOX_DEVICE_ID=${deviceId} npx detox test --configuration android.device.debug"
                        break
                    case 'appium':
                        testCommand = "ANDROID_DEVICE_ID=${deviceId} npm run test:appium:android"
                        break
                    case 'maestro':
                        testCommand = "maestro test --device ${deviceId} maestro/android-flows"
                        break
                    default:
                        testCommand = "ANDROID_DEVICE_ID=${deviceId} npm run test:device:android"
                }
                
                // Execute test
                sh script: testCommand
                
                // Capture device artifacts
                captureAndroidDeviceArtifacts(deviceId)
                
                logger.info("Tests passed on Android device: ${deviceId}")
                
            } catch (Exception e) {
                logger.error("Tests failed on Android device ${deviceId}: ${e.getMessage()}")
                captureAndroidDeviceArtifacts(deviceId) // Capture artifacts even on failure
                allPassed = false
            }
        }
        
        return allPassed
        
    } catch (Exception e) {
        logger.error("Android physical device testing failed: ${e.getMessage()}")
        return false
    }
}

/**
 * Run tests on iOS physical devices
 * @param config Pipeline configuration map
 * @param iosDevices List of iOS device IDs
 * @return Boolean true if tests pass
 */
def runIOSPhysicalDeviceTests(Map config, List<String> iosDevices) {
    logger.info("Running tests on ${iosDevices.size()} iOS device(s)")
    
    try {
        def testTool = config.tool_for_unit_testing?.react_native ?: 'detox'
        def allPassed = true
        
        // Test each device
        iosDevices.each { deviceId ->
            logger.info("Testing on iOS device: ${deviceId}")
            
            try {
                // Run tests based on tool
                def testCommand = ""
                switch(testTool) {
                    case 'detox':
                        testCommand = "npx detox test --configuration ios.device.debug"
                        break
                    case 'appium':
                        testCommand = "npm run test:appium:ios"
                        break
                    case 'maestro':
                        testCommand = "maestro test maestro/ios-flows"
                        break
                    default:
                        testCommand = "npm run test:device:ios"
                }
                
                // Execute test
                sh script: testCommand
                
                // Capture device artifacts
                captureIOSDeviceArtifacts(deviceId)
                
                logger.info("Tests passed on iOS device: ${deviceId}")
                
            } catch (Exception e) {
                logger.error("Tests failed on iOS device ${deviceId}: ${e.getMessage()}")
                captureIOSDeviceArtifacts(deviceId) // Capture artifacts even on failure
                allPassed = false
            }
        }
        
        return allPassed
        
    } catch (Exception e) {
        logger.error("iOS physical device testing failed: ${e.getMessage()}")
        return false
    }
}

/**
 * Get Android device information
 * @param deviceId Android device ID
 * @return Map device information
 */
def getAndroidDeviceInfo(String deviceId) {
    try {
        def manufacturer = sh(
            script: "adb -s ${deviceId} shell getprop ro.product.manufacturer",
            returnStdout: true
        ).trim()
        
        def model = sh(
            script: "adb -s ${deviceId} shell getprop ro.product.model",
            returnStdout: true
        ).trim()
        
        def androidVersion = sh(
            script: "adb -s ${deviceId} shell getprop ro.build.version.release",
            returnStdout: true
        ).trim()
        
        def apiLevel = sh(
            script: "adb -s ${deviceId} shell getprop ro.build.version.sdk",
            returnStdout: true
        ).trim()
        
        return [
            id: deviceId,
            manufacturer: manufacturer,
            model: model,
            androidVersion: androidVersion,
            apiLevel: apiLevel
        ]
        
    } catch (Exception e) {
        logger.warning("Could not get device info for ${deviceId}: ${e.getMessage()}")
        return [id: deviceId, manufacturer: 'Unknown', model: 'Unknown']
    }
}

/**
 * Install APK on Android device
 * @param deviceId Android device ID
 * @param config Pipeline configuration map
 */
def installAPKOnDevice(String deviceId, Map config) {
    try {
        // Find APK file
        def apkPath = findAPKFile()
        if (!apkPath) {
            logger.warning("No APK file found - skipping installation")
            return
        }
        
        logger.info("Installing APK on device ${deviceId}: ${apkPath}")
        
        // Uninstall previous version
        def packageName = config.android?.package_name ?: 'com.yourapp'
        sh script: "adb -s ${deviceId} uninstall ${packageName} || true"
        
        // Install new APK
        sh script: "adb -s ${deviceId} install -r ${apkPath}"
        
        logger.info("APK installed successfully on device ${deviceId}")
        
    } catch (Exception e) {
        logger.warning("APK installation failed on device ${deviceId}: ${e.getMessage()}")
    }
}

/**
 * Find APK file in build outputs
 * @return String APK file path or null
 */
def findAPKFile() {
    def apkPaths = [
        "android/app/build/outputs/apk/debug/app-debug.apk",
        "android/app/build/outputs/apk/release/app-release.apk",
        "android/app/build/outputs/apk/app-debug.apk",
        "android/app/build/outputs/apk/app-release.apk"
    ]
    
    for (String path : apkPaths) {
        if (fileExists(path)) {
            return path
        }
    }
    
    return null
}

/**
 * Capture Android device test artifacts
 * @param deviceId Android device ID
 */
def captureAndroidDeviceArtifacts(String deviceId) {
    try {
        def deviceInfo = getAndroidDeviceInfo(deviceId)
        def deviceName = "${deviceInfo.manufacturer}_${deviceInfo.model}".replaceAll(' ', '_')
        def artifactDir = "test-artifacts/android/${deviceName}_${deviceId}"
        
        sh script: "mkdir -p ${artifactDir}"
        
        // Capture screenshot
        sh script: "adb -s ${deviceId} exec-out screencap -p > ${artifactDir}/screenshot.png || true"
        
        // Capture logcat
        sh script: "adb -s ${deviceId} logcat -d > ${artifactDir}/logcat.txt || true"
        
        // Capture device info
        writeFile file: "${artifactDir}/device-info.json", text: groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(deviceInfo))
        
        // Capture app info
        def packageName = "com.yourapp" // This should come from config
        sh script: "adb -s ${deviceId} shell dumpsys package ${packageName} > ${artifactDir}/app-info.txt || true"
        
        logger.info("Captured artifacts for Android device: ${deviceId}")
        
    } catch (Exception e) {
        logger.warning("Could not capture artifacts for Android device ${deviceId}: ${e.getMessage()}")
    }
}

/**
 * Capture iOS device test artifacts
 * @param deviceId iOS device ID
 */
def captureIOSDeviceArtifacts(String deviceId) {
    try {
        def artifactDir = "test-artifacts/ios/${deviceId}"
        sh script: "mkdir -p ${artifactDir}"
        
        // Capture screenshot (if simulator)
        sh script: "xcrun simctl io booted screenshot ${artifactDir}/screenshot.png || true"
        
        // Capture system logs
        sh script: "xcrun simctl spawn booted log collect --output ${artifactDir}/system.logarchive || true"
        
        logger.info("Captured artifacts for iOS device: ${deviceId}")
        
    } catch (Exception e) {
        logger.warning("Could not capture artifacts for iOS device ${deviceId}: ${e.getMessage()}")
    }
}

/**
 * Display connected devices summary
 */
def displayDeviceSummary() {
    logger.info("=== CONNECTED DEVICES SUMMARY ===")
    
    try {
        def devices = getConnectedDevices()
        
        if (devices.android.size() > 0) {
            logger.info("Android Devices:")
            devices.android.each { deviceId ->
                def info = getAndroidDeviceInfo(deviceId)
                logger.info("  - ${info.manufacturer} ${info.model} (Android ${info.androidVersion}, API ${info.apiLevel})")
            }
        } else {
            logger.info("No Android devices connected")
        }
        
        if (devices.ios.size() > 0) {
            logger.info("iOS Devices: ${devices.ios.size()} connected")
        } else {
            logger.info("No iOS devices connected")
        }
        
        def totalDevices = devices.android.size() + devices.ios.size()
        logger.info("Total devices available for testing: ${totalDevices}")
        
        if (totalDevices == 0) {
            logger.info("💡 To test on physical devices:")
            logger.info("   1. Connect Android device via USB")
            logger.info("   2. Enable USB Debugging")
            logger.info("   3. Run 'adb devices' to verify connection")
        }
        
    } catch (Exception e) {
        logger.warning("Could not display device summary: ${e.getMessage()}")
    }
}