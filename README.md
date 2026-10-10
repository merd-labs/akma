# Akma — MERD / Cerebral Valley Hackathon 2026

**Status:** Android app with a Compose Activity, a user-started floating overlay, an action catalog with second-confirmation gate, output-safety checks, and an on-device **Gemma 4 E2B (`.litertlm`, sideloaded)** engine running through LiteRT-LM 0.18.0 (CPU). **Release** builds use the real engine with a sideloaded model (see docs/model-provisioning/INTEGRATION.md); **debug** builds use a clearly-marked demo engine and are not evidence of inference. Unit tests (JVM, synthetic engine) cover the journey state machine; they do **not** prove native model behaviour. On-device results are recorded only in [docs/evidence](docs/evidence/) and [docs/MODEL_VALIDATION.md](docs/MODEL_VALIDATION.md); anything not recorded there is **NOT TESTED**.

**Pova 2 baseline:** Android 11/API 30, physical **6 GB RAM / 128 GB storage capacity** (MERD-reported). Free capacity and current available RAM remain to be measured with ADB. Do not claim 4GB compatibility.

> The right words, for the right context.

**Android application ID/namespace:** `ph.merd.akma` · **Repository:** [merd-labs/akma](https://github.com/merd-labs/akma)

Android-first, privacy-conscious, user-invoked AI reply assistant. A user manually copies a message, opens Akma, pastes text, chooses a reply action/tone, confirms, and gets a draft from a model running **on the Android device**. The user edits, copies and pastes it back; Akma never sends messages automatically.

## 📱 Physical Device Matrix

| Device | Android Version | API Level | Hardware | Status |
|--------|----------------|-----------|----------|--------|
| **Tecno Pova 2** | Android 11 | API 30 | Helio G85, 6GB RAM, 128GB | **Target (Primary)** |

The supplied ZIP was already extracted and committed, then deleted, as confirmed by the owner. Existing history is preserved. Read `docs/BRAND_AND_PROVENANCE.md` for rename provenance and `docs/PUBLISH_GITHUB.md` before any remote write. The local baseline still requires human review and merge.

## Team

- Miguel Harvey Velasco — Lead Engineering, Architecture, AI Runtime and Integration
- Elijah Jairus Castalla — Native Android Local AI Engineer
- Rhence Bryan Tavera — Product Owner, Functional QA and Demo Lead.
- Kurt Danielle Setenta — UI/UX and Android Frontend Lead

## Schedule (PHT)

- Oct 9 2026, 14:30 — official kickoff slide's authorized build start.
- Oct 10 2026, 10:00 — **hard submission deadline**. Internal target **09:00**.
- Only actual work made during the authorized period may be submitted; reusable external models/libraries must be credited and licensed.

## First actions

1. Read **[docs/NEXT_90_MINUTES.md](docs/NEXT_90_MINUTES.md)**, [docs/START_HERE.md](docs/START_HERE.md), and [docs/SPRINT.md](docs/SPRINT.md).
2. On Linux/Windows run `scripts/verify-linux.sh` or `scripts/verify-windows.ps1`; do not install/update everything blindly.
3. Build the baseline below, review [bootstrap verification](docs/BOOTSTRAP_VERIFICATION.md), then use [docs/ADB_POVA2.md](docs/ADB_POVA2.md) to measure device facts. Miguel owns Gradle, wrapper and manifest; Elijah reviews integration. Merge the reviewed baseline before dependent implementations.
4. Elijah, Miguel, Danielle and Rhence work through isolated prompts/branches; review [docs/CONTRACT.md](docs/CONTRACT.md) first.
5. Prove **real on-device offline generation** before investing in polish. Record results in [docs/MODEL_VALIDATION.md](docs/MODEL_VALIDATION.md).

## Building the Android baseline

Select JDK 17 before running the checked-in wrapper. Default Java 27 on the audited Ubuntu host is unsuitable for this pinned baseline. See [Ubuntu setup](docs/SETUP_UBUNTU.md) or [Windows setup](docs/SETUP_WINDOWS.md).

**Linux Bash:** `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest`
**Windows PowerShell:** `.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest`

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` after a successful build. Pins: Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, Compose BOM 2025.10.00, Activity Compose 1.11.0, Lifecycle 2.9.4, Coroutines 1.10.2, JUnit 4.13.2, SDK 36 and Build Tools 35.0.0; minSdk 30. Wrapper distribution checksum is in `gradle-wrapper.properties`.

No APK or model weights are committed (the `.litertlm` asset is git-ignored and must be placed in `app/src/main/assets/` before a release build). Android CI runs build and unit tests and fails if baseline files are missing. Documentation checks run separately. Configuring CI does not establish a passing hosted run.

## 🔒 Security, Privacy & On-Device Boundary

- **Offline inference by design:** the model file is copied to the phone once (sideloaded) and runs on the device; there is no cloud API, backend or telemetry in the app. Offline behaviour on hardware is **only claimed where recorded in `docs/evidence`**.
- **Manual control only:** no AccessibilityService, no notification listeners, no automatic sending, no silent clipboard capture. Copy happens only on an explicit tap, and only for drafts that pass the output-safety check unchanged.
- Security review and hardening notes: [docs/security](docs/security/).

## Quick start (demo phone)

1. JDK 17 and the checked-in wrapper: `./gradlew :app:assembleRelease` (the **release** variant runs the real engine; **debug** uses a labelled demo engine). Sign it for your test device (the release variant has no signing config) and `adb install -r` it.
2. Download `gemma-4-E2B-it.litertlm` (2,588,147,712 bytes, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`) from [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) and verify the hash. It is too large to bundle in an APK.
3. Open Akma once, then sideload: `adb shell mkdir -p /sdcard/Android/data/ph.merd.akma/files/models && adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/ph.merd.akma/files/models/`.
4. In Akma choose **Reply here instead** (or turn the bubble on). If the first model check ran before the push, tap **Try again**. The first load copies and verifies the 2.6 GB file; keep about 5.2 GB free on the phone until the copy finishes.
5. Paste a message, **Read message**, pick an action (you can switch action or tone until you confirm), **Write reply**, edit, **Copy reply**.

**Storage:** the 2.6 GB file is copied into the app's private storage and verified (SHA-256), then the sideloaded source is deleted, so the first load needs about 5.2 GB free and a phone keeps about 2.6 GB afterwards. If the model is missing or fails verification the app says "Model file not found. Copy gemma-4-E2B-it.litertlm into this app's models folder..." and **Try again** re-runs the check.

**Device log for measurements:** `adb logcat -d | grep AkmaInference` shows `provision_ms`, `avail_mb`/`total_mb`, `model_initialized_ms` and per-draft `generation_ms` (durations and sizes only, no message text).

Details: [docs/model-provisioning/INTEGRATION.md](docs/model-provisioning/INTEGRATION.md). Verify with `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:compileReleaseKotlin` (420 JVM tests, synthetic engine).

## Physical device matrix

The two physical test phones. "Specified" is the team-supplied spec sheet; "Observed" is what ADB reported on the unit that was actually tested (see [docs/DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md) for the third baseline, Tecno Camon 30, and per-phone risks).

| | **Tecno Pova 2 (LE7)** | **Infinix Zero 5G (X6815B)** |
|---|---|---|
| Role | Acceptance gate, **worst case** (design for the 4 GB variant) | Performance comparison, final-device gate |
| OS / skin (specified) | Android 11, HiOS 7.6 | Android 11, XOS 10 |
| Target API | 30 | 30 |
| **Observed OS** | Android 11 / API 30, arm64-v8a ([evidence](docs/evidence/device/POVA2_PHYSICAL_2026-10-09.md)) | **Android 12 / API 31** ([evidence](docs/evidence/device/zero5g-2026-10-10.md)) |
| SoC | MediaTek Helio G85, 12 nm | MediaTek Dimensity 900 5G, 6 nm |
| CPU | 2x Cortex-A75 @ 2.0 GHz + 6x Cortex-A55 @ 1.8 GHz | 2x Cortex-A78 @ 2.4 GHz + 6x Cortex-A55 @ 2.0 GHz |
| GPU / APIs | Mali-G52 MC2 (up to 1000 MHz), OpenGL ES 3.2, Vulkan 1.1 | Mali-G68 MC4, OpenGL ES 3.2, Vulkan 1.1 |
| RAM (specified) | 4 GB or 6 GB LPDDR4X | 8 GB LPDDR5 |
| **Observed RAM** | `MemTotal` 5,905,908 kB (a **6 GB** unit; the 4 GB variant is untested) | `MemTotal` 7,805,584 kB |
| Storage (specified) | 64 or 128 GB, **eMMC 5.1** (slow I/O) | 128 GB, UFS 3.1 |
| **Observed free storage** | about 8.1 GB free of 113 GB (93 % used) at preflight | about 56.3 GB free of 113 GB |
| Display | 6.9", 1080x2460, ~389 ppi, 60 Hz, 180 Hz touch, punch-hole | 6.78", 1080x2460, ~388-396 ppi, 120 Hz (8.33 ms/frame), punch-hole |
| Connectivity | Not in the supplied spec (inference is offline) | 5G SA/NSA, Wi-Fi 6 (offline inference does not use them) |
| Battery / charge | 7,000 mAh, 18 W | 5,000 mAh, 33 W |
| Background policy | HiOS: aggressive service/receiver/wakelock limits | XOS 10: app limits and freezing |
| Thermal | 12 nm: prone to steady throttling under sustained load | 6 nm: stable under sustained load |
| **Gemma 4 E2B result** | **NOT TESTED** | **Partial, recorded 2026-10-10:** 5 offline drafts, 4.8-7.4 s each, ~1.5 GB PSS after load, action/tone switching verified |

**Reading the matrix**
- Zero 5G: the only phone with a recorded Gemma 4 E2B run. Not yet measured there: model init time, peak memory, thermals, overlay, a Filipino-speaker review.
- Pova 2: no Gemma 4 E2B run is committed. Spec-derived risks: a ~1.7 GB model on a 4 GB phone under HiOS background killing; slow eMMC for the 2.6 GB copy and SHA-256 (init budget is 15 min); only ~8.1 GB free on the tested unit against a ~5.2 GB peak during first load (about 2.6 GB afterwards); slower and throttle-prone A75/A55 12 nm CPU. A 6 GB unit does not prove the 4 GB variant.
- The third baseline, Tecno Camon 30 (Android 14 / API 34 specified, Helio G99 Ultimate, 8 GB, UFS 2.2, 120 Hz), is **NOT TESTED** with this model; see [docs/DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md).

## 🧠 Model Integration Status

1. **Desktop model research:** Qwen2.5 1.5B Q8 was evaluated first in the desktop prompt lab (LiteRT-LM), then Qwen3-1.7B ([docs/evidence/qwen3-1p7b-latency.md](docs/evidence/qwen3-1p7b-latency.md)); Gemma 4 E2B replaced it for Filipino/Taglish quality ([docs/evidence/gemma4-e2b-filipino-gate.md](docs/evidence/gemma4-e2b-filipino-gate.md)).
2. **Android integration:** sideloaded `gemma-4-E2B-it.litertlm` (2,588,147,712 bytes, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`), loaded by `LiteRtReplyEngine`.
3. **Physical-device results:** Infinix Zero 5G (Android 12 / API 31, 7.8 GB RAM), 2026-10-10: Gemma 4 E2B loaded and produced 5 offline drafts in 4.8-7.4 s each, about 1.5 GB PSS after load, option switching verified ([evidence](docs/evidence/device/zero5g-2026-10-10.md)). Not yet measured: model init time, peak memory, thermals, overlay, Pova 2 / Camon 30 with this model, and a native-speaker review of Filipino/Taglish drafts. See [docs/MODEL_VALIDATION.md](docs/MODEL_VALIDATION.md); anything not recorded is **NOT TESTED**.
4. **Known limits:** drafts are suggestions and must be read before copying; Filipino/Taglish quality was only checked on a small synthetic set; the Analyze step is deterministic (keyword category, no model call), so the intent card says "Not classified. Showing general replies."

## AI / Local AI disclosures

See [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md) and [docs/BRAND_AND_PROVENANCE.md](docs/BRAND_AND_PROVENANCE.md). We use AI coding agents, but generated changes require human review; do not claim all agents were used if they were not. Model and runtime facts are in [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md).

## License

No project license chosen yet. Do not add a permissive license without team approval. Document third-party model/library licenses separately.
