# Security hardening handoff (2026-10-10 ~01:30 PHT)

Branch `fix/security-hardening` (base `main` `3edd10f`, which already contains #4, #13, #14, #17, #12). Draft PR: see GitHub.
Everything below is observed output unless labelled otherwise. Unrun items are **NOT TESTED**.

## 1. Production code and tests added (all new files; no owner's file edited)

| File | Kind | Tests |
|---|---|---|
| `app/src/main/java/ph/merd/akma/safety/ModelOutputSafety.kt` | output sanitiser, prompt-input neutraliser, safe failure mapping | `ModelOutputSafetyTest` 46 |
| `scripts/security/ManifestGuard.java` + `manifest-policy.txt` + `check-merged-manifest.sh/.ps1` | merged-manifest guard | `ManifestGuardTest` 6 (runs inside `testDebugUnitTest`) |
| `app/src/test/resources/manifests/merged-clean-debug.xml` | fixture (copy of the real merged debug manifest) | — |
| `docs/security/*` | integration guide, patches, evaluation, checklist, probe sources | — |

Final gate on this branch (JDK 17, `--offline`): `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → **BUILD SUCCESSFUL, exit 0;
159 tests, 0 failures, 0 errors, 0 skipped; lint 0 errors, 11 warnings** (main alone has 107 tests; these add 52). Hosted CI: see PR checks.
`gitleaks` on `origin/main..HEAD`: no leaks.

## 2. Bugs reproduced

| # | Bug | Reproduction (observed) | Status |
|---|---|---|---|
| B1 | Native `Error` (`UnsatisfiedLinkError`, `NoClassDefFoundError`, `OutOfMemoryError`) escapes `ReplyCoordinator.process` (`catch (Exception)`) | `NativeErrorResilienceTest`: 3/3 fail on `main`, `expected:<Error> but was:<ModelLoading>`; in-app process crash is REASONED, not run | **Patch ready** (`domain-output-safety.patch`); owner applies. After patch: 3/3 pass |
| B2 | Drafts reach editable/copyable state unsanitised (RLO, NUL, template tokens, hallucinated second turn) | probes p07/p08 (round 3) and `DraftOutputSafetyIntegrationTest` | **Patch ready**; component delivered |
| B3 | Copied text forges ChatML turns in PR #20's prompts | `PromptInjectionReproTest` 3/3 fail on #20 head: `expected:<3> but was:<5>` | **Patch ready** (`akmaprotocol-input-neutralization.patch`); after: 179 tests, 0 failures |
| B4 | Foreground-service early return without `startForeground` (`OverlayService.kt:68-71`) | not provoked in rapid-tap test | REASONED; defensive patch in `overlay-hardening.patch` |
| B5 | `filterTouchesWhenObscured` missing on Copy/Confirm | probe: plain button receives tap through a translucent foreign overlay, filtered button does not (API 36 emulator + API 30 Pova 2) | **Patch ready**; effect measured |

## 3. Required changes by owner

- **Domain (Tertiary):** apply `domain-output-safety.patch` (B1, B2). Keep `ReplyValidation.normalize` for analysis text.
- **Overlay (Secondary):** apply `overlay-hardening.patch` (B4, B5). Do **not** enable `FLAG_SECURE` for the recording build (it blanks the demo video; measured). Put `filterTouchesWhenObscured` on the Confirm button when the bridge adds it.
- **UI owner:** Confirm/Cancel bridge is still **NOT FOUND** on `main` and on every remote branch (re-polled 01:21 PHT). Without it an action tap stops at `pendingConfirmation` and no draft can be produced.
- **Elijah (runtime) / #20:** apply `akmaprotocol-input-neutralization.patch`; follow `MODEL_RUNTIME_CHECKLIST.md`; supply a reviewed `ModelArtifactSpec` (size + SHA-256 + revision) for #22; measure hash time vs the 60 s timeout on the Pova 2.
- **Primary / Miguel (shared files):** optional `ci-manifest-guard.patch`; a release build type (`isDebuggable = false`) in `app/build.gradle.kts` for the submitted APK; delegate `app/build.gradle.kts` before #20's Gson dependency merges.

## 4. Remaining release blockers (ranked)

1. **Blocker** — no Android local-inference adapter: no offline demo, no offline claim. (NOT FOUND)
2. **Blocker** — no Confirm/Cancel UI; drafting cannot complete. (NOT FOUND)
3. **Blocker (claims)** — no pinned model spec/hash and no Pova 2 airplane-mode evidence.
4. **High** — B1/B2/B3 patches not yet applied by their owners (a missing native library would crash the app; drafts unsanitised; prompt forgery).
5. **Medium** — debug APK is `debuggable=true`; hash re-verification time unmeasured; FGS early-return (B4); PR #1 submission-copy residuals (see issue #9).
6. **Low** — clipboard sensitivity flag/clear, IME learning, display-spoofing edge cases (combining marks), API 34 behaviour unknown.

## 5. PASS / FAIL / NOT TESTED

| Item | Result | Evidence |
|---|---|---|
| Output sanitiser: hostile + preservation fixtures | **PASS** | `ModelOutputSafetyTest` 46/46 |
| Manifest guard passes real merged manifest | **PASS** | `ManifestGuardTest.cleanFixtureAndTheRealMergedManifestPass`; CLI exit 0 (JDK 17 and 27) |
| Manifest guard detects injected INTERNET in real merged manifest | **PASS** (detected, exit 1) | `forbiddenPermissionInjectedIntoRealMergedManifestIsDetected`; CLI run |
| 13 hostile manifest variants detected with expected message | **PASS** | `eachHostileVariantIsDetectedWithAnActionableMessage` |
| `--release` fails on `debuggable=true` | **PASS** | test + CLI exit 1 |
| Build + unit tests + lint on branch | **PASS** | 159 tests, 0 failures; lint 0 errors |
| Domain patch: tests before/after | **FAIL before → PASS after** | NativeError 3/3 fail on main; 174 pass patched (scratch composite) |
| #20 prompt-forgery repro | **FAIL on #20 → PASS patched** | 5 vs 3 markers; 179 pass |
| `filterTouchesWhenObscured` effect | **PASS** on API 30 (Pova 2) and API 36 | probe taps A=2, B=1 |
| `FLAG_SECURE` effect on screenshots | **PASS** (hidden) API 36; API 30 screencap refused | probe |
| `FLAG_SECURE` on screen recording / MediaProjection | NOT TESTED | |
| Android 14 (API 34) overlay behaviour | NOT TESTED | no image/device |
| PowerShell wrapper on native Windows | NOT TESTED | ran with PowerShell 7 on Ubuntu only |
| FGS early-return race (B4) | NOT TESTED | |
| Crash in app from native `Error` | NOT TESTED (JVM repro only) | |
| #22 provisioning tests / on-device import | NOT TESTED by me (code read only) | |
| Hash time of a ~1.6 GB model on Pova 2 | NOT TESTED | no model file |
| Real model output / prompt-injection resistance | NOT TESTED | no engine exists |
| Pova 2 / Infinix / Camon 30 full-app acceptance | NOT TESTED | probe apps only; Akma untouched |
| Offline (airplane-mode) inference | NOT TESTED | no engine |

## 6. How I used the Pova 2

Only the two probe apps (`ph.merd.securityprobe.victim|attacker`) were installed, run and uninstalled (verified with `pm list packages`);
`ph.merd.akma` stayed in the foreground untouched (no HOME key, no force-stop, no data clear). Screenshots taken for the FLAG_SECURE test were
deleted locally after reading one pixel.
