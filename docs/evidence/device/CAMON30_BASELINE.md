# Tecno Camon 30 (CL6) baseline — recorded observations

**Status: PARTIAL, from existing evidence only.** This page collects what the repository's evidence files already record about the Camon 30 (`camon30`). No new measurement was taken for this page, no device was attached when it was written, and nothing here is a Pova 2 result. The Pova 2 baseline stays in [POVA2_PHYSICAL_2026-10-09.md](POVA2_PHYSICAL_2026-10-09.md).

## Identity (observed vs. team-reported)

| Item | Observed (source) | Team-reported ([DEVICE_MATRIX.md](../../DEVICE_MATRIX.md)) |
|---|---|---|
| Model | Tecno CL6 (Camon 30) ([android-inference-20261010.md](../android-inference-20261010.md)) | Tecno Camon 30 CL6 |
| Android | **API 36**, ARM64 (same source) | 14 / API 34, HiOS 14 — **differs from the observation**; the exact release was not recorded, so the matrix row is unconfirmed |
| SoC / RAM / storage | **NOT MEASURED** | Helio G99 Ultimate, 8 GB+ |

## Hardware readings

| Metric | Value | Status |
|---|---|---|
| `MemTotal` / `MemAvailable` | NOT MEASURED | no collector snapshot exists |
| Free `/data` | NOT MEASURED | |
| Battery temperature / thermal status | NOT MEASURED | |
| App total PSS, post-run | 2,003,974 kB (PR #26 build); 2,094,759 kB (final integrated build) | snapshots after generation, **not peaks** |
| Swap PSS, post-run | 50,166 kB; 73,832 kB | same builds, same caveat |

## Offline generation runs recorded on this device

Wi-Fi off and airplane mode on (`airplane_mode_on=1`) were recorded for both runs; mobile data was `0` for the integrated build and **not recorded** for the PR #26 build. Timings are single runs, UI-observed, with the boundaries stated in the source files.

| Build (APK SHA-256) | Scenario | Result | Source |
|---|---|---|---|
| PR #26 debug, `ddbf4439…e238c2` (1,676,607,448 B) | Reschedule, one tap | cold init 8,743 ms (copy excluded); warm init 960 ms; analysis 30,053 ms; draft 22,487 ms, 57 chars; an earlier prompt trial accepted the proposed time despite Reschedule | [android-inference-20261010.md](../android-inference-20261010.md) |
| Integrated `877b7276…d25782` | Friday interview, Reschedule, Professional | analysis 31,710 ms; draft 22,294 ms, but a generic refusal — **failed action fidelity** | [qwen-runtime-stability-20261010.md](../qwen-runtime-stability-20261010.md) |
| same | clearer job invitation | analysis produced unusable output twice (25,733 / 30,898 ms); recoverable error shown | same |
| same | Ask for details, Professional | analysis 33,421 ms; draft 21,270 ms, relevant and editable | same |
| Final integrated `fdcd5d75…cdcf9` (1,676,787,462 B) | Ask for details, Professional | analysis 36,800 ms (first token 17,884 ms); draft 21,473 ms (first token 18,898 ms); warm native init 1,105 ms | same |

Not recorded for this device: cold-process timing of the final build, peak memory, three back-to-back repeats, Filipino/Taglish output, ANR/crash audit, heat. English Reschedule fidelity: **failed in one run**, unverified otherwise.

## Not covered

- The merged release APK on `main` (SHA-256 `2dd925c30166b10a42c072473b559bd5e685a63c0670562bc4ecf09a4fbae99a`) has **never been run** on this or any device.
- A different APK ran on the earlier Pova 2 baseline; results do not transfer between devices or builds.

## To complete the baseline (needs the phone attached and an agreed slot)

```bash
bash scripts/bench/collect.sh --serial <PRIVATE_SERIAL> --device camon30 --phase baseline-before \
  --output <PRIVATE_DIR>/camon30-baseline-before.tsv --slot-confirmed
```

Then follow "Manual benchmark" in [scripts/bench/README.md](../../../scripts/bench/README.md) and copy [TEMPLATE.md](TEMPLATE.md). Commit only the reviewed TSV; never serials, raw ADB output, messages or weights.

## Team-reported specification (2026-10-10, not measured)

Android 14 / HiOS 14 (target API 34; foreground-service types, runtime receiver registration and granular media permissions matter), MediaTek Helio G99 Ultimate 6 nm (2x A76 @ 2.2 GHz + 6x A55 @ 2.0 GHz), Mali-G57 MC2, 8 or 12 GB LPDDR4X (8 GB floor), 256 GB UFS 2.2, 6.78" AMOLED 1080x2436 at 120 Hz with a punch-hole, 5,000 mAh with 70 W charging, HiOS app freezing and strict battery policy.

**Gemma 4 E2B on this phone: NOT TESTED.** See the readiness table in [DEVICE_MATRIX.md](../../DEVICE_MATRIX.md).
