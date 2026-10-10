# AI Disclosure Record — Complete Before Submission

**CURRENT STATUS: a local model is bundled and wired into release builds; on-device results count only where recorded in `docs/evidence`. Anything else is NOT TESTED. The model license has not been verified by this record.**

This template is intentionally NOT a claim that a tool/model was used. It must be updated with verifiable facts before the final submission.

| Item | Actual name/version/model | Human operator/usage | Status |
|---|---|---|---|
| Codex coding agent | Session version not recorded | Miguel-coordinated bootstrap, code review and verification | Used; human review remains required |
| Claude Code | Model/version not recorded per change | Miguel / Danielle (workflow, docs, security review, tests) | Used; human review remains required |
| Google AI agent(s) | Gemini 3.1 Pro (High) / Antigravity (as recorded by the contributor) | Miguel (workflow/docs) | Used for docs; human review remains required |
| Spec Kit | Specify CLI 1.1.1 | Read-only version/help audit | Integration deferred |
| Matt Pocock Skills | Not installed by this bootstrap | Miguel owns optional setup | Deferred |
| LiteRT-LM (Android) | `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, CPU | On-device inference in `LiteRtReplyEngine` | Integrated; physical-device results only as recorded in `docs/evidence` / `MODEL_VALIDATION.md` |
| LiteRT-LM (desktop prompt lab) | Python `litert-lm-api` | Prompt/evaluation research, not part of the APK | Observed on desktop only |
| Model artifact | `gemma-4-E2B-it.litertlm` from `litert-community/gemma-4-E2B-it-litert-lm` (Hugging Face; base `google/gemma-4-E2B-it`), 2,588,147,712 bytes, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | Sideloaded with `adb push` (ignored by version control; too large to bundle) | Weights are third-party; the model card lists Apache-2.0, to be confirmed by the owner before submission - **not verified by this record** |
| Gemma 3 1B Q4 | Desktop comparison only | Prompt lab research | Not shipped |
| Kotlin/AGP/Android SDK versions | Kotlin 2.4.0, AGP 8.11.1, Gradle 8.14, JDK 17, SDK 36, minSdk 30 | Android build | See `BOOTSTRAP_VERIFICATION.md` |
| AndroidX / unit tests | Compose, Activity Compose, JUnit 4.13.2 | Compose UI and domain validation | AndroidX Apache-2.0, JUnit EPL-1.0 |
| Existing open source assets/libraries | TBD | List provenance/licenses | To confirm |

## Requirements for Final Submission
- The README must name the exact technologies **actually used**.
- Must explicitly distinguish the local model executed on the phone from any Internet-required model download.
- Must disclose any reused existing resources, including their licenses.
- No fake benchmarks or unverified output claims are permitted.
