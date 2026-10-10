# Hardware baseline (provided by MERD; verify with ADB)

## Team-reported specifications (verify with ADB; observed values are in the status table below)

| | Tecno Pova 2 (LE7) | Infinix Zero 5G (X6815B) | Tecno Camon 30 (CL6) |
|---|---|---|---|
| Role | Primary acceptance gate, **worst case** | Performance comparison | Android 14 compatibility |
| OS / skin | Android 11, HiOS 7.6 | Android 11, XOS 10 (**unit observed: Android 12 / API 31**) | Android 14, HiOS 14 (earlier unit observed API 36) |
| Target API | 30 | 30 | 34 |
| SoC | MediaTek Helio G85, 12 nm | MediaTek Dimensity 900 5G, 6 nm | MediaTek Helio G99 Ultimate, 6 nm |
| CPU | 2x A75 @ 2.0 GHz + 6x A55 @ 1.8 GHz | 2x A78 @ 2.4 GHz + 6x A55 @ 2.0 GHz | 2x A76 @ 2.2 GHz + 6x A55 @ 2.0 GHz |
| GPU / APIs | Mali-G52 MC2, GLES 3.2, Vulkan 1.1 | Mali-G68 MC4, GLES 3.2, Vulkan 1.1 | Mali-G57 MC2, GLES 3.2, Vulkan 1.1 |
| RAM | 4 or 6 GB LPDDR4X; **design for 4 GB** (the unit tested had 6 GB) | 8 GB LPDDR5 | 8 or 12 GB LPDDR4X; 8 GB floor |
| Storage | 64 or 128 GB **eMMC 5.1** (slow I/O) | 128 GB UFS 3.1 | 256 GB UFS 2.2 |
| Display | 6.9", 1080x2460, 60 Hz, 180 Hz touch, punch-hole | 6.78", 1080x2460, 120 Hz (8.33 ms/frame), punch-hole | 6.78" AMOLED, 1080x2436, 120 Hz, punch-hole |
| Battery / charge | 7,000 mAh, 18 W | 5,000 mAh, 33 W | 5,000 mAh, 70 W |
| Background policy | HiOS: aggressive service/receiver/wakelock limits | XOS 10: app limits and freezing | HiOS 14: freezing, strict battery policy; Android 14 foreground-service types and runtime receiver rules |
| Thermal | 12 nm: prone to steady throttling under sustained load | 6 nm: stable under sustained load | 6 nm: reliable sustained efficiency |

## Gemma 4 E2B readiness per phone (2026-10-10)

Only the Zero 5G has a recorded run. The other rows are **spec-derived risks, not results**; do not read them as "works".

| Phone | Gemma 4 E2B status | Why / what could fail (from specs) |
|---|---|---|
| Infinix Zero 5G | **Partial, recorded:** 5 offline generations, 4.8-7.4 s, ~1.5 GB PSS post-run, option switching verified ([zero5g-2026-10-10.md](evidence/device/zero5g-2026-10-10.md)) | Not yet measured: init time, peak memory, thermals, overlay, clean English/Filipino runs. |
| Tecno Pova 2 | **NOT TESTED** | 4 GB variant: model needs ~1.7 GB RSS (model card, other chips), HiOS may kill it under memory pressure. eMMC 5.1: the 2.6 GB copy plus SHA-256 will be slow (init budget is 900 s). 64 GB variant: needs ~5.2 GB free at the peak of first load (the sideloaded file is deleted after a verified copy, leaving ~2.6 GB). Helio G85 (2x A75 + 6x A55, 12 nm) is slower than the Zero 5G and may throttle; expect longer drafts. Pass target unchanged: <= 30 s warm, 3 runs, no crash/ANR. A 6 GB unit does not prove the 4 GB variant. |
| Tecno Camon 30 | **NOT TESTED** | 8 GB and UFS 2.2 make memory and I/O unlikely blockers; Android 14 (API 34) foreground-service type and receiver rules apply to the overlay service, and an earlier unit reported API 36, so record the real API. Helio G99 (A76) should sit between Pova 2 and Zero 5G in speed (expectation, not measurement). |

Run the same device gate on each phone (README "Quick start"; capture `adb logcat -d | grep AkmaInference` for `provision_ms`, `model_initialized_ms`, `avail_mb/total_mb` and `generation_ms`, plus `dumpsys meminfo ph.merd.akma`).

## Measured baseline status (as of 2026-10-10)

The table above is team-reported. What has actually been measured:

| Device | Hardware snapshot (RAM / storage / thermal) | Offline generation | Details |
|---|---|---|---|
| Tecno Pova 2 LE7 | **Measured** (2026-10-09, API 30, MemTotal 5,905,908 kB) | NOT TESTED with the bundled model | [POVA2_PHYSICAL_2026-10-09.md](evidence/device/POVA2_PHYSICAL_2026-10-09.md) |
| Tecno Camon 30 CL6 | **NOT MEASURED**; observed Android API 36 (matrix says 14 / API 34) | Observed on earlier builds, incl. one failed Reschedule case; final release APK not run | [CAMON30_BASELINE.md](evidence/device/CAMON30_BASELINE.md) |
| Infinix Zero 5G X6815B | Partial: Android 12 / API 31, 7.8 GB RAM, 56 GB free (2026-10-10) | Gemma 4 E2B smoke test: 5 offline generations, 4.8-7.4 s each | [zero5g-2026-10-10.md](evidence/device/zero5g-2026-10-10.md) |

These specifications were **provided by the team**, not independently verified. Do not infer Vulkan/GPU inference availability from graphics API support alone. Rendering at 120Hz is not a requirement for inference speed; avoid UI stalls.

ADB (Bash / PowerShell):
```
adb devices -l
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell cat /proc/meminfo
adb shell df -h /data
```

A Pova 2 **6GB** test does NOT prove that its 4GB sibling variant will run the model. Record battery, model load time, warmed generation seconds and app memory (where measurable); use the same prompt/settings on all phones. Disable Wi-Fi/mobile data for actual inference smoke test. Beware HiOS/XOS background restrictions.
