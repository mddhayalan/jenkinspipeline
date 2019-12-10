
function CheckForSubBuildScripts($targetFilePath) {
  [xml]$readFile = Get-Content -Path $targetFilePath
  $readContent = $readFile.Project.ItemGroup.SubBuildScripts
  if ($null -eq $readContent) {
      if ($targetsList -notcontains $targetFilePath) {
        $targetsList.Add($targetFilePath)
      }
  }else {
      foreach ($item in $readContent) {
        if ($null -ne $item) {
          CheckForSubBuildScripts($item)
        }
      }
      $targetsList.Add($targetFilePath)
  }  
}

Add-Type -AssemblyName System.Collections
$targetsList = New-Object 'System.Collections.Generic.List[System.String]'
$rootPath = "C:\Views\trunk\"
$rootFile = "C:\Views\trunk\Build.targets"
[xml]$readXml = Get-Content -Path $rootFile
$MainBuildScripts = $readXml.Project.ItemGroup.BuildScripts
$codebaseTargetFiles = $MainBuildScripts.Include
foreach ($item in $codebaseTargetFiles) {
  $val = CheckForSubBuildScripts("$rootPath$item")
  if ($val) {
      Write-Host "$item contains sub build targets"
  }
}
