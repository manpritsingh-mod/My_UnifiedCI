/**
 * Enhanced Java Gradle Template - Pulls pre-built images from Nexus
 * Usage: javaGradle_docker_template([gradle_version: '7.6.1', run_tests: true])
 */
def call(Map config = [:]) {
    logger.info("🐘 Starting Java Gradle Pipeline with Nexus Docker Images")
    
    config = setupDockerConfig(config)
    def stageResults = [:]
    
    stage('Checkout') {
        script {
            logger.info("📥 CHECKOUT STAGE")
            core_github.checkout()
            stageResults['Checkout'] = 'SUCCESS'
        }
    }
    
    stage('Pull Gradle Image from Nexus') {
        script {
            logger.info("📦 PULLING GRADLE IMAGE FROM NEXUS")
            
            def imagePath = "${config.nexus.registry}/${config.nexus.project}/gradle:${config.gradle_version}"
            logger.info("🔍 Image: ${imagePath}")
            
            docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
                def image = docker.image(imagePath)
                
                logger.info("⬇️ Pulling image...")
                image.pull()
                
                // Verify image works
                image.inside {
                    sh 'gradle --version'
                    sh 'java -version'
                }
                
                logger.info("✅ Gradle image ready!")
            }
            
            env.GRADLE_DOCKER_IMAGE = imagePath
            stageResults['Pull Image'] = 'SUCCESS'
        }
    }
    
    stage('Setup Environment') {
        script {
            logger.info("🔧 SETUP ENVIRONMENT")
            core_utils.setupProjectEnvironment(config.project_language, config)
            stageResults['Setup'] = 'SUCCESS'
        }
    }
    
    stage('Install Dependencies') {
        script {
            logger.info("📦 INSTALL DEPENDENCIES IN CONTAINER")
            
            docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
                def image = docker.image(env.GRADLE_DOCKER_IMAGE)
                
                image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
                    logger.info("🔧 Resolving Gradle dependencies...")
                    sh './gradlew dependencies --refresh-dependencies'
                    logger.info("✅ Dependencies resolved successfully")
                }
            }
            
            stageResults['Install Dependencies'] = 'SUCCESS'
        }
    }
    
    stage('Lint') {
        if (core_utils.shouldExecuteStage('lint', config)) {
            script {
                logger.info("🔍 LINTING IN CONTAINER")
                
                def lintResult = runLintInContainer(config)
                env.LINT_RESULT = lintResult
                stageResults['Lint'] = lintResult
                logger.info("Lint completed with result: ${lintResult}")
            }
        } else {
            script {
                logger.info("Linting is disabled - skipping")
                env.LINT_RESULT = 'SKIPPED'
                stageResults['Lint'] = 'SKIPPED'
            }
        }
    }
    
    stage('Build') {
        script {
            logger.info("🔨 BUILDING IN CONTAINER")
            
            docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
                def image = docker.image(env.GRADLE_DOCKER_IMAGE)
                
                image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
                    logger.info("🏗️ Building Gradle project...")
                    sh './gradlew clean build -x test'
                    
                    // Archive artifacts
                    if (fileExists('build/libs/*.jar')) {
                        archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                        logger.info("📦 JAR artifacts archived")
                    }
                    
                    logger.info("✅ Build completed successfully")
                }
            }
            
            stageResults['Build'] = 'SUCCESS'
        }
    }
    
    stage('Test Execution') {
        script {
            def parallelTests = [:]
            
            // Unit Tests
            if (core_utils.shouldExecuteStage('unittest', config)) {
                parallelTests['Unit Test'] = {
                    logger.info("🧪 Running Unit Tests in Container")
                    def testResult = runUnitTestsInContainer(config)
                    env.UNIT_TEST_RESULT = testResult
                    stageResults['Unit Test'] = testResult
                    logger.info("Unit tests completed with result: ${testResult}")
                }
            } else {
                env.UNIT_TEST_RESULT = 'SKIPPED'
                stageResults['Unit Test'] = 'SKIPPED'
            }
            
            // Functional Tests
            if (core_utils.shouldExecuteStage('functionaltest', config)) {
                parallelTests['Functional Tests'] = {
                    runFunctionalTestsInContainer(config, stageResults)
                }
            } else {
                env.FUNCTIONAL_TEST_RESULT = 'SKIPPED'
                stageResults['Functional Tests'] = 'SKIPPED'
            }
            
            // Execute parallel tests
            if (parallelTests.size() > 0) {
                parallel parallelTests
            } else {
                logger.info("No tests enabled - skipping test execution")
            }
        }
    }
    
    stage('Generate Reports') {
        script {
            logger.info("📊 GENERATE REPORTS STAGE")
            sendReport.generateAndSendReports(config, stageResults)
            stageResults['Generate Reports'] = 'SUCCESS'
        }
    }
}

// Helper Methods
private def setupDockerConfig(Map config) {
    // Set defaults
    if (!config.project_language) {
        config.project_language = 'java-gradle'
    }
    
    // Docker/Nexus configuration
    if (!config.nexus) {
        config.nexus = [:]
    }
    config.nexus.url = config.nexus.url ?: env.NEXUS_REGISTRY_URL ?: 'https://nexus.company.com:8082'
    config.nexus.registry = config.nexus.registry ?: 'nexus.company.com:8082'
    config.nexus.project = config.nexus.project ?: 'dev'
    config.nexus.credentials_id = config.nexus.credentials_id ?: 'nexus-docker-creds'
    
    // Gradle version
    config.gradle_version = config.gradle_version ?: '7.6.1'
    
    // Test configuration
    if (!config.tool_for_unit_testing) {
        config.tool_for_unit_testing = [java: 'junit']
    }
    if (!config.tool_for_lint_testing) {
        config.tool_for_lint_testing = [java: 'checkstyle']
    }
    
    // Stage execution flags
    if (config.runUnitTests == null) config.runUnitTests = true
    if (config.runLintTests == null) config.runLintTests = true
    if (config.runFunctionalTests == null) config.runFunctionalTests = true
    if (config.runSmokeTests == null) config.runSmokeTests = true
    if (config.runSanityTests == null) config.runSanityTests = true
    if (config.runRegressionTests == null) config.runRegressionTests = true
    
    return config
}

private def runLintInContainer(Map config) {
    return docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
        def image = docker.image(env.GRADLE_DOCKER_IMAGE)
        
        return image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
            try {
                def lintTool = config.tool_for_lint_testing.java
                logger.info("🧹 Running ${lintTool}...")
                
                if (lintTool == 'checkstyle') {
                    sh './gradlew checkstyleMain checkstyleTest'
                } else if (lintTool == 'spotbugs') {
                    sh './gradlew spotbugsMain'
                } else {
                    sh "./gradlew ${lintTool}"
                }
                
                return 'SUCCESS'
            } catch (Exception e) {
                logger.warning("⚠️ Lint found violations: ${e.getMessage()}")
                return 'UNSTABLE'
            }
        }
    }
}

private def runUnitTestsInContainer(Map config) {
    return docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
        def image = docker.image(env.GRADLE_DOCKER_IMAGE)
        
        return image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
            try {
                logger.info("🏃 Running unit tests...")
                sh './gradlew test'
                
                // Publish test results
                if (fileExists('build/test-results/test/*.xml')) {
                    publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
                }
                
                return 'SUCCESS'
            } catch (Exception e) {
                logger.warning("⚠️ Some unit tests failed: ${e.getMessage()}")
                return 'UNSTABLE'
            }
        }
    }
}

private def runFunctionalTestsInContainer(Map config, Map stageResults) {
    docker.withRegistry(config.nexus.url, config.nexus.credentials_id) {
        def image = docker.image(env.GRADLE_DOCKER_IMAGE)
        
        image.inside("-v ${WORKSPACE}:/workspace -w /workspace") {
            // Smoke Tests
            stage('Smoke Tests') {
                if (core_utils.shouldExecuteStage('smoketest', config)) {
                    logger.info("💨 Running Smoke Tests...")
                    try {
                        sh './gradlew test -Psmoke'
                        env.SMOKE_TEST_RESULT = 'SUCCESS'
                        stageResults['Smoke Tests'] = 'SUCCESS'
                        logger.info("✅ Smoke Tests completed successfully")
                    } catch (Exception e) {
                        logger.warning("⚠️ Smoke Tests failed: ${e.getMessage()}")
                        env.SMOKE_TEST_RESULT = 'UNSTABLE'
                        stageResults['Smoke Tests'] = 'UNSTABLE'
                    }
                } else {
                    logger.info("Smoke Tests are disabled - skipping")
                    env.SMOKE_TEST_RESULT = 'SKIPPED'
                    stageResults['Smoke Tests'] = 'SKIPPED'
                }
            }
            
            // Sanity Tests
            stage('Sanity Tests') {
                if (core_utils.shouldExecuteStage('sanitytest', config)) {
                    logger.info("🧠 Running Sanity Tests...")
                    try {
                        sh './gradlew test -Psanity'
                        env.SANITY_TEST_RESULT = 'SUCCESS'
                        stageResults['Sanity Tests'] = 'SUCCESS'
                        logger.info("✅ Sanity Tests completed successfully")
                    } catch (Exception e)



_________________________________


@Library('your-shared-library') _

pipeline {
    agent any
    
    stages {
        stage('Build Gradle App') {
            steps {
                script {
                    javaGradle_docker_template([
                        gradle_version: '7.6.1',
                        runUnitTests: true,
                        runLintTests: true,
                        runSmokeTests: true,
                        runSanityTests: true,
                        runRegressionTests: true
                    ])
                }
            }
        }
    }
}
