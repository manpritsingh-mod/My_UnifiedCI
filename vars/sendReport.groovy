/**
 * SUPER SIMPLE Report Generator
 * Does 3 things: 1) Copy test files 2) Make Allure report 3) Send email
 */

// MAIN METHOD: Do everything in simple steps
def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Starting simple report generation...")
    
    try {
        // Step 1: Make Allure report
        makeAllureReport()
        
        // Step 2: Send email with summary
        sendSimpleEmail(config, stageResults)
        
        logger.info("Reports completed successfully!")
        
    } catch (Exception e) {
        logger.error("Report generation failed: ${e.getMessage()}")
    }
}

// STEP 1: Make Allure Report (Simple Version)
def makeAllureReport() {
    logger.info("Making Allure report...")
    
    try {
        // Create folder for test results (Windows safe way)
        if (!fileExists('allure-results')) {
            bat 'mkdir allure-results'
            // sh 'mkdir -p allure-results' // Linux equivalent
        }
        
        // Copy all test files we can find
        copyAllTestFiles()
        
        // Generate the Allure report
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: 'allure-results']]
        ])
        
        // Publish the HTML report
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'allure-report',
            reportFiles: 'index.html',
            reportName: 'Allure Report'
        ])
        
        logger.info("Allure report created!")
        
    } catch (Exception e) {
        logger.error("Allure report failed: ${e.getMessage()}")
    }
}

// Copy test files from common locations (Simple Version)
def copyAllTestFiles() {
    logger.info("Looking for test files...")
    
    def foundAny = false
    
    try {
        // Look for Maven test files
        if (fileExists('target/surefire-reports')) {
            def files = findFiles(glob: 'target/surefire-reports/*.xml')
            files.each { file ->
                try {
                    bat "copy \"${file.path}\" allure-results\\"
                    // sh "cp \"${file.path}\" allure-results/" // Linux equivalent
                    foundAny = true
                    logger.info("Copied Maven test: ${file.name}")
                } catch (Exception e) {
                    logger.info("Could not copy ${file.name}")
                }
            }
        }
        
        // Look for Gradle test files
        if (fileExists('build/test-results/test')) {
            def files = findFiles(glob: 'build/test-results/test/*.xml')
            files.each { file ->
                try {
                    bat "copy \"${file.path}\" allure-results\\"
                    // sh "cp \"${file.path}\" allure-results/" // Linux equivalent
                    foundAny = true
                    logger.info("Copied Gradle test: ${file.name}")
                } catch (Exception e) {
                    logger.info("Could not copy ${file.name}")
                }
            }
        }
        
        // Look for Python test file
        if (fileExists('test-results.xml')) {
            try {
                bat "copy test-results.xml allure-results\\"
                // sh "cp test-results.xml allure-results/" // Linux equivalent
                foundAny = true
                logger.info("Copied Python test: test-results.xml")
            } catch (Exception e) {
                logger.info("Could not copy test-results.xml")
            }
        }
        
    } catch (Exception e) {
        logger.info("Error looking for test files: ${e.getMessage()}")
    }
    
    // If no files found, create a dummy one so Allure doesn't break
    if (!foundAny) {
        logger.info("No test files found, creating dummy...")
        makeDummyTestFile()
    }
}

// Make a fake test file so Allure doesn't break
def makeDummyTestFile() {
    def dummyXml = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="NoTestsFound" tests="1" failures="0" errors="0" skipped="0">
    <testcase name="placeholder" classname="PlaceholderTest">
        <system-out>No tests found - this is just a placeholder</system-out>
    </testcase>
</testsuite>'''
    
    writeFile file: 'allure-results/dummy.xml', text: dummyXml
    logger.info("Created dummy test file")
}

// STEP 2: Send Simple Email
def sendSimpleEmail(Map config, Map stageResults) {
    logger.info("Sending email...")
    
    try {
        // Get basic info
        def jobName = env.JOB_NAME ?: 'Unknown Job'
        def buildNumber = env.BUILD_NUMBER ?: '0'
        def buildStatus = notify.getBuildStatus()
        
        // Count tests
        def testCounts = countTests()
        
        // Count lint issues
        def lintCount = countLintIssues()
        
        // Make email
        def subject = "Build ${buildStatus}: ${jobName} #${buildNumber}"
        def body = makeEmailBody(jobName, buildNumber, buildStatus, stageResults, testCounts, lintCount)
        
        // Send it
        emailext (
            subject: subject,
            body: body,
            mimeType: 'text/plain',
            to: config.notifications?.email?.recipients?.join(',') ?: 'smanprit022@gmail.com'
        )
        
        logger.info("Email sent!")
        
    } catch (Exception e) {
        logger.error("Email failed: ${e.getMessage()}")
    }
}

// SIMPLE HELPER METHODS

// Count how many tests we have
def countTests() {
    def total = 0, failed = 0, skipped = 0
    
    try {
        // Look for test XML files in common places
        def testFiles = []
        
        // Maven tests
        if (fileExists('target/surefire-reports')) {
            testFiles.addAll(findFiles(glob: 'target/surefire-reports/*.xml'))
        }
        
        // Gradle tests  
        if (fileExists('build/test-results/test')) {
            testFiles.addAll(findFiles(glob: 'build/test-results/test/*.xml'))
        }
        
        // Python tests
        if (fileExists('test-results.xml')) {
            testFiles.add([path: 'test-results.xml'])
        }
        
        // Read each file and count tests
        testFiles.each { file ->
            try {
                def xml = readFile(file.path)
                total += getNumber(xml, 'tests')
                failed += getNumber(xml, 'failures') + getNumber(xml, 'errors')
                skipped += getNumber(xml, 'skipped')
            } catch (Exception e) {
                // Skip files we can't read
            }
        }
        
    } catch (Exception e) {
        logger.warning("Could not count tests: ${e.getMessage()}")
    }
    
    def passed = total - failed - skipped
    return [total: total, passed: passed, failed: failed, skipped: skipped]
}

// Count lint issues
def countLintIssues() {
    def count = 0
    
    try {
        // Java checkstyle
        if (fileExists('target/checkstyle-result.xml')) {
            def xml = readFile('target/checkstyle-result.xml')
            count = (xml =~ /<error/).size()
        }
        
        // Python pylint
        if (fileExists('pylint-report.txt')) {
            def report = readFile('pylint-report.txt')
            count = report.split('\n').findAll { it.contains(':') && !it.contains('*') }.size()
        }
        
    } catch (Exception e) {
        logger.warning("Could not count lint issues: ${e.getMessage()}")
    }
    
    return count
}

// Get a number from XML (like tests="5")
def getNumber(String xml, String attribute) {
    try {
        def pattern = "${attribute}=\"(\\d+)\""
        def match = (xml =~ pattern)
        if (match && match.size() > 0) {
            return Integer.parseInt(match[0][1])
        }
    } catch (Exception e) {
        // Return 0 if we can't parse
    }
    return 0
}

// Make the email text
def makeEmailBody(String jobName, String buildNumber, String status, Map stages, Map tests, int lintCount) {
    def stageText = ""
    stages.each { name, result ->
        stageText += "${name}: ${result}\n"
    }
    
    return """
BUILD REPORT
============

Job: ${jobName}
Build: #${buildNumber}
Status: ${status}
Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
URL: ${env.BUILD_URL ?: 'Not available'}

STAGES:
${stageText}

TESTS:
Total: ${tests.total}
Passed: ${tests.passed}
Failed: ${tests.failed}
Skipped: ${tests.skipped}

LINT ISSUES: ${lintCount}

============
Build ${status}
============
"""
}

return this