# Infinix Zero 5G (X6815B, Rhence's handset) baseline — status

**Status: NOT MEASURED.** No hardware reading for this handset exists in the repository. This page records what is known, why no snapshot has been taken, and the exact command to take one (`zero5g` alias in `scripts/bench`).

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
| Android API / release / ABI / model | NOT MEASURED |
| `MemTotal` / `MemAvailable` | NOT MEASURED |
| Free `/data` | NOT MEASURED |
| Battery temperature / thermal status | NOT MEASURED |
| App PSS, model load, analysis, generation, first token | NOT MEASURED |
| Offline generation (English / Filipino / Taglish) | NOT TESTED |
| Final merged release APK (`2dd925c3…bae99a`) | NOT TESTED; never installed or run on this handset |

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
