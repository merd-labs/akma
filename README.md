# Akma — MERD / AppBuildersPH Hackathon 2026

**Status:** Features currently marked as **deferred** or **not-tested**. The Android bootstrap code is located on the `chore/akma-bootstrap` branch. The `main` branch is currently for documentation only. No real local model inference has been verified yet.

> The right words, for the right context.

**Android application ID:** `ph.merd.akma` · **Repository name:** `akma`

Android-first, privacy-conscious, user-invoked AI reply assistant. A user manually copies a message, opens a Akma overlay, pastes text, chooses a reply action/tone, and gets a draft from a model running **on the Android device**. The user edits, copies and pastes it back.

## 📱 Physical Device Matrix

| Device | Android Version | API Level | Hardware | Status |
|--------|----------------|-----------|----------|--------|
| **Tecno Pova 2** | Android 11 | API 30 | Helio G85, 6GB RAM, 128GB | **Target (Primary)** |
| **Infinix Zero 5G** | Android 11 | API 30 | - | **Secondary** |
| **Camon 30** | Android 14 | API 34 | - | **Secondary** |

*(Note: Free capacity and current available RAM must be measured with ADB. Do not claim 4GB compatibility.)*

## 🔒 Security, Privacy & On-Device Boundary (Pending Verification)

- **Targeted 100% Offline Inference:** Model is intended to run entirely on the device (airplane mode supported, subject to verification).
- **Targeted No Network Requests:** Aiming for no cloud APIs, no backend, no telemetry.
- **Manual Control Only:** No AccessibilityService, no notification listeners, no automatic sending, no silent clipboard capture. The user manually copies and pastes text.
- **Data Boundary:** App data is intended to be isolated from outside the app (to be verified with integrated model).

## 🛠️ Setup (Ubuntu / Win11)

**Prerequisites:**
- Android Studio
- JDK 17+
- Git

**Steps:**
1. Clone the repository: `git clone <repo-url>`
2. Open in Android Studio or run via command line:
   - **Ubuntu/Linux Bash:** `./gradlew --no-daemon assembleDebug testDebugUnitTest`
   - **Win11 PowerShell:** `.\gradlew.bat --no-daemon assembleDebug testDebugUnitTest`
3. Install via ADB: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

*(Note: MinSDK is API 30. The CI workflow reports documentation-only status until a Gradle wrapper exists.)*

## 🧠 Reproducible Model Provisioning

No prebuilt APK or bundled model weights exist in this bootstrap.
When a model is selected:
1. Obtain the required model weights file (e.g., `.tflite` or `.gguf`).
2. Verify the model's license permits use.
3. Place the weights manually into `app/src/main/assets/`.
4. Update `docs/AI_DISCLOSURE.md` with the exact model name, weights, quantization, and license attribution.

## AI / Local AI Disclosures

See [docs/AI_DISCLOSURE.md](docs/AI_DISCLOSURE.md) and [docs/BRAND_AND_PROVENANCE.md](docs/BRAND_AND_PROVENANCE.md). We use AI coding agents, but generated changes require human review. Model names, exact weights, quantization, licensing and execution backend remain TBD pending device smoke tests.

## Team

- Miguel Harvey Velasco — lead engineering, architecture, AI runtime and integration
- Elijah Jairus Castalla — Android overlay/integration and QA
- Rhence Bryan Tavera — requirements, coordination, QA and pitch
- Kurt Danielle Setenta (Danielle) — product design, Compose UI/Android Views and assets

## License

No project license chosen yet. Do not add a permissive license without team approval. Document third-party model/library licenses separately.
