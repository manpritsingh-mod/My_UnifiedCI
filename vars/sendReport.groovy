/**
 * SIMPLE Report generation utilities
 * - Collect basic test and lint data
 * - Send plain text email summary
 */

// MAIN METHOD: Generate Allure report and send email summary
def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Generating Allure report and build summary")
    
    try {
        // Generate Allure report first
        generateAllureReport()
        
        // Then collect summary and send email
        def buildSummary = collectBuildSummary(config, stageResults)
        sendBuildSummaryEmail(config, buildSummary)
        
    } catch (Exception e) {
        logger.error("Failed to generate reports: ${e.getMessage()}")
    }
}

// GENERATE ALLURE REPORT
def generateAllureReport() {
    logger.info("=== GENERATING ALLURE REPORT ===")
    
    try {
        // Create allure-results directory
        if (!fileExists('allure-results')) {
            bat 'mkdir allure-results'
            logger.info("Created allure-results directory")
        } else {
            logger.info("allure-results directory already exists")
        }
        
        // Copy test results to allure-results
        logger.info("Copying test results to allure-results...")
        copyTestResultsToAllure()
        
        // Check what files are in allure-results before generating report
        logger.info("Files in allure-results before generation:")
        bat 'dir allure-results'
        
        // Generate Allure report
        logger.info("Generating Allure report...")
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: 'allure-results']]
        ])
        
        // Check if allure-report was created
        if (fileExists('allure-report')) {
            logger.info("allure-report directory created successfully")
            bat 'dir allure-report'
        } else {
            logger.warning("allure-report directory was not created!")
        }
        
        // Publish HTML report
        logger.info("Publishing HTML report...")
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'allure-report',
            reportFiles: 'index.html',
            reportName: 'Allure Report',
            reportTitles: ''
        ])
        
        logger.info("=== ALLURE REPORT GENERATION COMPLETED ===")
        
    } catch (Exception e) {
        logger.error("Failed to generate Allure report: ${e.getMessage()}")
        e.printStackTrace()
    }
}

private def copyTestResultsToAllure() {
    logger.info("DEBUG: Checking for test result files...")
    
    try {
        // List all files in workspace for debugging
        bat 'dir /s *.xml'
        
        def foundFiles = false
        
        // Copy JUnit results (Maven/Gradle)
        if (fileExists('target/surefire-reports')) {
            logger.info("Found target/surefire-reports directory")
            bat 'dir target\\surefire-reports'
            
            // Copy all XML files from surefire-reports
            bat 'xcopy /y target\\surefire-reports\\*.xml allure-results\\ 2>nul || echo "No XML files to copy from surefire-reports"'
            
            // Also try copying with full path
            def surefireFiles = findFiles(glob: 'target/surefire-reports/*.xml')
            if (surefireFiles.size() > 0) {
                logger.info("Found ${surefireFiles.size()} surefire XML files")
                surefireFiles.each { file ->
                    logger.info("Copying: ${file.path}")
                    bat "copy \"${file.path}\" allure-results\\"
                }
                foundFiles = true
            } else {
                logger.warning("No XML files found in target/surefire-reports")
            }
        } else {
            logger.info("target/surefire-reports directory not found")
        }
        
        // Copy pytest results (Python)
        if (fileExists('test-results.xml')) {
            logger.info("Found test-results.xml file")
            bat 'copy test-results.xml allure-results\\'
            foundFiles = true
        } else {
            logger.info("test-results.xml file not found")
        }
        
        // Try to find any XML files with test in the name
        def testXmlFiles = findFiles(glob: '**/*test*.xml')
        if (testXmlFiles.size() > 0) {
            logger.info("Found ${testXmlFiles.size()} test XML files:")
            testXmlFiles.each { file ->
                logger.info("  - ${file.path}")
                try {
                    bat "copy \"${file.path}\" allure-results\\"
                    foundFiles = true
                } catch (Exception e) {
                    logger.warning("Could not copy ${file.path}: ${e.getMessage()}")
                }
            }
        }
        
        // Try to find any XML files in common test directories
        def commonPaths = [
            'target/surefire-reports/*.xml',
            'target/failsafe-reports/*.xml', 
            'build/test-results/test/*.xml',
            'test-output/*.xml',
            'reports/*.xml'
        ]
        
        commonPaths.each { pattern ->
            def files = findFiles(glob: pattern)
            if (files.size() > 0) {
                logger.info("Found ${files.size()} files matching ${pattern}")
                files.each { file ->
                    try {
                        bat "copy \"${file.path}\" allure-results\\"
                        foundFiles = true
                    } catch (Exception e) {
                        logger.warning("Could not copy ${file.path}: ${e.getMessage()}")
                    }
                }
            }
        }
        
        // Always list what we have in allure-results
        logger.info("Contents of allure-results directory:")
        bat 'dir allure-results'
        
        // Check if we have any XML files in allure-results
        def allureFiles = findFiles(glob: 'allure-results/*.xml')
        if (allureFiles.size() > 0) {
            logger.info("Successfully copied ${allureFiles.size()} XML files to allure-results")
            allureFiles.each { file ->
                logger.info("  - ${file.name}")
                // Show first few lines of each XML to verify content
                try {
                    def content = readFile(file.path)
                    def firstLines = content.split('\n')[0..2].join('\n')
                    logger.info("    Content preview: ${firstLines}")
                } catch (Exception e) {
                    logger.warning("Could not read ${file.path}: ${e.getMessage()}")
                }
            }
        } else {
            logger.warning("No XML files found in allure-results! Creating dummy test result...")
            createDummyTestResult()
        }
        
    } catch (Exception e) {
        logger.warning("Failed to copy test results: ${e.getMessage()}")
        createDummyTestResult()
    }
}

private def createDummyTestResult() {
    logger.info("Creating dummy test result for Allure demonstration")
    
    def dummyResult = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="DummyTestSuite" tests="1" failures="0" errors="0" skipped="0" time="0.001">
    <testcase name="dummyTest" classname="DummyTest" time="0.001">
        <system-out>No actual tests were found, this is a placeholder for Allure report generation.</system-out>
    </testcase>
</testsuite>'''
    
    writeFile file: 'allure-results/dummy-test-result.xml', text: dummyResult
    logger.info("Dummy test result created")
}

// COLLECT BUILD SUMMARY
def collectBuildSummary(Map config, Map stageResults) {
    logger.info("Collecting build summary data")
    
    def summary = [
        buildInfo: getBuildInfo(),
        stageResults: stageResults ?: getDefaultStageResults(),
        testSummary: getTestSummary(),
        lintSummary: getLintSummary(),
        overallStatus: notify.getBuildStatus()
    ]
    
    return summary
}

// SEND EMAIL SUMMARY
def sendBuildSummaryEmail(Map config, Map buildSummary) {
    logger.info("Sending build summary email")
    
    try {
        def subject = "Build Summary: ${buildSummary.buildInfo.jobName} #${buildSummary.buildInfo.buildNumber} - ${buildSummary.overallStatus}"
        def body = generateEmailBody(buildSummary)
        
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/plain',
            to: config.notifications?.email?.recipients?.join(',') ?: 'smanprit022@gmail.com'
        )
        
        logger.info("Build summary email sent successfully")
        
    } catch (Exception e) {
        logger.error("Failed to send email: ${e.getMessage()}")
    }
}

// HELPER METHODS

private def getBuildInfo() {
    return [
        jobName: env.JOB_NAME ?: 'Unknown Job',
        buildNumber: env.BUILD_NUMBER ?: 'Unknown Build',
        buildUrl: env.BUILD_URL ?: 'Unknown URL',
        gitBranch: env.GIT_BRANCH ?: 'Unknown Branch',
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        duration: currentBuild.durationString ?: 'Unknown'
    ]
}

private def getDefaultStageResults() {
    return [
        'Checkout': 'SUCCESS',
        'Build': 'SUCCESS',
        'Test': 'SUCCESS',
        'Lint': 'SUCCESS'
    ]
}

private def getTestSummary() {
    def summary = [total: 0, passed: 0, failed: 0, skipped: 0]
    
    try {
        // Check Maven/Gradle test results
        if (fileExists('target/surefire-reports')) {
            def testFiles = findFiles(glob: 'target/surefire-reports/TEST-*.xml')
            testFiles.each { file ->
                def xml = readFile(file.path)
                summary.total += parseNumber(xml, /tests="(\d+)"/)
                summary.failed += parseNumber(xml, /failures="(\d+)"/)
                summary.failed += parseNumber(xml, /errors="(\d+)"/)
                summary.skipped += parseNumber(xml, /skipped="(\d+)"/)
            }
        }
        
        // Check pytest results
        if (fileExists('test-results.xml')) {
            def xml = readFile('test-results.xml')
            summary.total += parseNumber(xml, /tests="(\d+)"/)
            summary.failed += parseNumber(xml, /failures="(\d+)"/)
            summary.skipped += parseNumber(xml, /skipped="(\d+)"/)
        }
        
        summary.passed = summary.total - summary.failed - summary.skipped
        
    } catch (Exception e) {
        logger.warning("Failed to get test summary: ${e.getMessage()}")
    }
    
    return summary
}

private def getLintSummary() {
    def summary = [violations: 0, tool: 'none']
    
    try {
        // Check checkstyle (Java)
        if (fileExists('target/checkstyle-result.xml')) {
            def xml = readFile('target/checkstyle-result.xml')
            summary.violations = (xml =~ /<error/).size()
            summary.tool = 'checkstyle'
        }
        
        // Check pylint (Python)
        if (fileExists('pylint-report.txt')) {
            def report = readFile('pylint-report.txt')
            summary.violations = report.split('\n').findAll { it.contains(':') && !it.contains('*') }.size()
            summary.tool = 'pylint'
        }
        
    } catch (Exception e) {
        logger.warning("Failed to get lint summary: ${e.getMessage()}")
    }
    
    return summary
}

private def parseNumber(String text, String pattern) {
    try {
        def match = (text =~ pattern)
        return match ? Integer.parseInt(match[0][1]) : 0
    } catch (Exception e) {
        return 0
    }
}

private String generateEmailBody(Map buildSummary) {
    def status = buildSummary.overallStatus
    def buildInfo = buildSummary.buildInfo
    def stageResults = buildSummary.stageResults
    def testSummary = buildSummary.testSummary
    def lintSummary = buildSummary.lintSummary
    
    return """
===============================================
BUILD SUMMARY - ${status}
===============================================

BUILD INFO:
-----------
Job:      ${buildInfo.jobName}
Build:    #${buildInfo.buildNumber}
Status:   ${status}
Duration: ${buildInfo.duration}
Branch:   ${buildInfo.gitBranch}
Time:     ${buildInfo.timestamp}
URL:      ${buildInfo.buildUrl}

STAGES:
-------
${generateStageText(stageResults)}

TESTS:
------
Total:    ${testSummary.total}
Passed:   ${testSummary.passed}
Failed:   ${testSummary.failed}
Skipped:  ${testSummary.skipped}

LINT:
-----
Tool:       ${lintSummary.tool}
Violations: ${lintSummary.violations}

===============================================
${getStatusMessage(status)}
===============================================
    """
}

private String generateStageText(Map stageResults) {
    def text = ""
    stageResults.each { stage, result ->
        text += "${stage.padRight(20)} ${result}\n"
    }
    return text
}

private String getStatusMessage(String status) {
    switch(status.toUpperCase()) {
        case 'SUCCESS': return 'All stages completed successfully!'
        case 'FAILED': return 'Build failed. Please check the logs.'
        case 'UNSTABLE': return 'Build completed with warnings.'
        default: return 'Build completed.'
    }
}

return this