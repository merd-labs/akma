param(
    [Parameter(Mandatory = $true)]
    [string]$ModelPath
)

$ErrorActionPreference = 'Stop'
$modelName = 'Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm'
$expectedBytes = 1597931520L
$expectedHash = 'FAA60663B333290C1496C499828B21D3E3254A788CACD8CCE917CE0F761A2DC9'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$source = (Resolve-Path -LiteralPath $ModelPath).Path
$destination = Join-Path $repo "app/src/main/assets/$modelName"

if ((Get-Item -LiteralPath $source).Length -ne $expectedBytes) {
    throw 'Model size does not match the pinned artifact.'
}
if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Model SHA-256 does not match the pinned artifact.'
}
if ($source -ne $destination) {
    Copy-Item -LiteralPath $source -Destination $destination
}
if ((Get-Item -LiteralPath $destination).Length -ne $expectedBytes -or
    (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Bundled model copy failed verification.'
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
