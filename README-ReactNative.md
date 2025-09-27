# React Native CI/CD Implementation

## 🚀 Overview

This implementation extends the existing shared library to support **React Native mobile applications** with comprehensive CI/CD capabilities for both **Android and iOS platforms**.

## 📱 Features

### **Supported Platforms**
- ✅ **Android** - APK builds with emulator testing
- ✅ **iOS** - Bundle generation (Linux) / IPA builds (macOS)
- ✅ **Parallel Builds** - Simultaneous Android and iOS builds

### **Testing Frameworks**
- **Unit Testing**: Jest with coverage reports
- **E2E Testing**: Detox, Appium, Maestro
- **Device Support**: Virtual devices (emulators/simulators) and real devices
- **Functional Testing**: Smoke, Sanity, Regression tests

### **Code Quality**
- **Linting**: ESLint, Prettier, TypeScript
- **Bundle Analysis**: React Native bundle size analysis
- **Quality Gates**: Configurable thresholds

### **Reporting**
- **Allure Reports**: Enhanced with mobile artifacts
- **JUnit Reports**: Test results in standard format
- **Coverage Reports**: Code coverage with LCOV format
- **Mobile Artifacts**: APK/IPA files, screenshots, device logs

## 📁 Files Created

### **1. Command Generator**
```
src/ReactNativeScript.groovy
```
- **Purpose**: Generate all React Native build, test, and lint commands
- **Features**: Android/iOS builds, device management, testing commands
- **Tools**: Supports Detox, Appium, Maestro, Jest, ESLint

### **2. Mobile Build Utilities**
```
vars/mobile_build.groovy
```
- **Purpose**: Handle React Native builds and dependency management
- **Features**: Parallel Android/iOS builds, artifact management
- **Outputs**: APK files, IPA files, JS bundles

### **3. Mobile Testing Framework**
```
vars/mobile_test.groovy
```
- **Purpose**: Comprehensive mobile testing with device management
- **Features**: Virtual/real device testing, parallel test execution
- **Artifacts**: Screenshots, logs, test videos

### **4. Pipeline Template**
```
vars/reactNative_template.groovy
```
- **Purpose**: Main CI pipeline orchestration
- **Features**: Complete CI flow with error handling and reporting
- **Stages**: Checkout → Setup → Dependencies → Lint → Build → Test → Report → Cleanup

### **5. Enhanced Reporting**
```
vars/sendReport.groovy (updated)
vars/core_build.groovy (updated)
```
- **Purpose**: Extended existing reporting for mobile artifacts
- **Features**: Mobile-specific test results and build artifacts

### **6. Configuration**
```
ci-config-react-native.yaml
```
- **Purpose**: Sample configuration for React Native projects
- **Features**: Platform settings, tool selection, quality gates

## 🔄 CI Flow (Line by Line)

```
1. 📥 Checkout Code
   └── Pull source code from repository

2. 🐳 Pull Docker Image
   └── React Native image with Android SDK + Node.js from Nexus

3. 🛠️ Setup Environment
   ├── Install Node.js dependencies (npm ci)
   ├── Install iOS dependencies (pod install) - parallel
   ├── Setup Android SDK environment
   └── Verify tool versions

4. 🔍 Code Quality (Lint)
   ├── ESLint for JavaScript/TypeScript
   ├── Prettier for code formatting
   └── TypeScript compilation check

5. 🏗️ Build Applications (Parallel)
   ├── Android Build
   │   ├── Generate Android bundle
   │   ├── Build APK (debug/release)
   │   └── Archive APK artifacts
   └── iOS Build
       ├── Generate iOS bundle (Linux)
       ├── Build IPA (macOS only)
       └── Archive iOS artifacts

6. 🧪 Test Execution (Parallel)
   ├── Unit Tests
   │   ├── Jest with coverage
   │   └── Generate JUnit reports
   ├── Android E2E Tests
   │   ├── Start Android emulator
   │   ├── Run Detox/Appium tests
   │   ├── Capture screenshots/logs
   │   └── Cleanup emulator
   └── iOS E2E Tests
       ├── Start iOS simulator (macOS)
       ├── Run Detox/Appium tests
       ├── Capture screenshots/logs
       └── Cleanup simulator

7. 📊 Generate Reports
   ├── Allure reports with mobile artifacts
   ├── Email notifications with build summary
   └── Slack notifications (if configured)

8. 🧹 Cleanup
   └── Remove build artifacts and temporary files
```

## 🛠️ Tools & Technologies

### **Free & Open Source Testing Tools**
1. **Detox** - React Native E2E testing (Recommended)
2. **Appium** - Cross-platform mobile automation
3. **Maestro** - Simple mobile UI testing
4. **Jest** - Unit testing with coverage

### **Device Testing Options**
1. **Virtual Devices** (Free)
   - Android Emulators
   - iOS Simulators (macOS only)
2. **Real Devices** (Free)
   - Connected Android devices via ADB
   - Connected iOS devices via Xcode
3. **Cloud Device Farms** (Paid/Free Tier)
   - AWS Device Farm
   - Firebase Test Lab

### **Docker Image Requirements**
```dockerfile
# Base image should include:
- Node.js 16+
- Android SDK
- Java 11
- npm/yarn
- ADB tools
- Emulator support
```

## ⚙️ Configuration

### **Basic Configuration**
```yaml
project_language: "react-native"
tool_for_unit_testing:
  react_native: "detox"  # jest, detox, appium, maestro
tool_for_lint_testing:
  react_native: "eslint"  # eslint, prettier, typescript
```

### **Platform Configuration**
```yaml
test_platforms:
  - android
  - ios
device_type: "virtual"  # virtual or real
android_avd: "Pixel_4_API_30"
ios_device: "iPhone 14"
```

### **Build Configuration**
```yaml
build_type: "debug"  # debug or release
build:
  android:
    enabled: true
    sign_apk: false
  ios:
    enabled: true
    sign_ipa: false
```

## 🚀 Usage

### **1. Pipeline Usage**
```groovy
// In your Jenkinsfile
@Library('your-shared-library') _

pipeline {
    agent any
    stages {
        stage('React Native CI') {
            steps {
                script {
                    // Load configuration
                    def config = readYaml file: 'ci-config-react-native.yaml'
                    
                    // Run React Native pipeline
                    reactNative_template(config)
                }
            }
        }
    }
}
```

### **2. Direct Function Usage**
```groovy
// Build React Native apps
mobile_build.buildReactNativeApp(config)

// Run mobile tests
mobile_test.runReactNativeTests(config)

// Install dependencies
mobile_build.installReactNativeDependencies(config)
```

## 📋 Prerequisites

### **Jenkins Setup**
1. **Plugins Required**:
   - Allure Plugin
   - Docker Pipeline Plugin
   - Pipeline Stage View Plugin
   - Email Extension Plugin

2. **Credentials**:
   - Nexus registry credentials
   - Email SMTP configuration
   - Slack webhook (optional)

### **Docker Image**
- React Native development image with Android SDK
- Available in your Nexus repository
- Includes Node.js, Android tools, Java

### **Project Structure**
```
your-react-native-project/
├── android/                 # Android project
├── ios/                     # iOS project
├── src/                     # React Native source
├── __tests__/               # Jest tests
├── e2e/                     # E2E tests (Detox)
├── package.json             # Dependencies
├── ci-config-react-native.yaml  # CI configuration
└── Jenkinsfile              # Pipeline definition
```

## 🎯 Benefits

### **For Development Teams**
- **Parallel Builds**: Faster CI/CD with simultaneous Android/iOS builds
- **Comprehensive Testing**: Unit, E2E, and functional testing
- **Quality Gates**: Automated code quality checks
- **Rich Reporting**: Detailed reports with mobile artifacts

### **For QA Teams**
- **Device Testing**: Support for both virtual and real devices
- **Visual Reports**: Screenshots and videos of test failures
- **Test History**: Track test stability over time
- **Multiple Frameworks**: Choose between Detox, Appium, or Maestro

### **For DevOps Teams**
- **Standardized Pipeline**: Consistent CI/CD across projects
- **Docker Integration**: Containerized builds with Nexus registry
- **Flexible Configuration**: YAML-based configuration management
- **Monitoring**: Comprehensive logging and error handling

## 🔧 Customization

### **Adding New Test Tools**
1. Update `ReactNativeScript.groovy` with new commands
2. Extend `mobile_test.groovy` with new test execution logic
3. Update configuration schema in `ci-config-react-native.yaml`

### **Platform-Specific Builds**
1. Modify `mobile_build.groovy` for custom build logic
2. Add platform-specific environment setup
3. Update artifact collection in reporting

### **Custom Reporting**
1. Extend `sendReport.groovy` for additional metrics
2. Add custom Allure categories
3. Integrate with external reporting tools

## 🐛 Troubleshooting

### **Common Issues**
1. **Emulator Start Failure**: Check Android SDK installation and AVD configuration
2. **iOS Build on Linux**: iOS builds are limited to bundle generation on Linux
3. **Dependency Installation**: Ensure package.json and Podfile are present
4. **Test Failures**: Check device connectivity and test configuration

### **Debug Commands**
```bash
# Check Android setup
adb devices
emulator -list-avds

# Check iOS setup (macOS)
xcrun simctl list devices

# Check React Native
npx react-native doctor
```

## 📈 Future Enhancements

1. **Cloud Device Integration**: AWS Device Farm, Firebase Test Lab
2. **Performance Testing**: App startup time, memory usage metrics
3. **Security Scanning**: Static analysis for mobile security
4. **App Store Integration**: Automated deployment to stores
5. **Visual Regression**: Screenshot comparison testing

This implementation provides a solid foundation for React Native CI/CD that can be extended based on specific project needs and requirements.