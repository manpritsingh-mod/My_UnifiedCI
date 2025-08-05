/**
 * Main aim of this file is to do these 3 things
 * 1) Copy test files 2) Make Allure report 3) Send email
 */

def generateAndSendReports(Map config, Map stageResults = [:]) {
    logger.info("Starting simple report generation...")
    
    try {
        // Make Allure report from test files like (.xml)
        makeAllureReport()
        
        // Send email with Overall build summary
        sendSimpleEmailSlack(config, stageResults)
        
        logger.info("Reports completed successfully!")
        
    } catch (Exception e) {
        logger.error("Report generation failed: ${e.getMessage()}")
    }
}

def makeAllureReport() {
    logger.info("Making Allure report...")

    try {
        if (!fileExists('allure-results')) {
            sh 'mkdir -p allure-results'
        }

        // Copy test result files from different build tool folders to allure-results
        copyAllTestFiles()

        // Generate and publish the Allure report 
        allure([
            includeProperties: false,
            jdk: '',
            properties: [],
            reportBuildPolicy: 'ALWAYS',
            results: [[path: 'allure-results']]
        ])

        logger.info("Allure report created and published in Jenkins UI!")

    } catch (Exception e) {
        logger.error("Allure report failed: ${e.getMessage()}")
    }
}

def copyAllTestFiles() {
    logger.info("Looking for test files...")
    
    def foundAny = false
    
    try {
        // Look for Maven test files in target/surefire-reports/*.xml
        if (fileExists('target/surefire-reports')) {
            def files = findFiles(glob: 'target/surefire-reports/*.xml')
            files.each { file ->
                try {
                    sh "cp \"${file.path}\" allure-results/" 
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
                    sh "cp \"${file.path}\" allure-results/"
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
                sh "cp test-results.xml allure-results/"
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
        makeDummyTestFile() // method call 
    }
}

def makeDummyTestFile() {
    def dummyXml = '''
    <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="NoTestsFound" tests="1" failures="0" errors="0" skipped="0">
            <testcase name="placeholder" classname="PlaceholderTest">
                <system-out>No tests found - this is just a placeholder</system-out>
            </testcase>
        </testsuite>
    '''
    
    writeFile file: 'allure-results/dummy.xml', text: dummyXml
    logger.info("Created dummy test file")
}

def sendSimpleEmailSlack(Map config, Map stageResults) {
    logger.info("Sending email...")

    def emailBody = { String jobName, String buildNumber, String buildStatus, Map stages, Map testCounts, int lintCount ->
        def stageText = ""
        stages.each { name, result ->
            stageText += " - ${name}: ${result}\n"
        }

        return """
            BUILD REPORT
            ============

            Job: ${jobName}
            Build: #${buildNumber}
            Status: ${buildStatus}
            Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
            URL: ${env.BUILD_URL ?: 'Not available'}

            STAGES:
            ${stageText}

            TESTS:
            - Total: ${testCounts.total}
            - Passed: ${testCounts.passed}
            - Failed: ${testCounts.failed}
            - Skipped: ${testCounts.skipped}

            LINT ISSUES: ${lintCount}

            ============ Build ${buildStatus} ============
        """
    }

    try {
        def jobName = env.JOB_NAME ?: 'Unknown Job'
        def buildNumber = env.BUILD_NUMBER ?: '0'
        def buildStatus = notify.getBuildStatus()

        def testCounts = countTests()
        def lintCount = countLintIssues()

        def emailFullbody = emailBody(jobName, buildNumber, buildStatus, stageResults, testCounts, lintCount)

        emailext([
            subject : "Build ${buildStatus}: ${jobName} #${buildNumber}",
            body    : emailFullbody,
            mimeType: 'text/plain',
            to      : config.notifications?.email?.recipients?.join(',') ?: 'smanprit022@gmail.com'
        ])

        logger.info("Email sent!")

    } catch (Exception e) {
        logger.error("Email failed: ${e.getMessage()}")
    }
}



def countTests() {
    def total = 0, failed = 0, skipped = 0 
    
    try {
        // Look for test XML files in the workspace
        def testFiles = []
        
        // Maven tests in target/surefire-reports/*.xml
        if (fileExists('target/surefire-reports')) {
            testFiles.addAll(findFiles(glob: 'target/surefire-reports/*.xml'))
            logger.info("=========File exist for Maven========")
        }
        
        // Gradle tests in build/test-results/test/*.xml
        if (fileExists('build/test-results/test')) {
            testFiles.addAll(findFiles(glob: 'build/test-results/test/*.xml'))
            logger.info("=========File exist for Gradle=======")
        }
        
        // Python pytest results in test-results.xml
        if (fileExists('test-results.xml')) {
            testFiles.add([path: 'test-results.xml'])
            logger.info("=========File exist for Python=======")
        }

        // Parse each XML file and extract test counts
        testFiles.each { file ->
            try {
                def xml = readFile(file.path)
                def parsed = new XmlSlurper().parseText(xml)
                total += parsed.@tests.toInteger()
                failed += parsed.@failures.toInteger() + parsed.@errors.toInteger()
                skipped += parsed.@skipped.toInteger()
            } catch (e) {
                logger.info("Could not parse ${file.path}")
            }
        }
        
    } catch (Exception e) {
        logger.warning("Could not count tests: ${e.getMessage()}")
    }
    
    def passed = total - failed - skipped
    return [total: total, passed: passed, failed: failed, skipped: skipped]
}

def countLintIssues() {
    def count = 0

    // Java checkstyle violations in target/checkstyle-result.xml
    if (fileExists('target/checkstyle-result.xml')) {
        logger.debug("============check for the File=========")
        def xml = readFile('target/checkstyle-result.xml')
        count = (xml =~ /<error/).size()
        logger.debug("=========read File done successfully=========${count}")

    // Python pylint violations in pylint-report.txt
    } else if (fileExists('pylint-report.txt')) {
        logger.debug("============check for the File=========")
        def report = readFile('pylint-report.txt')
        logger.debug("=========read File done successfully=========")
        count = report.readLines().count
        { 
            it.contains(':') && !it.contains('*') 
        }
    }

    return count
}

def getNumber(String xml, String attribute) {
    try {
        def parsed = new XmlSlurper().parseText(xml)
        return parsed."@$attribute".toInteger()
    } catch (Exception e) {
        return 0
    }
}

// def sendSlackMessage(String status, String jobName, String buildNumber, Map tests, int lintCount) {
    
//     def color = (status == 'SUCCESS') ? 'good' :
//                 (status == 'UNSTABLE') ? 'warning' : 'danger'

//     def message = """
//             ${status}: ${jobName} #${buildNumber}
//             Tests: Total: ${tests.total}, Passed: ${tests.passed}, Failed: ${tests.failed}, Skipped: ${tests.skipped}
//             Lint Issues: ${lintCount}
//             URL: ${env.BUILD_URL ?: 'Not available'}
//             """.stripIndent().trim()

//     slackSend(
//         channel: '#sample',
//         color: color,
//         message: message
//     )
// }

return this