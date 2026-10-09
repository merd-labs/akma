# MERD — Next 90 minutes (start immediately)

**Status:** Prepared documentation package, NOT a built Android app or a tested model. All clocks below are execution timeboxes, not organizer deadlines.

## 0–10 min — shared coordination

- Miguel: confirm the actual GitHub org slug; create **one** private repository `akma`; extract bootstrap into repo; commit and push initial documentation (see `docs/PUBLISH_GITHUB.md`). No model weights/secrets.
- Elijah: own Android project skeleton/Gradle wrapper; verify installed Java/SDK/Android Studio. Do NOT select AGP/JDK blindly.
- Danielle: start design within `feat/assistant-ui` after baseline skeleton exists; until then, prepare design assets and explicit UI contract review.
- Rhence: enter three synthetic scenarios in GitHub Issues; create QA checks and presentation outline.

## 10–25 min — Pova 2 facts

Connect Pova 2 via USB with authorized debugging. See `docs/ADB_POVA2.md`. Verify model, API 30, ABI arm64-v8a, MemAvailable and **free /data space**. Hide ADB serial when sharing outputs. Avoid USB debug over untrusted PCs.

## 10–50 min — parallel spikes (after baseline branch created)

1. **Elijah**: Android project builds and launches on the Pova 2, with no fake model output. Add safe, narrow overlay permissions only when overlay feature is underway.
2. **Miguel**: Execute real quantized model **on the Pova 2 itself**. First check LiteRT-LM build compatibility; if blocked, try portable llama.cpp Android ARM64 NDK path and Qwen2.5-0.5B-Instruct-GGUF Q4_K_M (license Apache-2.0). Existing llama.android example requires API 33: not directly compatible. Do not waste time adapting a huge model to a 6 GB device.
3. **Danielle**: Create a usable Activity design with paste, action/tone, draft editing, clear model-ready/error states; use mock only with visible DEBUG label and never in submitted demo.
4. **Rhence**: Test message quality for invitation/reschedule, complaint/acknowledge, deadline/clarify; ensure generated text never invents dates/commitments.

## 50–90 min — gate and decision

- Android build installs on API 30 Pova 2; overlay works in chosen app OR Activity fallback is accepted.
- A real local model generates a relevant, faithful reply with **Wi-Fi and mobile data off**; record cold model load time, generation time, and 3 retries. If model fails, continue with a smaller supported GGUF or choose transparently simpler features; never substitute cloud output while claiming on-device inference.
- Miguel records observed device and model facts in `docs/MODEL_VALIDATION.md`. Do not mark an untested experiment as passed.

## Integration order

Android skeleton -> shared domain contracts -> real inference adapter -> Activity -> overlay. Do not run four agents writing shared Gradle/manifest/contracts simultaneously. Merge narrow reviewed branches; keep `main` installable.
