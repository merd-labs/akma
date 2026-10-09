# Read-only selected-device snapshots. Targets Windows PowerShell 5.1 and PowerShell 7.
[CmdletBinding()]
param(
    [string]$Serial = '',
    [string]$Device = '',
    [string]$Phase = '',
    [string]$Output = '',
    [string]$Package = 'ph.merd.akma',
    [switch]$SlotConfirmed,
    [switch]$Help
)
$ErrorActionPreference = 'Stop'
if ($Help) {
    @'
Usage: ./collect.ps1 -Serial SERIAL -Device pova2|zero5g|camon30
  -Phase LABEL -Output NEW_FILE -SlotConfirmed [-Package PACKAGE]

Read-only snapshot for a physical Android device. SERIAL is never saved.
Default package: ph.merd.akma. Phase: 1-32 letters, digits, underscores or hyphens.
-SlotConfirmed attests Elijah handed this device exclusively to you.
Output: UTF-8 TSV; existing files are never overwritten. Parent must exist.
Requires adb. Each ADB call has a 10-second limit.
No launch, benchmark, model load, settings change, clipboard read or log capture.
Do not use this collector during another operator's benchmark or Gradle build.
'@
    exit 0
}
function Stop-Collection([string]$Message) {
    [Console]::Error.WriteLine($Message)
    exit 1
}
if ($Serial -cnotmatch '^[A-Za-z0-9_.:-]+$') { Stop-Collection 'Valid explicit serial required.' }
if ($Device -cnotin @('pova2', 'zero5g', 'camon30')) { Stop-Collection 'Supported device alias required.' }
if ($Phase -cnotmatch '^[A-Za-z0-9_-]{1,32}$') { Stop-Collection 'Valid phase label required.' }
if ($Package -cnotmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') { Stop-Collection 'Invalid package.' }
if (-not $SlotConfirmed) { Stop-Collection 'Exclusive device handoff required; pass -SlotConfirmed after agreement.' }
if ($Serial.StartsWith('emulator-', [StringComparison]::OrdinalIgnoreCase)) { Stop-Collection 'Emulator rejected; physical device required.' }
try {
    if ([string]::IsNullOrWhiteSpace($Output)) { throw 'missing' }
    $OutputPath = [IO.Path]::GetFullPath($Output)
    if (-not [IO.Directory]::Exists([IO.Path]::GetDirectoryName($OutputPath)) -or (Test-Path -LiteralPath $OutputPath)) { throw 'invalid' }
} catch { Stop-Collection 'New output file in existing directory required.' }
$Adb = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $Adb) { Stop-Collection 'adb unavailable.' }

function Invoke-Adb([string[]]$AdbArguments) {
    $Process = New-Object Diagnostics.Process
    $Process.StartInfo.FileName = $Adb.Source
    $Process.StartInfo.UseShellExecute = $false
    $Process.StartInfo.CreateNoWindow = $true
    $Process.StartInfo.RedirectStandardOutput = $true
    $Process.StartInfo.RedirectStandardError = $true
    # Every argument is a fixed command or validated identifier; no shell or eval.
    $AllArguments = @('-s', $Serial) + $AdbArguments
    $Process.StartInfo.Arguments = ($AllArguments -join ' ')
    try {
        [void]$Process.Start()
        $Stdout = $Process.StandardOutput.ReadToEndAsync()
        $Stderr = $Process.StandardError.ReadToEndAsync()
        if (-not $Process.WaitForExit(10000)) {
            $Process.Kill()
            [void]$Process.WaitForExit(1000)
            return @{ Raw = ''; Status = 'timeout' }
        }
        if ($Process.ExitCode -ne 0) { return @{ Raw = ''; Status = 'query_failed' } }
        if (-not $Stdout.Wait(1000)) { return @{ Raw = ''; Status = 'timeout' } }
        return @{ Raw = $Stdout.Result.Replace("`r", '').TrimEnd("`n"); Status = 'ok' }
    } catch {
        return @{ Raw = ''; Status = 'query_failed' }
    } finally { $Process.Dispose() }
}
$Query = Invoke-Adb @('get-state')
if ($Query.Status -ne 'ok' -or $Query.Raw -ne 'device') { Stop-Collection 'Selected device unavailable or unauthorized.' }
foreach ($Property in @('ro.kernel.qemu', 'ro.boot.qemu')) {
    $Query = Invoke-Adb @('shell', 'getprop', $Property)
    if ($Query.Status -ne 'ok' -or $Query.Raw -notin @('', '0')) { Stop-Collection 'Physical device verification failed.' }
}
$Timestamp = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$Rows = New-Object 'Collections.Generic.List[string]'
function Add-Row([string]$Metric, [string]$Value, [string]$Unit, [string]$Status) {
    if ([string]::IsNullOrEmpty($Value) -and $Status -eq 'ok') { $Status = 'unavailable' }
    $Rows.Add((@($Timestamp, $Device, $Phase, $Metric, $Value, $Unit, $Status) -join "`t"))
}
function Add-Integer([string]$Metric, [string]$Value, [string]$Unit, [string]$Status) {
    if ($Value -cnotmatch '^[0-9]+$') { $Value = '' }
    Add-Row $Metric $Value $Unit $Status
}
$Query = Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')
Add-Integer 'android_api' $Query.Raw 'api' $Query.Status
$Query = Invoke-Adb @('shell', 'getprop', 'ro.build.version.release')
$Value = if ($Query.Raw -cmatch '^[0-9]+(\.[0-9]+)*$') { $Query.Raw } else { '' }
Add-Row 'android_release' $Value 'version' $Query.Status
$Query = Invoke-Adb @('shell', 'getprop', 'ro.product.cpu.abi')
$Value = if ($Query.Raw -cin @('arm64-v8a', 'armeabi-v7a', 'armeabi', 'x86', 'x86_64', 'riscv64')) { $Query.Raw } else { '' }
Add-Row 'abi' $Value 'name' $Query.Status
$Query = Invoke-Adb @('shell', 'getprop', 'ro.product.model')
$Value = $Query.Raw.Replace($Serial, '[REDACTED]')
if ($Value -cnotmatch '^[A-Za-z0-9][A-Za-z0-9 ._()\[\]-]{0,79}$') { $Value = '' }
Add-Row 'model' $Value 'name' $Query.Status
$Query = Invoke-Adb @('shell', 'cat', '/proc/meminfo')
foreach ($Entry in @(@('mem_total', 'MemTotal'), @('mem_available', 'MemAvailable'))) {
    $Value = if ($Query.Raw -match "(?m)^$($Entry[1]):\s+([0-9]+)\s+kB\s*$") { $Matches[1] } else { '' }
    Add-Integer $Entry[0] $Value 'KiB' $Query.Status
}
$Query = Invoke-Adb @('shell', 'df', '-k', '/data')
$Value = ''
foreach ($Line in $Query.Raw.Split("`n")) {
    $Fields = $Line.Trim() -split '\s+'
    if ($Fields.Count -ge 6 -and $Fields[-1] -eq '/data') { $Value = $Fields[-3]; break }
}
Add-Integer 'data_available' $Value 'KiB' $Query.Status
$Query = Invoke-Adb @('shell', 'dumpsys', 'battery')
$Value = if ($Query.Raw -match '(?m)^\s*temperature:\s*([0-9]+)\s*$') { $Matches[1] } else { '' }
Add-Integer 'battery_temperature' $Value 'deciC' $Query.Status
$Query = Invoke-Adb @('shell', 'dumpsys', 'thermalservice')
$Value = if ($Query.Raw -match '(?m)^\s*Thermal Status:\s*([0-9]+)\s*$') { $Matches[1] } else { '' }
Add-Integer 'thermal_status' $Value 'android_status' $Query.Status
$Query = Invoke-Adb @('shell', 'dumpsys', 'meminfo', $Package)
$Value = if ($Query.Raw -match 'TOTAL PSS:\s*([0-9]+)') { $Matches[1] }
    elseif ($Query.Raw -match '(?m)^\s*TOTAL\s+([0-9]+)\s') { $Matches[1] } else { '' }
Add-Integer 'app_total_pss' $Value 'KiB' $Query.Status
try {
    $Stream = New-Object IO.FileStream($OutputPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
} catch { Stop-Collection 'Cannot create new output file.' }
try {
    $Writer = New-Object IO.StreamWriter($Stream, (New-Object Text.UTF8Encoding($false)))
    $Writer.NewLine = "`n"
    $Writer.WriteLine("timestamp_utc`tdevice_alias`tphase`tmetric`tvalue`tunit`tstatus")
    foreach ($Row in $Rows) { $Writer.WriteLine($Row) }
    $Writer.Flush()
} finally {
    if ($Writer) { $Writer.Dispose() } else { $Stream.Dispose() }
}
Write-Output 'Snapshot saved. Review evidence before sharing.'
