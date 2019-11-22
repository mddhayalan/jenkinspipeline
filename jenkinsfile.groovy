pipeline {
    agent {label 'fortifybuildserver' }
    options {
        buildDiscarder(logRotator(daysToKeepStr: '18'))
    }
    parameters {
        string defaultValue: 'Head', description: 'Capacity Passed Revision', name: 'Revision', trim: false
    }
    environment {
        FORTIFY_URL = "https://fortify.philips.com/ssc"
        FORTIFY_REPORT_LOCATION = "Output\\Publish\\Fortify\\"
    }
    stages{
        stage('Checkout/update source code') {
            options {
                timeout(time: 2, unit: 'HOURS')
                timestamps()
            }
            steps  {
                checkout changelog: false, 
                poll: false, 
                scm: [$class: 'SubversionSCM',
                additionalCredentials: [],
                excludedCommitMessages: '',
                excludedRegions: '',
                excludedRevprop: '',
                excludedUsers: '',
                filterChangelog: false,
                ignoreDirPropChanges: false,
                includedRegions: '',
                locations: [[cancelProcessOnExternalsFail: true,
                credentialsId: '5f139fb7-9e46-44a7-ac1e-fd010ec54cc4',
                depthOption: 'infinity',
                ignoreExternalsOption: false,
                local: '.',
                remote: 'https://svn-01.ta.philips.com/svn/icap-platform/trunk']],
                quietOperation: true,
                workspaceUpdater: [$class: 'CheckoutUpdater']]
            }
        }
        stage ('IPF Build') {
            options {
                timeout(time: 2, unit: 'HOURS')
                timestamps()
            }
            steps {
                script {
                    bat """%WORKSPACE%\\build.bat --server-build BIN"""
                }
            }
        }
        stage ('Fortify Build and Scan') {
            options {
                timeout(time: 5, unit: 'HOURS')
                timestamps()
            }
            steps {
                script {
                    //TODO: replace below call with dedicated build.bat option
                    RunBatch("""call %WORKSPACE%\\build_\\build\\vsvars.cmd
                    call msbuild %WORKSPACE%\\build.targets /t:_check /p:EnableFortifyCheck=true /p:DisableCodeAnalysis=true /p:UseSharedArtifactoryAuthentication=true /p:CheckLicenseBuild=false /p:DeploymentConfiguration=server
                    """)
                }
            }
        }
        stage('Upload Fortify Reports') {
            options {
                timeout(time: 1, unit: 'HOURS')
                timestamps()
            }
            steps{
                script {
                    def codebaseNames = ["AII", "AIP", "AppDev", "Build", "Common", "ManagedServices", "SII", "System"]                    
                    codebaseNames.each { 
                        codebaseName -> UploadCodebaseFortifyReport(codebaseName)
                        }
                    }
                    
                }
            }   
        }
}

def UploadCodebaseFortifyReport(String codebaseName) {
    script{
        withCredentials([string(credentialsId: "FortifyUploadToken-${codebaseName}", variable: 'TOKEN')]) {
            def reportFile = "${WORKSPACE}\\${codebaseName}\\${FORTIFY_REPORT_LOCATION}${codebaseName}Scan.fpr"
            def codebaseVersion = "HSDP-CP-${codebaseName}"
            echo "Uploading: ${reportFile} to ${codebaseVersion}"
            //TODO: Check if revision can be added as a param
            RunBatch("fortifyclient -url ${FORTIFY_URL} -authtoken ${TOKEN} uploadFPR -file ${reportFile} -project HSDP-CP -version ${codebaseVersion}")
        }
    }
}

def RunBatch(String command) {
    bat returnStatus: false, script:"${command}"
}