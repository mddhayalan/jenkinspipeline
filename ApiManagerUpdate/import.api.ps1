$apiEnv = $env:APIENV
$WORKSPACE = $env:WORKSPACE
$BuildNumber = $env:BUILD_NUMBER

Write-Host "WORKSPACE set to $WORKSPACE"
Write-Host "apiEnv is set to $apiEnv"
Write-Host "BuildNumber is set to $BuildNumber"

Set-Location "C:\tools\apictl"
try {
    .\apictl login $apiEnv -u admin -p admin -k
    $zips = Get-ChildItem "$WORKSPACE\$BuildNumber" -Filter "*.zip" -File

    foreach ($file in $zips) {
        $fileName = $file.Name
        $fileFullName = $file.FullName
        Write-Host "Importing "+$fileName
        # $relPath = ".\QA\Updated\$fileName"
        .\apictl import-api -e $apiEnv -k --update -f $fileFullName
    }
}
catch {
    exit -1 
}