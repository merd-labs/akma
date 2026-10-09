param(
    [string]$Artifact,
    [string]$ExpectedBytes,
    [string]$ExpectedSha256,
    [switch]$Help
)
$ErrorActionPreference = 'Stop'
if ($Help) {
    Write-Output 'Usage: verify-artifact.ps1 -Artifact FILE -ExpectedBytes BYTES -ExpectedSha256 SHA256'
    Write-Output 'Use trusted owner metadata. Output contains status only; never enable tracing.'
    exit 0
}
if ($ExpectedBytes -notmatch '^[1-9][0-9]*$' -or $ExpectedSha256 -cnotmatch '^[a-f0-9]{64}$') {
    Write-Output 'FAIL: invalid trusted metadata'
    exit 2
}
try {
    $item = Get-Item -LiteralPath $Artifact -ErrorAction Stop
    if ($item.PSIsContainer -or $item.Length.ToString() -cne $ExpectedBytes) {
        Write-Output 'FAIL: size mismatch or non-file artifact'
        exit 1
    }
    $stream = [System.IO.File]::OpenRead($item.FullName)
    try {
        $header = New-Object byte[] 8
        $offset = 0
        while ($offset -lt 8) {
            $count = $stream.Read($header, $offset, 8 - $offset)
            if ($count -eq 0) { break }
            $offset += $count
        }
        if ($offset -ne 8 -or [System.Text.Encoding]::ASCII.GetString($header) -cne 'LITERTLM') {
            Write-Output 'FAIL: LiteRT-LM container signature mismatch'
            exit 1
        }
        $stream.Position = 0
        $hash = [System.Security.Cryptography.SHA256]::Create()
        try {
            $actualSha = [System.BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
        } finally { $hash.Dispose() }
    } finally { $stream.Dispose() }
    if ($actualSha -cne $ExpectedSha256) {
        Write-Output 'FAIL: SHA-256 mismatch'
        exit 1
    }
    Write-Output 'PASS: local size, container signature and SHA-256 verified; native inference NOT TESTED'
} catch {
    Write-Output 'FAIL: missing artifact or filesystem error (details redacted)'
    exit 1
}
