# Official downloads, pinned to the exact files used for this module.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Force -Path .tools | Out-Null
function Get-Pinned([string]$Url, [string]$Path, [string]$Sha256) {
    if (-not (Test-Path -LiteralPath $Path)) { Invoke-WebRequest -Uri $Url -OutFile $Path }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Sha256) {
        throw "Checksum mismatch: $Path"
    }
}
Get-Pinned 'https://dl.google.com/android/repository/build-tools_r35_windows.zip' '.tools/build-tools.zip' '5753C679A1B90BCF6FBC9945A2CE39DFB9E74F1DF0831A1E1866AE5B594326F0'
Get-Pinned 'https://dl.google.com/android/repository/platform-35_r02.zip' '.tools/platform.zip' '0988CACAD01B38A18A47BAC14A0695F246BC76C1B06C0EEB8EB0DC825AB0C8E0'
Get-Pinned 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar' '.tools/xposed-api-82.jar' 'F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25'
Expand-Archive -LiteralPath .tools/build-tools.zip -DestinationPath .tools/sdk/build-tools -Force
Expand-Archive -LiteralPath .tools/platform.zip -DestinationPath .tools/sdk/platforms -Force
Write-Host 'Android SDK 35 and Xposed API 82 ready. Run ./build.ps1 with JDK 17 or later.'
