# Akma — MERD / Cerebral Valley Hackathon 2026

**Status:** Android app with a Compose Activity, a user-started floating overlay, an action catalog with second-confirmation gate, output-safety checks, and an on-device **Qwen3-1.7B (int8, `.litertlm`)** engine running through LiteRT-LM 0.18.0 (CPU). **Release** builds bundle the model and use the real engine; **debug** builds use a clearly-marked demo engine and are not evidence of inference. Unit tests (JVM, synthetic engine) cover the journey state machine; they do **not** prove native model behaviour. On-device results are recorded only in [docs/evidence](docs/evidence/) and [docs/MODEL_VALIDATION.md](docs/MODEL_VALIDATION.md); anything not recorded there is **NOT TESTED**.

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

- **Offline inference by design:** the model is bundled in the APK and runs on the device; there is no cloud API, backend or telemetry in the app. Offline behaviour on hardware is **only claimed where recorded in `docs/evidence`**.
- **Manual control only:** no AccessibilityService, no notification listeners, no automatic sending, no silent clipboard capture. Copy happens only on an explicit tap, and only for drafts that pass the output-safety check unchanged.
- Security review and hardening notes: [docs/security](docs/security/).

## 🧠 Model Integration Status

1. **Desktop model research:** Qwen2.5 1.5B Q8 was evaluated first in the desktop prompt lab (LiteRT-LM); Qwen3-1.7B replaced it for latency, see [docs/evidence/qwen3-1p7b-latency.md](docs/evidence/qwen3-1p7b-latency.md).
2. **Android integration:** bundled `Qwen3_1.7B.litertlm` (2,056,729,520 bytes, SHA-256 `66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c`), loaded by `LiteRtReplyEngine`.
3. **Physical-device results:** see [docs/MODEL_VALIDATION.md](docs/MODEL_VALIDATION.md); not-yet-recorded items are **NOT TESTED**.

## AI / Local AI disclosures

See [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md) and [docs/BRAND_AND_PROVENANCE.md](docs/BRAND_AND_PROVENANCE.md). We use AI coding agents, but generated changes require human review; do not claim all agents were used if they were not. Model and runtime facts are in [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md).

## License

No project license chosen yet. Do not add a permissive license without team approval. Document third-party model/library licenses separately.
