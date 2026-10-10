# Model/runtimes feasibility gate — fill using observations

**Do not claim this gate has passed until evidence exists.** Priority: on-phone inference > overlay > polish.

## 🖥️ Desktop Model Research (Status: PASS)
Based on desktop verification (PR #16), we tested Python LiteRT-LM prompt labs for Akma workflows.

- **Primary Candidate:** Qwen2.5 1.5B Q8 `.litertlm`
  - **Local artifact size:** 1,597,931,520 bytes
  - **Runtime failures:** 0
  - **Generation Latency (Desktop):** ~2.27s
  - **Result:** Provisional leading candidate based on JSON parsing and intent-following checks. Exact model provenance, source URL, and SHA-256 are pending integration documentation.
- **Secondary Candidate:** Gemma 3 1B Q4 `.litertlm`
  - **Local artifact size:** 584,417,280 bytes
  - **Generation Latency (Desktop):** ~0.76s
  - **Result:** Fast, but produced repetitive responses and failed strict Taglish/Filipino validation in desktop probes.

*Note: Qwen Q8 vs Gemma Q4 is not a controlled quantization comparison. No human quality scores have been finalized. Desktop pass does NOT guarantee Android pass.*

## 🔄 Current model: Gemma 4 E2B (2026-10-10, supersedes the Qwen2.5 rows below)

- **Why:** Qwen3-1.7B (the interim choice) was incoherent in Filipino/Taglish regardless of prompt wording ([qwen3-1p7b-latency.md](evidence/qwen3-1p7b-latency.md)).
- **Artifact:** `gemma-4-E2B-it.litertlm`, [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) rev `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`, 2,588,147,712 bytes, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, Apache-2.0 per the model card. Sideloaded (above the 2 GiB APK asset limit).
- **Desktop gate (PASS, provisional):** 19 synthetic cases, LiteRT-LM 0.18.0 CPU: about 13 of 14 Filipino/Taglish drafts coherent and on-intent, mean 6.9 s versus 26.7 s for Qwen3-1.7B on the same machine; injection cases did not commit. Raw outputs and caveats: [gemma4-e2b-filipino-gate.md](evidence/gemma4-e2b-filipino-gate.md). Needs review by a Filipino speaker.
- **Device (Infinix Zero 5G, Android 12 / API 31):** 5 offline generations, 4.8-7.4 s, PSS about 1.5 GB post-run, no crash; option switching verified. Details and gaps: [device/zero5g-2026-10-10.md](evidence/device/zero5g-2026-10-10.md).
- **Still NOT TESTED:** Pova 2 and Camon 30 with this model, model init time, peak memory, thermals, overlay, native-speaker quality review.

## 📱 Physical Android Integration (Status: PARTIAL — Camon 30 only; Pova 2 acceptance gate still OPEN)
Do not claim Pova 2 (Tecno LE7, Android 11 / API 30) integration works. Offline generation with the bundled model was observed only on a **Tecno Camon 30 (CL6), Android API 36, ARM64** — a different device, and not the acceptance device. Rows below state what the linked evidence records; anything without a recorded observation is **NOT TESTED / NOT MEASURED**.

| Check | Status / observed value | Evidence |
|---|---|---|
| Target handset | Tecno Pova 2 LE7; Android 11 API 30; arm64-v8a; MemTotal 5,905,908 kB; /data free 8,224,300 KiB at preflight (2026-10-10) | [device/20261010-runtime-integrity.md](evidence/device/20261010-runtime-integrity.md) |
| Model artifact | `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`, [litert-community/Qwen2.5-1.5B-Instruct](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/tree/19edb84c69a0212f29a6ef17ba0d6f278b6a1614) rev `19edb84c69a0212f29a6ef17ba0d6f278b6a1614`, Apache-2.0 per that evidence; 1,597,931,520 bytes; SHA-256 `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9` | [android-inference-20261010.md](evidence/android-inference-20261010.md) |
| Runtime / backend | `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, CPU backend only (no GPU/NPU) | [android-inference-20261010.md](evidence/android-inference-20261010.md) |
| Offline generation on a physical device (Camon 30, API 36) | Observed with airplane mode on, Wi-Fi off, mobile data off: one relevant editable "Ask for details" draft, repeated on the final APK bytes of that run. Analysis 31.7–36.8 s; draft 21.3–22.3 s (first token 14.6–19.7 s). Warm init 1,105 ms. | [qwen-runtime-stability-20261010.md](evidence/qwen-runtime-stability-20261010.md) |
| Cold-process model initialization | 8,743 ms on Camon 30 for the upstream PR #26 build (copy time excluded); not measured for the final APK | [android-inference-20261010.md](evidence/android-inference-20261010.md) |
| Process memory | Post-run snapshots only (PSS ≈ 2.0–2.1 GB on Camon 30); **no peak measured** | both evidence files above |
| Short HR reschedule preserves user intent | **FAILED / unverified** — one Reschedule draft was a generic refusal; an earlier trial accepted the proposed time despite Reschedule | [qwen-runtime-stability-20261010.md](evidence/qwen-runtime-stability-20261010.md) |
| Unusable analysis output | Observed twice on a clearer interview invitation; Akma showed a recoverable error and claimed no draft | [qwen-runtime-stability-20261010.md](evidence/qwen-runtime-stability-20261010.md) |
| Multilingual (English / Filipino / Taglish) quality | Qwen2.5 build: **NOT TESTED**. Gemma 4 E2B: Taglish draft observed on Zero 5G; see the section above | [device/zero5g-2026-10-10.md](evidence/device/zero5g-2026-10-10.md) |
| Pova 2: load time, draft latency, peak memory | **NOT MEASURED** | — |
| Pova 2: three back-to-back generations | **NOT TESTED** | — |
| Pova 2: airplane mode (Wi-Fi + mobile data OFF) generation | **NOT TESTED** (radio-state probe passed; no inference attempted) | [device/20261010-runtime-integrity.md](evidence/device/20261010-runtime-integrity.md) |
| Final merged release APK (`main` `842b7b5`, 1,671,565,148 bytes, SHA-256 `2dd925c30166b10a42c072473b559bd5e685a63c0670562bc4ecf09a4fbae99a`) | Built, signed, JVM tests (400) green; **never run on any device** | Build/test numbers in [PR #39](https://github.com/merd-labs/akma/pull/39); no device evidence exists |

JVM unit tests use a synthetic engine and do not prove native model behaviour.

Suggested pass target: a truthful, useful short response ≤30 seconds **after warm-up**, 3 successful repeated runs, no crash/ANR, phone not swapping uncontrollably. This is an internal benchmark, not a product guarantee.
