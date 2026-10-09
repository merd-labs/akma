# Device tooling validation — 2026-10-09

**Implemented:** read-only Bash and PowerShell snapshot collectors, manual benchmark instructions, and a physical-device evidence template. **Synthetic only:** transport fixtures used for local checks. **Not demonstrated:** phone inference, device performance, or GPU acceleration.

## Actual local checks

Host: Linux x86_64, Bash 5.2.21, PowerShell 7.6.6, Python 3.14.8. Commands run from the dedicated `test/device-benchmark` worktree.

| Command | Actual result |
|---|---|
| `bash -n scripts/bench/collect.sh` | Exit 0 |
| `bash scripts/bench/collect.sh --help` | Exit 0; usage printed |
| PowerShell parser command below | Exit 0; `PowerShell parser: PASS` |
| `pwsh -NoProfile -File scripts/bench/collect.ps1 -Help` | Exit 0; usage printed |
| `python3 scripts/bench/test_collectors.py` | Final run: exit 0, 8 test methods, 112.196 seconds, `OK` |
| `git diff --check` | Exit 0 |

Exact PowerShell parser command:

```bash
pwsh -NoProfile -Command '$tokens = $null; $errors = $null; [void][System.Management.Automation.Language.Parser]::ParseFile((Join-Path (Get-Location) "scripts/bench/collect.ps1"), [ref]$tokens, [ref]$errors); if ($errors.Count) { $errors | ForEach-Object { $_.Message }; exit 1 }; "PowerShell parser: PASS"'
```

The final suite exercises both collectors. It checks serial selection on every transport call, malformed arguments, emulator serials and QEMU properties, unauthorized/offline states, permission failures, serial and private-sentinel filtering, modern/legacy PSS parsing, model-name parity, missing metrics, package overrides, no overwrite, and bounded timeout reporting. Fixture numbers are synthetic and are not copied into handset evidence.

An expanded model-name assertion exposed a Bash filter failure during development. The filter was corrected; the final full suite above passes. No physical device or model was exercised by the suite.

## Measurement gaps

Read-only `adb devices` inspection found only an emulator. No physical collection was attempted, and no exclusive handset slot was claimed.

- Pova 2, Zero 5G, and Camon 30: **NOT RUN**.
- Real API/ABI, current available RAM, free disk, battery temperature, and OEM thermal permissions: **NOT MEASURED**.
- App startup, cold model load, first-token latency, generation time, token counts, repeated inference, crashes/ANRs, and heat during inference: **NOT MEASURED**.
- Peak process memory and transient throttling: cannot be established by before/after snapshots alone.
- GPU acceleration and genuinely offline phone inference: **NOT VERIFIED**.
- Native Windows PowerShell 5.1 / Windows PowerShell 7 / WSL execution: **NOT RUN**. PowerShell checks here used PowerShell 7 on Linux.
- Android build and unit tests: not run for this tooling-only change; no production, Gradle, manifest, or contract files changed.

## Review and next owner

The branch uses verified remote bootstrap commit `02df71ad7328e55e0762d3fde8f747ef44a04348`; bootstrap is not treated as merged. Human review remains required. Miguel reviews tooling and Git scope. Elijah coordinates the next exclusive physical-device slot and real runtime measurements. Use the template only after confirming physical hardware and obtaining the slot.
