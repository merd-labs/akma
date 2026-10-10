param(
    [Parameter(Mandatory = $true)]
    [string]$ModelPath
)

$ErrorActionPreference = 'Stop'
$modelName = 'Qwen3_1.7B.litertlm'
$expectedBytes = 2056729520L
$expectedHash = '66064A4E9269CB693E124C4E3040BCB8A446B10BCA42663896329495ADD3861C'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$source = (Resolve-Path -LiteralPath $ModelPath).Path
$destination = Join-Path $repo "app/src/main/assets/$modelName"

if ((Get-Item -LiteralPath $source).Length -ne $expectedBytes) {
    throw 'Model size does not match the pinned artifact.'
}
if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Model SHA-256 does not match the pinned artifact.'
}
if (-not (Test-Path -LiteralPath $destination)) {
    Copy-Item -LiteralPath $source -Destination $destination
}
if ((Get-Item -LiteralPath $destination).Length -ne $expectedBytes -or
    (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Bundled model does not match the pin; an existing asset was not overwritten.'
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
