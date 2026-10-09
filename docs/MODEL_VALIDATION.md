# Model/runtimes feasibility gate — fill using observations

**Do not claim this gate has passed until evidence exists.** Priority: on-phone inference > overlay > polish.

## Candidate A — LiteRT-LM

- Reference: https://developers.google.com/edge/litert-lm/android
- Candidate model: compatible quantized Gemma 3 1B Instruct `.litertlm`; **NOT pinned/validated**. Check selected package bytecode/JDK requirement; do not assume every LiteRT-LM release is buildable with JDK 17.
- Choose actual published Maven version and exact model variant after runtime compatibility check. Prefer CPU initial smoke test if supported by selected model, then GPU only if validated. Never assume Helio G85 Mali GPU/NPU acceleration works.

## Candidate B — llama.cpp

- Reference: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- First small-model candidate: official **Qwen2.5-0.5B-Instruct-GGUF Q4_K_M**, Apache-2.0; inspect https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF . Model quality for Filipino/Taglish is **unverified**. Choose exact filename/hash/revision before distribution.
- **Important compatibility trap:** llama.cpp `examples/llama.android` currently has `minSdk=33`, so it is NOT drop-in compatible with Pova 2 Android 11/API 30. Use official NDK portable ARM64 build guidance and implement/test an API 30-compatible integration, or keep an API 30 inference spike via NDK CLI as a separate proof. Do not simply raise minSdk.
- Test modest context (e.g. 1024 tokens) and bounded output (e.g. 96 tokens) on the device; these are spike configurations, not performance claims. Do not bundle copyrighted or gated weights without license review.

## Model provenance and validation log

| Check | Status / observed value |
|---|---|
| Handset / hardware | Tecno Pova 2 LE7; Android 11 API 30; 6GB RAM, 128GB capacity (user reported); verify using ADB |
| Free storage / MemAvailable | NOT MEASURED |
| Model repo URL/revision/license acceptance | NOT SELECTED |
| Filename, bytes, SHA-256 | NOT SELECTED |
| Runtime dependency version/backend | NOT SELECTED |
| Load time after cold process launch | NOT MEASURED |
| Short HR reschedule draft latency | NOT MEASURED |
| Peak device process memory | NOT MEASURED |
| Three back-to-back generations | NOT TESTED |
| Airplane mode (Wi-Fi + mobile data OFF) | NOT TESTED |
| HR reschedule preserves user intent | NOT TESTED |

Suggested pass target: a truthful, useful short response ≤30 seconds **after warm-up**, 3 successful repeated runs, no crash/ANR, phone not swapping uncontrollably. This is an internal benchmark, not a product guarantee.

If it fails: reduce context/output length and choose a smaller supported model. Do not install a cloud fallback and call it local. CPU-only may be slow; record exact measured results and stop testing variants after the agreed timebox.
