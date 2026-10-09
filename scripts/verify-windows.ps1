Write-Output 'MERD Akma Windows setup audit — read-only'
$names = @('git','gh','java','javac','adb','sdkmanager','python','uv','specify','codex','claude','gemini','gradle')
foreach ($name in $names) {
  $cmd = Get-Command $name -ErrorAction SilentlyContinue
  if ($null -ne $cmd) { '{0,-14} {1}' -f $name,$cmd.Source }
  else { '{0,-14} MISSING' -f $name }
}
Write-Output ('ANDROID_HOME=' + $env:ANDROID_HOME)
Write-Output ('ANDROID_SDK_ROOT=' + $env:ANDROID_SDK_ROOT)
if (Get-Command adb -ErrorAction SilentlyContinue) { adb devices -l }
Write-Output 'No packages installed or configuration changed.'
