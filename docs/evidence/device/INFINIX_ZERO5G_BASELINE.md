# Infinix Zero 5G (X6815B, Rhence's handset) baseline — status

**Status: PARTIALLY MEASURED (2026-10-10).** A model-enabled smoke test ran on this handset; see [zero5g-2026-10-10.md](zero5g-2026-10-10.md). Items below still marked NOT MEASURED were not taken. This page records what is known, why no snapshot has been taken, and the exact command to take one (`zero5g` alias in `scripts/bench`).

## What is known (not measurements)

| Item | Value | Source |
|---|---|---|
| Model / platform | Infinix Zero 5G X6815B; Android 11 / API 30, XOS 10; Dimensity 900; 8 GB | team-reported, [DEVICE_MATRIX.md](../../DEVICE_MATRIX.md) |
| Role | Owner-reported **final-device gate** for the offline MVP; identity and hardware "still require observation" | [offline-mvp-release.md](../offline-mvp-release.md) |
| USB identity on the build host (2026-10-10) | MediaTek `0e8d:201c`, listed as "Infinix X6815B" | host `lsusb`, not a device query |
| ADB state on the build host | **`no permissions`** — the USB node is `root:root` and no udev rule covers vendor `0e8d`, so no ADB query was possible | host `adb devices` |

## Readings

| Metric | Value |
|---|---|
| Android API / release / model | **API 31 / Android 12** / Infinix X6815B (ABI not read) |
| `MemTotal` / `MemAvailable` | 7,805,584 kB / NOT MEASURED |
| Free `/data` | about 56.3 GB |
| Battery temperature / thermal status | 30.1 C (USB powered) at one point; thermal status NOT MEASURED |
| App PSS, model load, analysis, generation, first token | PSS 1.51-1.53 GB post-run; generation 4.8-7.4 s; first text 3.0-4.6 s; analysis has no model call; model load NOT MEASURED |
| Offline generation (English / Filipino / Taglish) | Taglish draft recorded; English/Filipino journeys not driven by the tester; owner reported working flows (not independently captured) |
| Final merged release APK (`2dd925c3…bae99a`) | NOT TESTED; superseded by the Gemma 4 E2B build below |
| Gemma 4 E2B build (release, debug-signed, no model in APK, SHA-256 `de9f10c9…3a8d`) | Installed, model sideloaded, **5 offline generations observed**; see [zero5g-2026-10-10.md](zero5g-2026-10-10.md) |

Earlier Pova 2 results and the Camon 30 observations do not establish anything for this handset.

## To measure (read-only snapshot)

1. Make the device reachable by ADB (one-time, needs sudo on the host):
   ```bash
   echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="0e8d", MODE="0666", GROUP="plugdev"' | sudo tee /etc/udev/rules.d/52-mtk.rules
   sudo udevadm control --reload-rules && sudo udevadm trigger
   ```
2. Confirm the handset owner has agreed an exclusive slot, then:
   ```bash
   bash scripts/bench/collect.sh --serial <PRIVATE_SERIAL> --device zero5g --phase baseline-before \
     --output <PRIVATE_DIR>/zero5g-baseline-before.tsv --slot-confirmed
   ```
3. Review the TSV, commit only that file under `docs/evidence/device/zero5g-<date>/`, and fill [TEMPLATE.md](TEMPLATE.md). The collector never launches the app, loads the model or changes device state.
