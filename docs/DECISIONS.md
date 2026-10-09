# Architecture decisions and unresolved questions

| ID | Decision | Confidence |
|---|---|---|
| ADR-001 | Native Android Kotlin over cross-platform for Android overlay and simplicity | Accepted |
| ADR-002 | MainActivity Jetpack Compose, overlay Android Views | Accepted |
| ADR-003 | User-triggered overlay, explicit paste, manual copy; no AccessibilityService or automatic send | Accepted |
| ADR-004 | No backend, login, cloud AI or user content storage for hackathon | Accepted |
| ADR-005 | Min Android support: API 30 primary; exact `minSdk` subject to inference dependency | Provisional |
| ADR-006 | Local runtime & model selection (LiteRT-LM vs llama.cpp) | Blocked on device proof |
| ADR-007 | Two-stage local intent analysis + generation vs preset actions + local generation | Blocked on latency/quality |
| ADR-008 | Floating bubble always on? OEM/background lifecycle | Test and simplify |
| ADR-010 | Three displayed actions maximum; local category/ID/label catalog; sanitized, source-labeled analysis; separate human confirmation before every draft | Implemented on domain branch; review/integration pending |
| ADR-009 | Miguel owns the baseline configuration; JDK 17, Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, compile/target SDK 36, Build Tools 35.0.0, minSdk 30 | Accepted baseline; device/runtime compatibility unverified |

ADR-009: Miguel coordinates the bootstrap branch; Elijah reviews Android integration. The owner approved concurrent Android integration during bootstrap. Its pins are Compose BOM 2025.10.00, Activity Compose 1.11.0, Lifecycle 2.9.4, Coroutines 1.10.2 and Build Tools 35.0.0. The user-started overlay has a non-exported foreground service; permission recovery falls back to the Activity. See `BOOTSTRAP_VERIFICATION.md` for exact commands and results, and `SETUP_UBUNTU.md` for official compatibility sources. ADR-005 now uses minSdk 30 for the baseline; any future inference dependency must support API 30. Runtime/model selection remains blocked on Pova 2 evidence.

Change a decision with a new ADR, owner and validation evidence. Do not let agents silently choose conflicting packages, dependency versions, or model paths.

ADR-010: Miguel authorizes shared domain reconciliation; Elijah reviews before runtime/UI integrations build against it. Raw model labels never define selected meaning. `AnalysisResult.source` is assigned by the adapter and must be explicit. `ReplyCoordinator.draft` stages an immutable confirmation; a separate `confirmDraft(id)` consumes it before engine work. Every action requires confirmation. Unknown categories/IDs and malformed output fail; explicit Other presets are deterministic and never hide model failures. Existing UI confirmation controls remain deferred to Elijah/Danielle. Structural validation does not establish semantic faithfulness. See `CONTRACT.md` and `evidence/domain-action-safety.md`.
