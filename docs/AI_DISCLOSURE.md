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
| Model artifact | `Qwen3_1.7B.litertlm` from `litert-community/Qwen3-1.7B` (Hugging Face), 2,056,729,520 bytes, SHA-256 `66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c` | Bundled in the release APK (git-ignored asset) | Weights are third-party; the license must be confirmed from the model card before submission - **not verified by this record** |
| Gemma 3 1B Q4 | Desktop comparison only | Prompt lab research | Not shipped |
| Kotlin/AGP/Android SDK versions | Kotlin 2.4.0, AGP 8.11.1, Gradle 8.14, JDK 17, SDK 36, minSdk 30 | Android build | See `BOOTSTRAP_VERIFICATION.md` |
| AndroidX / unit tests | Compose, Activity Compose, JUnit 4.13.2 | Compose UI and domain validation | AndroidX Apache-2.0, JUnit EPL-1.0 |
| Existing open source assets/libraries | TBD | List provenance/licenses | To confirm |

## Requirements for Final Submission
- The README must name the exact technologies **actually used**.
- Must explicitly distinguish the local model executed on the phone from any Internet-required model download.
- Must disclose any reused existing resources, including their licenses.
- No fake benchmarks or unverified output claims are permitted.
