# Selected-device snapshots

These scripts collect read-only snapshots from one physical Android device. They do not launch the app, load a model, generate text, or prove offline inference. Bash requires Linux/WSL, `adb`, and GNU `timeout`. PowerShell targets Windows PowerShell 5.1 and PowerShell 7; each host still needs validation recorded separately.

## Reserve the device

Ask Elijah for an exclusive slot before collection or inference. Agree on the handset, operator, start time, and release time. Record the agreement in the evidence template. `--slot-confirmed` / `-SlotConfirmed` is the operator's attestation; it is not a cross-host lock. Do not collect during Elijah's benchmark or run concurrent Gradle builds. Stop if the device becomes unresponsive, shows a temperature warning, or the operator feels unsafe heat. Release the slot after testing.

Select the serial privately from your authorized ADB setup. Do not paste device inventory or an actual serial into an issue, PR, evidence file, or screenshot. Only synthetic serials appear in the examples below.

## Run a snapshot

Create a private output directory outside the repository. Its parent must already exist. Choose a new output file for every snapshot; overwriting is refused.

```bash
bash scripts/bench/collect.sh --help
bash scripts/bench/collect.sh --serial SYNTHETIC_SERIAL --device pova2 \
  --phase cold-before --output /private/existing/cold-before.tsv --slot-confirmed
```

```powershell
./scripts/bench/collect.ps1 -Help
./scripts/bench/collect.ps1 -Serial SYNTHETIC_SERIAL -Device pova2 `
  -Phase cold-before -Output 'C:\private\existing\cold-before.tsv' -SlotConfirmed
```

Aliases are `pova2`, `zero5g`, and `camon30`; these are operator labels, not hardware verification. Phases allow 1–32 letters, digits, underscores, or hyphens. The default package is `ph.merd.akma`. Use `--package` / `-Package` only when the installed APK has a different verified application ID. Serial selectors support simple USB serials and IPv4 ADB endpoints; whitespace, IPv6 brackets, and shell metacharacters are rejected.

The scripts reject emulator serials and QEMU properties. This excludes the usual Android emulators; it is not cryptographic proof of physical hardware. Confirm the handset manually. Unauthorized, offline, disconnected, or unverifiable devices produce no snapshot.

## Output and limits

Both scripts write UTF-8 TSV with LF newlines and columns `timestamp_utc`, `device_alias`, `phase`, `metric`, `value`, `unit`, and `status`. The timestamp identifies the snapshot start; individual reads are sequential, not simultaneous. Each ADB call has a 10-second limit. A complete snapshot can take longer when several services time out.

Statuses are `ok`, `unavailable`, `query_failed`, and `timeout`. Missing values remain blank. Hardware readings can still be collected when the app is absent or stopped. A snapshot with missing metrics is partial evidence, not a successful model benchmark.

Collected metrics:

- Android API, release, primary ABI, and sanitized model name.
- `MemTotal` and `MemAvailable` from `/proc/meminfo`, in KiB. These are system readings, not a claim about exact installed physical RAM.
- Free `/data` space from `df -k`, in KiB. The Pova 2 OEM output can label the queried filesystem `/data/user/0`; both mount labels are accepted. This is distinct from marketed storage capacity.
- Battery temperature in tenths of degrees Celsius and Android thermal status, where exposed. Battery temperature is not CPU/GPU temperature.
- App total PSS from package-specific `dumpsys meminfo`, in KiB, where available. A snapshot is not peak memory; it cannot establish no memory pressure between samples.

Android thermal status values run from 0 (none) through 6 (shutdown). OEM output and permissions vary; unavailable thermal data does not mean no throttling. No graphics capability or thermal reading establishes GPU inference.

Raw ADB stdout/stderr, serials, package inventories, prompts, drafts, and crash logs are never saved. The scripts extract only approved fields. Review output before sharing; automatic filtering does not replace human review. Bash creates files with restricted permissions. On Windows, use an output directory with suitable access controls. Do not commit private messages, model weights, or unreviewed artifacts.

## Manual benchmark

Use `docs/evidence/device/TEMPLATE.md`. Keep Wi-Fi and mobile data off manually after provisioning. Record how offline conditions were checked; a snapshot alone cannot prove no network inference.

1. Record the exclusive slot, build SHA, model provenance, runtime/backend, and identical generation settings.
2. Take `cold-before`. Manually launch the app from an operator-confirmed stopped process. Time app readiness separately from explicit model initialization. Do not force-stop or flush caches with the collector. If process-cold conditions cannot be confirmed, label the run accordingly.
3. Use a synthetic HR invitation and select Reschedule with Professional tone. Time generation from the user action to the completed editable draft. Record analysis separately if the app performs it.
4. Take `cold-after`, then `warm-1-before` / `warm-1-after` through `warm-3-before` / `warm-3-after` around three consecutive generations. Record every attempt, including failures and interrupted runs.
5. Observe crash/ANR dialogs, responsiveness, warnings, and heat. An operator observation is not a full crash-log audit. Record `NOT OBSERVED`, not an unsupported guarantee of no crashes.
6. Record first-token latency, token counts, and throughput only when the actual runtime exposes trustworthy measurements. A final-only UI cannot establish first-token timing. Words and characters are not tokens. Mark gaps explicitly.

A process-cold run can still use warm filesystem caches. These scripts never clear caches or change device state. Current baseline engine reports model unavailable; record that condition and leave inference timings unmeasured until a real engine exists.

## Local checks

```bash
bash -n scripts/bench/collect.sh
bash scripts/bench/collect.sh --help
python3 scripts/bench/test_collectors.py
git diff --check
```

The Python check invokes both collectors using a temporary fake ADB executable. Its fixture values are synthetic test data, never handset or AI evidence. PowerShell must be available for both-host checks; absence is reported as a skipped check. Real Windows execution, OEM permission behavior, and physical-device generation remain separate acceptance gates.
