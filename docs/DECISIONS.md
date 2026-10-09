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

Change a decision with a new ADR, owner and validation evidence. Do not let agents silently choose conflicting packages, dependency versions, or model paths.
