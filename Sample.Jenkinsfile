#!/usr/bin/env groovy

pipeline {
    agent none
    options {
        buildDiscarder(logRotator(daysToKeepStr: '18'))
    }
    parameters {
        booleanParam(
            defaultValue: false,
            description: 'Run tests?',
            name: 'runTests'
        )
    }
    environment {
        // TODO: comments from Erwin.
        // This step is too large and contains too much detail.

        // Ideally, normal test suites should consist of four steps.
        // 1. Setup() - (generic: cleans workspaces, unstashes, downloads build results)
        // 2. Install("MyInstall.bat --foo ..") - Test Suite specific installer
        // 3. RunNUnitTests("AipIpf.xml") - Runs tests, may contain test engine specific details (e.g. test filter).
        // 4. Wrapup() - (generic: finalizes results, copies to result share, etc)
        // In principle, the Jenkins file should not contain suite-specific logic. This logic (which files to copy) should be moved inside the product archive.
        // We need to clearly separate what should be inside the Jenkins file, what should be in the product archive, and what should be elsewhere (e.g. pipeline config).
        // We want to minimize what is in the Jenkins file (to facilitate moving to a different environment, e.g. Azure)

        ARTIFACTORY_APIKEY = credentials('Artifactory_APIKey')
        ARTIFACTORY_ID = "Artifactory"

        // Directory where all work items are placed.
        PIPE_WORK_AREA = "Work"

        // Directory where stashed items are placed.
        PIPE_STASH_DIR = "Output\\Stash"

        // Temporary test properties
        // TODO: Need to move from here to test stage.
        TEST_STAGE_NAME = "StageCapacity"
        TEST_TYPE = "Regression"
    }
    stages {
        stage('Pretend Build') {
            agent { label 'buildserver' }
            options {
               timeout(time: 1, unit: 'HOURS')
               timestamps()
            }
            steps {
                // TODO: For now the buildserver is (mis)used for initializing the pipeline
                // TODO: Build id shall be determined at this place.
                InitializePipeline()
                // Cleaning the workspace is not done as it takes around 60 min to perform checkout
                // of complete archive. Jenkins which invokes this script is expected to configure
                // to perform archive update.
                // cleanWs()
                script {
                    if (EvaluateTestsToRun()) {
                        bat """ %WORKSPACE%/Automation/Build/PublishBuildStash.bat """
                        stash includes: 'Output/Stash/**', name: "BuildStash"
                    }
                }
            }
        }
        stage('Test') {
            when {
                expression { return EvaluateTestsToRun()}
            }
            parallel {
                stage('Test_InstallersCreation') {
                    agent {
                        label 'TestServer'
                    }
                    options { skipDefaultCheckout() }
                    steps {
                        Setup()
                        CreateInstallers()
                        PublishInstallers()
                    }
                }
                stage('Test_ManagedServices') {
                    stages {
                        stage('Test_ManagedServices_Core') {
                            agent {
                                label 'TestServer && WindowsDocker'
                            }
                            options { skipDefaultCheckout() }
                            steps {
                                Setup()
                                TestSetup("DeployAndTestManagedServicesCore_CapacityStage", "VSTEST")
                                DeployManagedServicesCore()
                                RunTestsManagedServicesCore()
                                TestWrapup()
                            }
                        }
                        stage('Test_ManagedServices_InDocker') {
                            agent {label 'TestServer && WindowsDocker'}
                            options { skipDefaultCheckout() }
                            steps {
                                Setup()
                                TestSetup("DeployAndTestManagedServicesInDocker", "VSTEST")
                                DeployManagedServicesInDocker()
                                RunTestsManagedServicesInDocker()
                                TestWrapup()
                            }
                        }
                        stage('Test_ManagedServices_InHSDP') {
                            agent {label 'TestServer && WindowsDocker'}
                            options { skipDefaultCheckout() }
                            environment {
                                // Credentials required to run tests which uses HSDP cloud foundry.
                                // TODO: Comments from Martijn
                                // Since we will start using this in more places and with different credentials (different docker registries for example). 
                                // It might be good to start thinking about making this also part of one of the four steps in the future.
                                DOCKER_CREDENTIALS = credentials("DOCKER_CREDENTIALS")
                                DOCKER_USERNAME = "${DOCKER_CREDENTIALS_USR}"
                                DOCKER_PASSWORD = "${DOCKER_CREDENTIALS_PSW}"
                                CLOUDFOUNDRY_USERNAME = "${DOCKER_CREDENTIALS_USR}"
                                CLOUDFOUNDRY_PASSWORD = "${DOCKER_CREDENTIALS_PSW}"
                            }
                            steps {
                                Setup()
                                TestSetup("DeployAndTestManagedServicesInHsdp", "VSTEST")
                                DeployManagedServicesInHsdp()
                                RunTestsManagedServicesInHsdp()
                                TestWrapup()
                            }
                        }
                    }
                }
            }
        }
    }
}

// Functions
// ---------- Product-specific deployment and test invocations ----------------------------------
// Below method depends on the product archive (e.g. invoke product-specific batch scripts).
// The below methods are only intended to improve readability of the main pipeline spec, but should only consist of one line each.
// The actual logic should still reside in the product archive itself.
def DeployManagedServicesCore() {
    RunBatch("${PIPE_TEST_SUITE_ROOT_PATH}\\DeployAndTestManagedServicesCore_CapacityStage.bat --install")
}
def RunTestsManagedServicesCore() {
    RunBatch("${PIPE_TEST_SUITE_ROOT_PATH}\\DeployAndTestManagedServicesCore_CapacityStage.bat --runTests  ${PIPE_TEST_RESULTS_DIR}")
}
def DeployManagedServicesInDocker() {
    RunBatch("${PIPE_TEST_SUITE_ROOT_PATH}\\DeployAndTestManagedServicesInDocker.bat --install")
}
def RunTestsManagedServicesInDocker() {
    RunBatch("${PIPE_TEST_SUITE_ROOT_PATH}\\DeployAndTestManagedServicesInDocker.bat --runTests  ${PIPE_TEST_RESULTS_DIR}")
}
def DeployManagedServicesInHsdp() {
    RunBatch("${PIPE_TEST_SUITE_ROOT_PATH}\\DeployAndTestManagedServicesInHsdp.bat --install")
}
def RunTestsManagedServicesInHsdp() {
    RunBatch("${PIPE_TEST_SUITE_ROOT_PATH}\\DeployAndTestManagedServicesInHsdp.bat --runTests  ${PIPE_TEST_RESULTS_DIR}")
}
def CreateInstallers() {
    RunBatch("${WORKSPACE}\\${PIPE_STASH_DIR}\\Build_\\CreateInstallersForRelease.bat --skip-integrity-check --generate-checksum ${PIPE_BUILD_DIR} ${PIPE_INSTALLERS_DIR}")
}

// ---------- Other functions -------------------------------------------
// NOTE: Developers SHOULD NOT modify any methods below, without prior consent from Spiders.

// Generic setup required for most of the machines.
def Setup() {
    script {
        cleanWs()
        unstash 'BuildStash'
        DownloadBuildResults("${WORKSPACE}\\${PIPE_WORK_AREA}")
        // Unzip the downloaded binaries
        RunBatch("%BTIENV_ZIP_EXE% x -y ${WORKSPACE}\\${PIPE_WORK_AREA}\\*.zip -o${WORKSPACE}\\${PIPE_WORK_AREA}\\")
        PIPE_BUILD_ID = GetBuildId()
        // TODO: Below line updates display name every time. It shall:
        // a. Update only once in a full run.
        // b. To ensure same build is downloaded during execution of subsequent stages, build id shall match with 'displayname' from second run onwards. Throw exception otherwise.
        currentBuild.displayName = "${PIPE_BUILD_ID}"

        PIPE_BUILD_DIR = "${WORKSPACE}\\${PIPE_WORK_AREA}\\${PIPE_BUILD_ID}"
        // Directory where installers are placed.
        PIPE_INSTALLERS_DIR = "${PIPE_BUILD_DIR}_Installers"
    }
}

// Perform test machine specific Setup and load execution environment.
def TestSetup(String testSuiteName, String testEngine) {
    InstallBTITools()

    // Define and set all possible environment variable, which would be used in consequent steps.
    // TODO: Parameterize OS name and site name (i.e. 'HTC').
    PIPE_TEST_SUITE_FOLDER_NAME = "Win1064LTSC_${testSuiteName}_${testEngine}_${NODE_NAME}_HTC"
    PIPE_TEST_RESULTS_DIR = "${WORKSPACE}\\${BTIENV_RELATIVE_TEST_RESULTS_SOURCE_PATH}\\${PIPE_TEST_SUITE_FOLDER_NAME}"
    PIPE_TEST_SUITE_ROOT_PATH = "${PIPE_BUILD_DIR}\\Testing"
    
    // Directory where test results to be published.
    PIPE_TARGET_TEST_RESULTS_PATH = "${BTIENV_TEST_RESULTS_DESTINATION}\\${BTIENV_PROJECT_FOLDER}\\${TEST_STAGE_NAME}\\${PIPE_BUILD_ID}\\${TEST_TYPE}\\${PIPE_TEST_SUITE_FOLDER_NAME}"
}

// Activities to be performed at end of the stage. Ex: Publish results
def TestWrapup() {
    // TODO: We can also copy all contents of publish directory.
    CopyFolder(PIPE_TEST_RESULTS_DIR, PIPE_TARGET_TEST_RESULTS_PATH)
    RunBatch("D:\\UTF\\TAE\\Regression\\Tools\\TestSuiteFinalizer.exe /rd=${PIPE_TARGET_TEST_RESULTS_PATH} /od=${PIPE_TARGET_TEST_RESULTS_PATH} /rawtofinal /cm=${PIPE_STASH_DIR}\\Build\\Source\\Shared\\InComponentMapping.csv")
}

// Initializes the pipeline by retrieving the context settings
// Sets the following environment variables:
//  - BTIENV_PROJECT_FOLDER
//  - BTIENV_RELEASE_CANDIDATE_FOLDER
def InitializePipeline() {
    GetBTISettingsFromConfigFile()
    OverwriteBTISettingsWithFolderProperties()
    GetJobTriggerCauses()
    // Set the result folders
    script {
        env.BTIENV_PROJECT_FOLDER = PrefixResultsFolderWithJenkinsFolders(JOB_NAME, BTIENV_PROJECT_IDENTIFIER)
        env.BTIENV_RELEASE_CANDIDATE_FOLDER = PrefixResultsFolderWithJenkinsFolders(JOB_NAME, BTIENV_RELEASE_CANDIDATE_FOLDER)
    }
    echo("Project folder: ${BTIENV_PROJECT_FOLDER}")
    echo("Release candidate folder: ${BTIENV_RELEASE_CANDIDATE_FOLDER}")
    // TODO: Temporary listing all environment variables to see what Jenkins env vars are available in production
    RunBatch("set")
}

// Gets the generic bti settings as environment variables
// Sets the following environment variables:
//  - BTIENV_ARTIFACTORY_URL
//  - BTIENV_ZIP_EXE
//  - BTIENV_TARGET_RELEASE_PATH
//  - BTIENV_RELEASE_CANDIDATE_DESTINATION
//  - BTIENV_RELEASE_CANDIDATE_FOLDER
//  - BTIENV_TEST_RESULTS_SHARE
//  - BTIENV_TEST_RESULTS_DESTINATION
//  - BTIENV_RELATIVE_TEST_RESULTS_SOURCE_PATH
//  - BTIENV_PROJECT_IDENTIFIER
def GetBTISettingsFromConfigFile() {
    // Get the generic bti settings as environment variables
    // TODO: Expected behavior is that plugin provides the setting of the environment variables when replaceTokens is switched on but somehow this did not happen!
    configFileProvider([configFile(fileId: 'GenericBTISettings', targetLocation: '\\Config\\bti_settings.xml', variable: 'BTI_SETTINGS', replaceTokens: false)]) {
        script {
            try {
                def fileContents = readFile("${env.BTI_SETTINGS}")
                def btiSettings = new XmlSlurper().parseText("${fileContents}")
                btiSettings.children().each {
                    echo "property name: ${it.name()}, value: ${it.text()}"
                }
                env.BTIENV_ARTIFACTORY_URL = btiSettings.BTIENV_ARTIFACTORY_URL.text()
                env.BTIENV_ZIP_EXE = btiSettings.BTIENV_ZIP_EXE.text()
                // TODO: Deprecated. 
                // Use combination of BTIENV_RELEASE_CANDIDATE_DESTINATION and BTIENV_RELEASE_CANDIDATE_FOLDER instead.
                env.BTIENV_TARGET_RELEASE_PATH = btiSettings.BTIENV_TARGET_RELEASE_PATH.text()
                env.BTIENV_RELEASE_CANDIDATE_DESTINATION = btiSettings.BTIENV_RELEASE_CANDIDATE_DESTINATION.text()
                env.BTIENV_RELEASE_CANDIDATE_FOLDER = btiSettings.BTIENV_RELEASE_CANDIDATE_FOLDER.text()
                // TODO: Deprecated. 
                // Use BTIENV_TEST_RESULTS_DESTINATION instead.
                env.BTIENV_TEST_RESULTS_SHARE = btiSettings.BTIENV_TEST_RESULTS_SHARE.text()
                env.BTIENV_TEST_RESULTS_DESTINATION = btiSettings.BTIENV_TEST_RESULTS_DESTINATION.text()
                // TODO: Temporary path for local generated test results
                env.BTIENV_RELATIVE_TEST_RESULTS_SOURCE_PATH = btiSettings.BTIENV_RELATIVE_TEST_RESULTS_SOURCE_PATH.text()
                // TODO: Temporary project property, is comparable with 'branch'
                env.BTIENV_PROJECT_IDENTIFIER = btiSettings.BTIENV_PROJECT_IDENTIFIER.text()
            } catch (error) {
                echo "Converting BTI settings to environment variables failed"
                throw new Exception(error.getMessage())
            }
        }
    }
}

// Gets the folder properties and overwrites the global environment variables
// Overwrites the following environment variable:
//  - BTIENV_TEST_RESULTS_DESTINATION
def OverwriteBTISettingsWithFolderProperties() {
    withFolderProperties {
        script {
            echo("Redefined test results share to be used: ${env.BTIENV_REDEFINED_TEST_RESULTS_SHARE}")
            // TODO: It looks like that properties provided by the plugin are not global, hence created a global!
            if (
                env.BTIENV_REDEFINED_TEST_RESULTS_SHARE!=null && 
                env.BTIENV_REDEFINED_TEST_RESULTS_SHARE.length()>0 && 
                !env.BTIENV_REDEFINED_TEST_RESULTS_SHARE.equalsIgnoreCase('null')
            ) {
                env.BTIENV_TEST_RESULTS_DESTINATION = env.BTIENV_REDEFINED_TEST_RESULTS_SHARE
            }
        }
    }
}

// Gets the causes that triggered the build
// Sets the following environment variables:
//  - PIPE_MANUALLY_TRIGGERED
//  - PIPE_TRIGGER_USERNAME
def GetJobTriggerCauses() {
    script {
        env.PIPE_MANUALLY_TRIGGERED = false
        env.PIPE_TRIGGER_USERNAME = ""
        def manualTriggeredSearchString = "started by user"
        def causes = currentBuild.rawBuild.getCauses()
        causes.each {
            def causeDescription = it.getShortDescription()
            echo "Cause that triggered the build: ${causeDescription}"
            userCauseIndex = causeDescription.toLowerCase().indexOf(manualTriggeredSearchString)
            if (userCauseIndex >= 0) {
                env.PIPE_MANUALLY_TRIGGERED = true
                env.PIPE_TRIGGER_USERNAME = causeDescription.substring(userCauseIndex + manualTriggeredSearchString.length()).trim().replace(", ", "")
            }
        }
        echo "Build is manually triggered?: ${PIPE_MANUALLY_TRIGGERED}"
        echo "User that triggered the build: ${PIPE_TRIGGER_USERNAME}"
    }
}

// Prefixes the results folder with the Jenkins folder structure. 
// Prefix depends on:
//  - PIPE_ENVIRONMENT, the environment the pipeline is running in, and 
//  - PIPE_TRIGGER_USERNAME, the cause that triggered this run
def PrefixResultsFolderWithJenkinsFolders(String jobName, String resultsFolder) {
    script {
        jenkinsFolders = GetPipelineEnvironment(jobName)
        if ((PIPE_MANUALLY_TRIGGERED  == 'true') && (PIPE_ENVIRONMENT == 'development')) {
            resultsFolder = "${PIPE_TRIGGER_USERNAME}\\" + resultsFolder
        }
        if (jenkinsFolders) {
            resultsFolder = jenkinsFolders + "\\" + resultsFolder
        }
        return resultsFolder
    }
}

// Gets the state of the pipeline. 
// Sets the following environment variables:
//  - PIPE_ENVIRONMENT, possible values are:
//      = production, pipeline is running in production environment
//      = staging, pipeline is running in staging environment
//      = development, pipeline is running in development environment
//      = other, pipeline is not running in one of the known environments
def GetPipelineEnvironment(String jobName) {
    script {
        env.PIPE_ENVIRONMENT = 'production'
        def jenkinsFolders
        // TODO: 'if (jobName) {}' should be sufficient but somehow it does not work!
        if (jobName!=null && jobName.length()>0 && !jobName.equalsIgnoreCase('null')) {
            // Extract the Jenkins folder structure from the JOB_NAME, e.g. folder1/folder2/foo, => JenkinsFolders = 'folder1/folder2'

            jenkinsFoldersLength = jobName.lastIndexOf("/")
            if (jenkinsFoldersLength >= 0) {
                // Job is running in a Jenkins folder, e.g. Staging, Development
                jenkinsFolders = jobName.substring(0, jenkinsFoldersLength)
                if (jenkinsFolders) {
                    jenkinsFolders.replaceAll('/', '\\')
                    if (jenkinsFolders.equalsIgnoreCase('staging')) {
                        env.PIPE_ENVIRONMENT = 'staging'
                    } else if (jenkinsFolders.equalsIgnoreCase('development')) {
                        env.PIPE_ENVIRONMENT = 'development'
                    } else {
                        env.PIPE_ENVIRONMENT = 'other'
                    }
                }
            }
        }
        return jenkinsFolders
    }
}

// Evaluates whether tests shall be executed
// Returns true when tests shall be executed, otherwise false
def EvaluateTestsToRun() {
    script {
        def execute = false
        if (
            params.runTests ||
            (
                // A boolean variable set as an environment variable seems automatically be converted to a string
                (PIPE_MANUALLY_TRIGGERED == 'false') &&
                (
                    (PIPE_ENVIRONMENT == 'production') ||
                    (PIPE_ENVIRONMENT == 'staging')
                )
            )
        ) {
            execute = true
        }
        echo "Evaluate tests to run: ${execute}"
        return execute
    }
}

// Extract build id from downloaded build results.
def GetBuildId() {
    try{
        RunBatch("""
            @echo off
            rem This is workaround to extract build id. When there are more than one
            rem build results are available, then only first item found will be considered.
            rem Assuming clean workspace, below works good.
            dir ${WORKSPACE}\\${PIPE_WORK_AREA} /B /A:D | findstr /I "^[0-9]+*" >TestResultsId.txt
            if errorlevel 1 (
                Echo Error:No builds found
                exit /b 1
            )
            echo "Test results ID"
            type TestResultsId.txt
        """)
        script {
            // Read only first line and ignore other lines.
            PIPE_BUILD_ID = readFile("${WORKSPACE}\\TestResultsId.txt").split("\n")[0].trim()

            // Deleting file shall not throw error in case the command is failed, since this is not a blocker for the current operation.
            bat returnStatus: true, script: "del TestResultsId.txt"
        }
        return PIPE_BUILD_ID

    } catch (error) {
        echo "Build id extraction failed"
        throw new Exception(error.getMessage())
    }
}

// Download build results.
def DownloadBuildResults(String TargetLocation) {
    try{
        // Perform JFRog configuration
        RunBatch("${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\jfrog.exe rt c ${ARTIFACTORY_ID} --apikey ${ARTIFACTORY_APIKEY} --url ${BTIENV_ARTIFACTORY_URL}")

        // Download latest capacity stage build
        // TODO: --server-id ${ARTIFACTORY_ID} shall be used with below command.
        RunBatch("${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\DownloadBuildResultsOfLatestPassedAcceptanceStage.bat --release-build-only --target-path ${TargetLocation}\\")

    } catch (error) {
        echo "Downloading build results failed"
        throw new Exception(error.getMessage())
    } finally {
        echo "Remove JFrog configuration settings"
        RunBatch("${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\jfrog.exe rt c --interactive=false delete ${ARTIFACTORY_ID}")
    }
}

// Download and install BTI-Tools.
def InstallBTITools() {
    try{
        // Perform JFRog configuration
        RunBatch("${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\jfrog.exe rt c ${ARTIFACTORY_ID} --apikey ${ARTIFACTORY_APIKEY} --url ${BTIENV_ARTIFACTORY_URL}")

        // Invoke script which downloads and installs BTI tools.
        // NOTE: The switch argument '-Latest' downloads tools from build share and without switch the tools
        // are downloaded from the Artifactory. In this case the switch argument '-Latest' is not used.
        RunPowershell("${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\InstallBtiTools.ps1 -ToolsList ${WORKSPACE}\\${PIPE_STASH_DIR}\\ToolsSynch\\ToolsList.xml -ReleasedToolsIndex ${WORKSPACE}\\${PIPE_STASH_DIR}\\ToolsSynch\\ReleasedToolsIndex.xml -ServerId ${ARTIFACTORY_ID} -JfrogExe ${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\jfrog.exe")
    } catch (error) {
        echo "Installing BTI tools failed"
        throw new Exception(error.getMessage())
    } finally {
        echo "Remove JFrog configuration settings"
        RunBatch("${WORKSPACE}\\${PIPE_STASH_DIR}\\DownloadBuildResults\\jfrog.exe rt c --interactive=false delete ${ARTIFACTORY_ID}")
    }
}

// Copies folders. Function takes care of long path issues
def CopyFolder(String Source, String Destination) {
    RunBatch("""
        @echo off
        robocopy ${Source} ${Destination} /e /np /r:3 /w:10
        if errorlevel 8 (
            rem It is a general approach to consider error codes 8 and higher as failing 
            exit /b 1
        )
        rem It is a general approach to consider error codes 0, 1, 2 and 4 as successful
        exit /b 0
    """)
}

def RunBatch(String command) {
    bat returnStatus: false, script:"${command}"
}
def RunPowershell(String command) {
    powershell script: "${command}"
}
def PublishInstallers() {
    CopyFolder("${PIPE_INSTALLERS_DIR}", "%BTIENV_TEST_RESULTS_DESTINATION%\\%BTIENV_PROJECT_FOLDER%\\${PIPE_BUILD_ID}")
}