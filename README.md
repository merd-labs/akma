# Akma — MERD / AppBuildersPH Hackathon 2026

**Status:** Akma hackathon documentation bootstrap (derived from the earlier ContextAI working-name package). No Android APK has been built here; no real local model inference has been verified.

**Pova 2 baseline:** Android 11/API 30, physical **6 GB RAM / 128 GB storage capacity** (MERD-reported). Free capacity and current available RAM remain to be measured with ADB. Do not claim 4GB compatibility.

> The right words, for the right context.

**Android application ID:** `ph.merd.akma` · **Repository name:** `akma` (organization slug must be supplied by the owner).

Android-first, privacy-conscious, user-invoked AI reply assistant. A user manually copies a message, opens a Akma overlay, pastes text, chooses a reply action/tone, and gets a draft from a model running **on the Android device**. The user edits, copies and pastes it back; Akma never sends messages automatically.

**MVP:** One end-to-end message → context → action → locally generated reply → manual copy path over one demonstrated messaging app. A genuine Android system keyboard, automatic thread capture, notification listening, AccessibilityService, cloud inference, login and backend are **out of scope**.

## Repository state and identity

The file bundle is ready for import; it is **not** a verified remote repository or Android build. Read `docs/BRAND_AND_PROVENANCE.md` for rename provenance and `docs/PUBLISH_GITHUB.md` before publishing. If a remote already exists, use `prompts/08_AKMA_REPO_MIGRATION.md` to preserve existing work.

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
3. See [docs/PUBLISH_GITHUB.md](docs/PUBLISH_GITHUB.md) for repo creation and [docs/ADB_POVA2.md](docs/ADB_POVA2.md) for measured device facts. Then Miguel runs [prompts/00_BOOTSTRAP_LEAD.md](prompts/00_BOOTSTRAP_LEAD.md) in a single agent to generate the **first compiling Android Studio/Gradle project**. Until that PR merges, this repository is docs-only.
4. Elijah, Miguel, Danielle and Rhence work through isolated prompts/branches; review [docs/CONTRACT.md](docs/CONTRACT.md) first.
5. Prove **real on-device offline generation** before investing in polish. Record results in [docs/MODEL_VALIDATION.md](docs/MODEL_VALIDATION.md).

## Building after Android bootstrap

Once an agent has generated and committed the Gradle wrapper, run:

**Linux/macOS Bash:** `./gradlew --no-daemon assembleDebug testDebugUnitTest`  
**Windows PowerShell:** ` .\gradlew.bat --no-daemon assembleDebug testDebugUnitTest`

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` after a successful build. All dependency versions must be pinned in generated Gradle files, and wrapper checksum/version documented.

No prebuilt APK, bundled model weights, or cloud AI endpoint exists in this bootstrap. The CI workflow reports documentation-only status until a Gradle wrapper exists.

## AI / Local AI disclosures (fill when actually used)

See [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md) and [docs/BRAND_AND_PROVENANCE.md](docs/BRAND_AND_PROVENANCE.md). We use AI coding agents, but generated changes require human review; do not claim all agents were used if they were not. Model names, exact weights, quantization, licensing and execution backend remain TBD pending device smoke tests.

## License

No project license chosen yet. Do not add a permissive license without team approval. Document third-party model/library licenses separately.
