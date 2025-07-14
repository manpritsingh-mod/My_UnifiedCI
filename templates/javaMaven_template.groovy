@Library('UnifiedCI_sh') _

// def call(Map config){ // doubt in this ??? What should be the best way for call the templates

// }
pipeline{
    agent any 

    environment {
        config = readJSON text: env.PROJECT_CONFIG // can i do it like this way ??
    }

    stages{
        stage('Checkout'){
            steps{
                script{
                    Logger.info("CHECKOUT STAGE")
                    core_github.checkout()
                }
            }
        }
        stage('Setup'){
            steps{
                script{
                    Logger.info("SETUP STAGE")
                    def config = readJSON text: env.PROJECT_CONFIG 
                    // I am having doubt that it can access the env.PROJECT_CONFIG or not ??
                    // If it can access which is the best approach calling in every stage or making it 
                    // as the environment variable outside the stages and using that 


                    core_utils.setupProjectEnvironment(config.project_language, config)
                    sh 'java -version'
                    sh 'mvn -version'
                }
            }
        }
        stage('Install Dependencies'){
            steps{
                script{
                    Logger.info("INSTALL DEPENDENCIES STAGE")
                    def config = readJSON text: env.PROJECT_CONFIG

                    core_build.installDependencies('java', 'maven', config )
                }
            }
        }
        stage('Lint'){
            when{
                expression{
                    def config = readJSON text: env.PROJECT_CONFIG
                    core_utils.shouldExecuteStage('lint', config) 
                    // should I do it here or the check part can be done inside the lint_utils also ??? Only written for the asking purpose
                }
            }
            steps{
                script{
                    Logger.info("LINTING STAGE")
                    def config = readJSON text: env.PROJECT_CONFIG
                    lint_utils.runLint(config) //incomplete 
                }
            }
        }
        stage("Build"){
            steps{
                script{
                    Logger.info("BUILDING STAGE")
                    def config = readJSON text: env.PROJECT_CONFIG
                    core_build.buildLanguages(config.project_language,config) 
                }
            }
        }
        stage("Unit Test"){
            when{
                expression{
                    def config = readJSON text: env.PROJECT_CONFIG
                    return core_utils.shouldExecuteStage('unittest', config) // should I do it here or the check part can be done inside the lint_utils also ???
                }
            }
            steps{
                script{
                    Logger.info("UNIT-TEST STAGE")
                    def config = readJSON text: env.PROJECT_CONFIG
                    core_test.runUnitTest(config) // incomplete 
                }
            }
        }
        // stage("Notify"){
      

        // }
    }
 
}