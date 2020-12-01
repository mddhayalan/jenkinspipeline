
pipeline {
    agent {label 'UATAgent' }
    options {
        buildDiscarder(logRotator(daysToKeepStr: '18'))
    }
    stages
    {
        stage('Run API Tests') 
        {
            options {
                timeout(time: 1, unit: 'HOURS')
                timestamps()
            }
            steps  {
                powershell label: 'Run API Tests',
                script: '''$workingDir = \'C:\\Jenkins\\Workspace\\apitest\'
                           $path =  \'C:\\Jenkins\\Workspace\\workspace\\SmartCity_API_Tests\\TestResults\\'
                            Write-Host "$path"
                           if (Test-Path $path) {
                              Remove-item $path -recurse
                            }'''
                RunBatch("""dotnet test %WORKSPACE%\\..\\..\\apitest\\*.tests.dll --logger:trx""")
            }
            post 
            {
                always 
                {
                      powershell label: 'Format Report', 
                      returnStatus: true, 
                      script: '''$path = \'C:\\Jenkins\\Workspace\\workspace\\SmartCity_API_Tests\\TestResults\\'
                           $toolspath = \'C:\\Tools\\TrxerConsole.exe\'
                           $trxFile = Get-ChildItem -Path $path -Filter "*.trx" | select FullName
                           $trxFileName = $trxFile.FullName                           
                           & $toolspath $trxFileName
                           $newIndexFile = $path + "..\\index.html"
                           $indexFile = \'C:\\inetpub\\wwwroot\\TestDashBoard\\index.html\'
                           if (Test-Path $indexFile) {
                              Remove-item $indexFile -Force
                            }
                           Copy-Item -Path $newIndexFile -Destination \'C:\\inetpub\\wwwroot\\TestDashBoard\\index.html\' -Force'''
                }
            }
        }
    }
}

def RunBatch(String command) {
    bat returnStatus: false, script:"${command}"
}