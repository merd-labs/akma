# Akma — MERD / AppBuildersPH Hackathon 2026

**Status:** Android baseline with a Compose Activity, user-started Views overlay panel, a local action catalog, a second-confirmation domain gate, reply coordinator and validation tests. UI confirmation controls still require separate integration; current action clicks stop safely at confirmation. Explicit paste and manual copy paths are implemented, but phone behavior is unverified. The engine reports **Model unavailable** and produces no AI drafts. See [bootstrap verification](docs/BOOTSTRAP_VERIFICATION.md) for actual evidence; real local inference remains planned.

**Pova 2 baseline:** Android 11/API 30, physical **6 GB RAM / 128 GB storage capacity** (MERD-reported). Free capacity and current available RAM remain to be measured with ADB. Do not claim 4GB compatibility.

> The right words, for the right context.

**Android application ID/namespace:** `ph.merd.akma` · **Repository:** [merd-labs/akma](https://github.com/merd-labs/akma), verified public during bootstrap inspection.

Planned product: Android-first, privacy-conscious, user-invoked AI reply assistant. A user manually copies a message, opens an Akma overlay, pastes text, chooses a reply action/tone, and gets a draft from a model running **on the Android device**. The user edits, copies and pastes it back; Akma never sends messages automatically.

**MVP:** One end-to-end message → context → action → locally generated reply → manual copy path over one demonstrated messaging app. A genuine Android system keyboard, automatic thread capture, notification listening, AccessibilityService, cloud inference, login and backend are **out of scope**.

## Repository state and identity

The supplied ZIP was already extracted and committed, then deleted, as confirmed by the owner. Existing history is preserved. Read `docs/BRAND_AND_PROVENANCE.md` for rename provenance and `docs/PUBLISH_GITHUB.md` before any remote write. The local baseline still requires human review and merge.

## Team

- Miguel Harvey Velasco — lead engineering, architecture, AI runtime and integration
- Elijah Jairus Castalla — Android overlay/integration and QA
- Rhence Bryan Tavera — requirements, coordination, QA and pitch
- Kurt Danielle Setenta (Danielle) — product design, Compose UI/Android Views and assets

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

No APK or model weights are committed. Android CI runs build and unit tests and fails if baseline files are missing. Documentation checks run separately. Configuring CI does not establish a passing hosted run.

## AI / Local AI disclosures (fill when actually used)

See [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md) and [docs/BRAND_AND_PROVENANCE.md](docs/BRAND_AND_PROVENANCE.md). We use AI coding agents, but generated changes require human review; do not claim all agents were used if they were not. Model names, exact weights, quantization, licensing and execution backend remain TBD pending device smoke tests.

## License

No project license chosen yet. Do not add a permissive license without team approval. Document third-party model/library licenses separately.
