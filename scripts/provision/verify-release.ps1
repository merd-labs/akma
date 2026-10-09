# Positional arguments match verify-release.sh. Compatible with Windows PowerShell 5.1 and PowerShell 7.
$ErrorActionPreference = 'Stop'
$javaCommand = 'java'
if ($env:JAVA_HOME) { $javaCommand = Join-Path $env:JAVA_HOME 'bin/java' }
try {
    & $javaCommand --source 17 (Join-Path $PSScriptRoot 'VerifyReleaseArtifact.java') @args
    exit $LASTEXITCODE
} catch {
    Write-Output 'FAIL: JDK 17 not available (details redacted)'
    exit 1
}
