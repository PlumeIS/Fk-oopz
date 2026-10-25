param(
    [string]$SdkRoot = "$PSScriptRoot\.tools\sdk",
    [string]$XposedApi = "$PSScriptRoot\.tools\xposed-api-82.jar"
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Set-Location -LiteralPath $PSScriptRoot

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed: $LASTEXITCODE" }
}

$buildTools = Join-Path $SdkRoot 'build-tools\android-15'
if (-not (Test-Path -LiteralPath $buildTools)) { $buildTools = Join-Path $SdkRoot 'build-tools\35.0.0' }
$androidJar = Join-Path $SdkRoot 'platforms\android-35\android.jar'
foreach ($required in @($androidJar, $XposedApi, "$buildTools\aapt2.exe")) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing $required; run ./setup-tools.ps1 first." }
}
# Remove only validated generated subdirectories; renamed packages must not leave stale DEX.
$buildRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))
foreach ($generated in @('classes','tests','dex')) {
    $generatedPath = [IO.Path]::GetFullPath((Join-Path $buildRoot $generated))
    if (-not $generatedPath.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Generated directory escaped build root'
    }
    if (Test-Path -LiteralPath $generatedPath) {
        $item = Get-Item -LiteralPath $generatedPath -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refuse to clean reparse point: $generatedPath" }
        Remove-Item -LiteralPath $generatedPath -Recurse -Force
    }
}
New-Item -ItemType Directory -Force -Path build,build/classes,build/tests,build/dex,dist,.signing | Out-Null

# Reuse the signing key across builds so Android can install module updates.
if (-not (Test-Path -LiteralPath '.signing\development.jks')) {
    Invoke-Checked 'keytool' @('-genkeypair','-keystore','.signing/development.jks',
        '-storepass','android','-keypass','android','-alias','fkoopz','-keyalg','RSA',
        '-keysize','2048','-validity','10000','-dname','CN=FkOopz Local Development','-noprompt')
}
$sources = @(Get-ChildItem -LiteralPath app/src/main/java -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked 'javac' (@('--release','8','-encoding','UTF-8','-classpath',"$androidJar;$XposedApi",'-d','build/classes') + $sources)
Invoke-Checked 'javac' @('--release','8','-encoding','UTF-8','-d','build/tests',
    'app/src/main/java/cn/plumc/fkoopz/SplashSession.java','app/src/main/java/cn/plumc/fkoopz/SplashCommands.java',
    'tests/cn/plumc/fkoopz/SplashSessionTest.java')
Invoke-Checked 'java' @('-cp','build/tests','cn.plumc.fkoopz.SplashSessionTest')
$fixtures = @(Get-ChildItem -LiteralPath tests/host -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked 'javac' (@('--release','8','-encoding','UTF-8','-classpath',"$androidJar;$XposedApi",'-d','build/tests') + $sources + $fixtures)
Invoke-Checked 'java' @('-cp',"build/tests;$androidJar;$XposedApi",'cn.plumc.fkoopz.HookWiringTest')
Invoke-Checked 'java' @('-cp',"build/tests;$androidJar;$XposedApi",'cn.plumc.fkoopz.CompatibilityTest')
# Only production classes enter module.jar; host fixtures stay under build/tests.
Invoke-Checked 'jar' @('--create','--file','build/module.jar','-C','build/classes','.')
Invoke-Checked "$buildTools\d8.bat" @('--release','--min-api','24','--lib',$androidJar,
    '--classpath',$XposedApi,'--output','build/dex','build/module.jar')
Invoke-Checked "$buildTools\aapt2.exe" @('compile','--dir','app/src/main/res','-o','build/resources.zip')
Invoke-Checked "$buildTools\aapt2.exe" @('link','-o','build/unsigned.apk','-I',$androidJar,
    '--manifest','app/src/main/AndroidManifest.xml','-A','app/src/main/assets','build/resources.zip')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkZip = [System.IO.Compression.ZipFile]::Open("$PSScriptRoot\build\unsigned.apk", 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkZip,
        "$PSScriptRoot\build\dex\classes.dex",'classes.dex') | Out-Null
} finally { $apkZip.Dispose() }
Invoke-Checked "$buildTools\zipalign.exe" @('-f','-p','4','build/unsigned.apk','build/aligned.apk')
Invoke-Checked "$buildTools\apksigner.bat" @('sign','--ks','.signing/development.jks',
    '--ks-key-alias','fkoopz','--ks-pass','pass:android','--key-pass','pass:android',
    '--out','dist/FkOopz-1.1.1.apk','build/aligned.apk')
Invoke-Checked "$buildTools\apksigner.bat" @('verify','--verbose','dist/FkOopz-1.1.1.apk')
Invoke-Checked "$buildTools\zipalign.exe" @('-c','-p','4','dist/FkOopz-1.1.1.apk')
$hash = (Get-FileHash -LiteralPath dist/FkOopz-1.1.1.apk -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  FkOopz-1.1.1.apk" | Set-Content -LiteralPath dist/SHA256SUMS.txt -Encoding ascii
Write-Host "Built: $PSScriptRoot\dist\FkOopz-1.1.1.apk"
