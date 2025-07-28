/**
 * SUPER SIMPLE Report Generator
 * Does 3 things: 1) Copy test files 2) Make Allure report 3) Send email
 */

/**
 * Main method: Generate Allure reports and send email summary
 * @param config Pipeline configuration map
 * @param stageResults Map of stage names and their results (SUCCESS/FAILURE/UNSTABLE/SKIPPED)
 * Usage: sendReport.generateAndSendReports(config, ['Checkout': 'SUCCESS', 'Build': 'FAILURE'])
 */
def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Starting simple report generation...")
    
    try {
        // Step 1: Make Allure report from test files
        makeAllureReport()
        
        // Step 2: Send email with build summary
        sendSimpleEmail(config, stageResults)
        
        logger.info("Reports completed successfully!")
        
    } catch (Exception e) {
        logger.error("Report generation failed: ${e.getMessage()}")
    }
}

/**
 * Creates Allure HTML report from test result files
 * Searches for test XML files in common locations and generates visual report
 * Usage: Called automatically by generateAndSendReports()
 */
def makeAllureReport() {
    logger.info("Making Allure report...")
    
    try {
        // Create folder for test results (Windows safe way)
        if (!fileExists('allure-results')) {
            bat 'mkdir allure-results'
            // sh 'mkdir -p allure-results' // Linux equivalent
        }
        
        // Copy all test files we can find from Maven/Gradle/Python
        copyAllTestFiles()
        
        // Generate the Allure HTML report using Jenkins plugin
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: 'allure-results']]
        ])
        
        // Publish the HTML report so it's accessible in Jenkins UI
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

/**
 * Copies test result XML files from common build tool locations to allure-results folder
 * Searches Maven (target/surefire-reports), Gradle (build/test-results), Python (test-results.xml)
 * Creates dummy file if no tests found to prevent Allure from breaking
 * Usage: Called automatically by makeAllureReport()
 */
def copyAllTestFiles() {
    logger.info("Looking for test files...")
    
    def foundAny = false
    
    try {
        // Look for Maven test files in target/surefire-reports/*.xml
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
        
        // Look for Gradle test files in build/test-results/test/*.xml
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
        
        // Look for Python pytest file test-results.xml
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

/**
 * Creates a dummy test XML file when no real tests are found
 * Prevents Allure from failing when there are no test results
 * @return void
 * Usage: Called automatically when no test files are found
 */
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

/**
 * Sends plain text email with build summary including test results and lint violations
 * @param config Pipeline configuration containing email recipients
 * @param stageResults Map of stage names and their results
 * Usage: sendSimpleEmail(config, ['Build': 'SUCCESS', 'Test': 'FAILURE'])
 */
def sendSimpleEmail(Map config, Map stageResults) {
    logger.info("Sending email...")
    
    try {
        // Get basic build information from Jenkins environment
        def jobName = env.JOB_NAME ?: 'Unknown Job'
        def buildNumber = env.BUILD_NUMBER ?: '0'
        def buildStatus = notify.getBuildStatus()
        
        // Count test results from XML files
        def testCounts = countTests()
        
        // Count lint violations from report files
        def lintCount = countLintIssues()
        
        // Create email subject and body
        def subject = "Build ${buildStatus}: ${jobName} #${buildNumber}"
        def body = makeEmailBody(jobName, buildNumber, buildStatus, stageResults, testCounts, lintCount)
        
        // Send email using Jenkins emailext plugin
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

/**
 * Counts test results from XML files in Maven/Gradle/Python locations
 * @return Map with total, passed, failed, skipped test counts
 * Usage: def testCounts = countTests(); println "Total: ${testCounts.total}"
 */
def countTests() {
    def total = 0, failed = 0, skipped = 0
    
    try {
        // Look for test XML files in common build tool locations
        def testFiles = []
        
        // Maven tests in target/surefire-reports/*.xml
        if (fileExists('target/surefire-reports')) {
            testFiles.addAll(findFiles(glob: 'target/surefire-reports/*.xml'))
        }
        
        // Gradle tests in build/test-results/test/*.xml
        if (fileExists('build/test-results/test')) {
            testFiles.addAll(findFiles(glob: 'build/test-results/test/*.xml'))
        }
        
        // Python pytest results in test-results.xml
        if (fileExists('test-results.xml')) {
            testFiles.add([path: 'test-results.xml'])
        }
        
        // Parse each XML file and extract test counts
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

/**
 * Counts lint violations from checkstyle (Java) or pylint (Python) report files
 * @return Integer count of lint violations found
 * Usage: def violations = countLintIssues(); println "Found ${violations} violations"
 */
def countLintIssues() {
    def count = 0
    
    try {
        // Java checkstyle violations in target/checkstyle-result.xml
        if (fileExists('target/checkstyle-result.xml')) {
            def xml = readFile('target/checkstyle-result.xml')
            count = (xml =~ /<error/).size()
        }
        
        // Python pylint violations in pylint-report.txt
        if (fileExists('pylint-report.txt')) {
            def report = readFile('pylint-report.txt')
            count = report.split('\n').findAll { it.contains(':') && !it.contains('*') }.size()
        }
        
    } catch (Exception e) {
        logger.warning("Could not count lint issues: ${e.getMessage()}")
    }
    
    return count
}

/**
 * Extracts numeric value from XML attribute (e.g., tests="5" returns 5)
 * @param xml String containing XML content
 * @param attribute Name of XML attribute to extract number from
 * @return Integer value found in attribute, or 0 if not found
 * Usage: def testCount = getNumber(xmlContent, 'tests')
 */
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

/**
 * Creates formatted plain text email body with build summary
 * @param jobName Jenkins job name
 * @param buildNumber Jenkins build number
 * @param status Overall build status (SUCCESS/FAILURE/UNSTABLE)
 * @param stages Map of stage names and results
 * @param tests Map with test counts (total, passed, failed, skipped)
 * @param lintCount Number of lint violations
 * @return String formatted email body
 * Usage: def body = makeEmailBody("MyJob", "123", "SUCCESS", stages, tests, 5)
 */
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