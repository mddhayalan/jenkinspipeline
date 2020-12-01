Set-Location 'C:\UIATWorkspace\SeleniumTool'
Start-Process seleniumServer.bat -PassThru
Start-Process seleniumNode.bat -PassThru
Set-Location 'C:\UIATWorkspace\BDDFramework\BDDFramework'
Start-Sleep -Seconds 5
mvn exec:java -Dsurefire.rerunFailingTestsCount=2


Add-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST/Default Web Site'  -filter "system.webServer/cors" -name "." -value @{origin='https://haproxy.smcty.honeywell.com:2828'} -name "failUnlistedOrigins" -value "True"

