<#
Runs the Akma merged-manifest security guard on a built variant (Windows PowerShell 5.1+ / PowerShell 7).
Usage: .\scripts\security\check-merged-manifest.ps1 [-Variant debug] [-Release]
Build first, e.g. .\gradlew.bat :app:processDebugManifest
Exit: 0 pass, 1 policy violation, 2 usage/input error.
#>
param(
    [string]$Variant = 'debug',
    [switch]$Release
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (Resolve-Path (Join-Path (Join-Path $here '..') '..')).Path
$cap = $Variant.Substring(0, 1).ToUpper() + $Variant.Substring(1)
$dir = Join-Path (Join-Path (Join-Path (Join-Path $root 'app') 'build') 'intermediates') (Join-Path 'merged_manifests' $Variant)
$manifest = Join-Path (Join-Path $dir "process${cap}Manifest") 'AndroidManifest.xml'
if (-not (Test-Path -LiteralPath $manifest)) {
    $found = if (Test-Path -LiteralPath $dir) { Get-ChildItem -LiteralPath $dir -Recurse -Filter AndroidManifest.xml | Select-Object -First 1 } else { $null }
    if ($null -eq $found) {
        [Console]::Error.WriteLine("Merged manifest for variant '$Variant' not found. Run: .\gradlew.bat :app:process${cap}Manifest")
        exit 2
    }
    $manifest = $found.FullName
}
$exe = if ($env:OS -eq 'Windows_NT') { 'java.exe' } else { 'java' }
$javaCandidate = if ($env:JAVA_HOME) { Join-Path (Join-Path $env:JAVA_HOME 'bin') $exe } else { $null }
$java = if ($javaCandidate -and (Test-Path -LiteralPath $javaCandidate)) { $javaCandidate } else { 'java' }
$arguments = @((Join-Path $here 'ManifestGuard.java'), '--policy', (Join-Path $here 'manifest-policy.txt'))
if ($Release) { $arguments += '--release' }
$arguments += $manifest
& $java @arguments
exit $LASTEXITCODE
