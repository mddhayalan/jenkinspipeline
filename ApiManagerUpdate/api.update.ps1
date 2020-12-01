$WORKSPACE = [System.Environment]::GetEnvironmentVariable('JENKINSWORKSPACE','machine')
$URL = [System.Environment]::GetEnvironmentVariable('APIURL','machine')
$BuildNumber = $env:BUILD_NUMBER
Write-Host "WORKSPACE set to $WORKSPACE"
Write-Host "URL is set to $URL"
Write-Host "BuildNumber is set to $BuildNumber"

Set-Location $WORKSPACE
$zipFiles = (Get-ChildItem -Path ".\$BuildNumber" -Filter "*.zip")
if ((Test-Path ".\Extracted")){
    Remove-Item ".\Extracted" -Force -Recurse
}
New-Item -Path ".\Extracted" -ItemType Directory
foreach ($file in $zipFiles) {
    $fullName = $file.FullName
    $fileName = Split-Path $file.Name
    Write-Host "unzipping "$fullName
    Expand-Archive -LiteralPath $fullName -DestinationPath ".\Extracted\$fileName\"
}
$folders = Get-ChildItem -Path ".\Extracted\" -File -Recurse -Filter "api.yaml"
foreach ($apiFiles in $folders) {
    $content = Get-Content -Path $apiFiles.FullName
    $content = $content -replace "dev.smcty.honeywell.com","$URL" 
	($content -join "`n") + "`n" | Set-Content -NoNewline $apiFiles.FullName
}
if ((Test-Path ".\Updated")){
    Remove-Item ".\Updated" -Force -Recurse
}
New-Item -Path ".\Updated" -ItemType Directory
$folders = Get-ChildItem -Path ".\Extracted\" -Directory
foreach ($folder in $folders) {
    $foldername = $folder.Name
    Compress-Archive -Path $folder.FullName -DestinationPath ".\Updated\$foldername"
}