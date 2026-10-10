param(
    [Parameter(Mandatory = $true)]
    [string]$ModelPath
)

# Gemma 4 E2B is larger than the 2 GiB APK-asset limit, so it is NOT bundled. This script verifies the
# model file against the pin, builds the APK without it, and prints the sideload commands.
$ErrorActionPreference = 'Stop'
$modelName = 'gemma-4-E2B-it.litertlm'
$expectedBytes = 2588147712L
$expectedHash = '181938105E0EEFD105961417E8DA75903EACDA102C4FCE9CE90F50B97139A63C'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$source = (Resolve-Path -LiteralPath $ModelPath).Path

if ((Get-Item -LiteralPath $source).Length -ne $expectedBytes) {
    throw 'Model size does not match the pinned artifact.'
}
if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Model SHA-256 does not match the pinned artifact.'
}
Push-Location $repo
try {
    & .\gradlew.bat --no-daemon :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
    $apk = Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk'
    Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Select-Object Path, Hash
} finally {
    Pop-Location
}
Write-Host 'Install the APK, open Akma once, then sideload the model:'
Write-Host '  adb shell mkdir -p /sdcard/Android/data/ph.merd.akma/files/models'
Write-Host "  adb push `"$source`" /sdcard/Android/data/ph.merd.akma/files/models/$modelName"
