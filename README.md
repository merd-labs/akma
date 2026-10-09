# Akma — MERD / Cerebral Valley Hackathon 2026

**Status:** The Android baseline (PR #4) is pending merge. `main` is currently documentation-only. 

> The right words, for the right context.

**Android application ID:** `ph.merd.akma` · **Repository name:** `akma`

Android-first, privacy-conscious, user-invoked AI reply assistant. A user manually copies a message, opens an Akma overlay, pastes text, chooses a reply action/tone, and gets a draft from a model running **on the Android device**. 

## 📱 Physical Device Matrix

| Device | Android Version | API Level | Hardware | Status |
|--------|----------------|-----------|----------|--------|
| **Tecno Pova 2** | Android 11 | API 30 | Helio G85, 6GB RAM, 128GB | **Target (Primary)** |

*(Note: Free capacity and current available RAM must be measured with ADB. Do not claim 4GB compatibility.)*

## 🔒 Security, Privacy & On-Device Boundary

- **Targeted 100% Offline Inference:** (NOT TESTED on Android) Model intended to run entirely on the device.
- **Targeted No Network Requests:** Aiming for no cloud APIs, no backend, no telemetry.
- **Manual Control Only:** No AccessibilityService, no notification listeners, no automatic sending, no silent clipboard capture.

## 🛠️ Setup & Build Prerequisites

**Supported OS:**
- Ubuntu 24.04 LTS
- Windows 11 Pro

**Prerequisites:**
- JDK 17 (exact requirement for Gradle and Android compatibility).
- Android SDK with API Level 30 installed.
- Git.

**Steps:**
1. Clone the repository: `git clone https://github.com/merd-labs/akma.git`
2. Run via command line (once Android baseline merges and wrapper exists):
   - **Ubuntu 24.04 Bash:** `./gradlew --no-daemon assembleDebug testDebugUnitTest`
   - **Win11 Pro PowerShell:** `.\gradlew.bat --no-daemon assembleDebug testDebugUnitTest`
3. Install via ADB: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

*(Note: MinSDK is API 30. Current `main` is documentation only.)*

## 🧠 Model Integration Status

We separate our model validation into three distinct phases to ensure truthfulness:

1. **Desktop Model Research:** **PASS** (Qwen2.5 1.5B Q8 verified on desktop prompt lab via LiteRT-LM. Size: ~1.59GB, SHA-256 and exact URL pending).
2. **Physical Android Tests:** **NOT TESTED**
3. **In-App Offline Inference:** **NOT TESTED**

When a model is integrated on Android:
1. Verify the model's license.
2. Place the weights manually into `app/src/main/assets/`.
3. Update `docs/AI_DISCLOSURE.md` with exact metadata.

## AI / Local AI Disclosures

See [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md). We use AI coding agents (Codex CLI, Claude Code, Google agents), but generated changes require human review. Model execution backend on Android remains TBD pending device smoke tests.

## Team

- Miguel Harvey Velasco — lead engineering, architecture, AI runtime and integration
- Elijah Jairus Castalla — Android overlay/integration and QA
- Rhence Bryan Tavera — requirements, coordination, QA and pitch
- Kurt Danielle Setenta (Danielle) — product design, Compose UI/Android Views and assets
