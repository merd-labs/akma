param([string]$Serial, [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) {
    Write-Output 'Usage: check-offline.ps1 -Serial SERIAL'
    Write-Output 'Read-only airplane/Wi-Fi/default-subscription data check. Keep selector private; never enable tracing.'
    exit 0
}
if ([string]::IsNullOrWhiteSpace($Serial)) { Write-Output 'FAIL: explicit private serial required'; exit 2 }
function Read-Adb([string[]]$Arguments) {
    try {
        $value = & adb -s $Serial @Arguments 2>$null
        if ($LASTEXITCODE -ne 0) { return 'UNKNOWN' }
        return ($value -join "`n").Trim()
    } catch { return 'UNKNOWN' }
}
if ((Read-Adb -Arguments @('get-state')) -cne 'device') { Write-Output 'NOT TESTED: authorized device unavailable'; exit 1 }
$airplane = Read-Adb -Arguments @('shell','settings','get','global','airplane_mode_on')
$wifi = Read-Adb -Arguments @('shell','settings','get','global','wifi_on')
$data = Read-Adb -Arguments @('shell','settings','get','global','mobile_data')
$subscription = Read-Adb -Arguments @('shell','settings','get','global','multi_sim_data_call')
$dataSource = 'legacy_global'
if ($subscription -match '^[0-9]{1,10}$') {
    $data = Read-Adb -Arguments @('shell','settings','get','global',"mobile_data$subscription")
    $dataSource = 'default_subscription'
}
$service = Read-Adb -Arguments @('shell','dumpsys','wifi')
$wifiDisabled = $service -match '(?m)^\s*Wi-Fi is disabled\s*$'
if ($airplane -cnotmatch '^[01]$') { $airplane = 'UNKNOWN' }
if ($wifi -cnotmatch '^[0123]$') { $wifi = 'UNKNOWN' }
if ($data -cnotmatch '^[01]$') { $data = 'UNKNOWN' }
Write-Output "airplane=$airplane" "wifi_setting=$wifi" "wifi_service_disabled=$($wifiDisabled.ToString().ToLowerInvariant())" "mobile_data=$data" "mobile_data_source=$dataSource"
if ($airplane -ceq '1' -and @('0','3') -contains $wifi -and $wifiDisabled -and $data -ceq '0') {
    Write-Output 'PASS: airplane enabled, Wi-Fi disabled, mobile data disabled; inference NOT TESTED'
    exit 0
}
Write-Output 'FAIL: offline radio state is not fully verified'
exit 1
