# Patches for files this branch does not own

Each patch was applied and tested in a throw-away scratch worktree; none of the owners' files is modified on this branch.
Apply with `git apply --check <patch>` first, then `git apply <patch>` on a branch the owner controls.

| Patch | Owner | Base | Validated by | Fixes |
|---|---|---|---|---|
| `domain-output-safety.patch` | domain (Tertiary) | `main` `3edd10f` + this branch | `git apply --check`; full unit suite 174 tests, 0 failures (scratch composite); the 3 `NativeErrorResilienceTest` cases **failed before** (stuck in `ModelLoading`) and pass after | native `Error` escaping the coordinator; unsanitised drafts; raw exception strings |
| `akmaprotocol-input-neutralization.patch` | runtime/prompt (Elijah, PR #20) | PR #20 head `89b2492` + this branch | 3 `PromptInjectionReproTest` cases **failed before** (`expected:<3> but was:<5>`) and pass after; suite 179 tests, 0 failures | forged ChatML turns from copied text |
| `overlay-hardening.patch` | overlay (Secondary) | `main` `3edd10f` | `git apply --check`; `:app:assembleDebug :app:testDebugUnitTest` exit 0 (scratch composite). Effect of the flag measured separately: `OVERLAY_FLAG_EVALUATION.md` | obscured-touch filter on Copy (+Confirm to come); `startForeground` before early-return stop |
| `ci-manifest-guard.patch` | CI (Primary/Miguel) | `main` `ci.yml` | `actionlint` clean; `git apply --check` | explicit merged-manifest guard step |

Not covered by a patch (owner action only): Confirm/Cancel UI (bridge) must call `confirmDraft(displayedId)` from a separate control with
`filterTouchesWhenObscured = true`; `app/build.gradle.kts` release build type with `isDebuggable = false` (Gradle is Miguel's).
| `ui-draft-review-notes.patch` | UI (Danielle, #31) | `feat/ui-live-integration` | `git apply --check` on #31 head `583c469`; compiled and UI tests 40/40 (incl. new mapper test) in a scratch composite | shows advisory notes under a draft when `DraftGrounding` finds ungrounded details, commitments or invented unavailability (non-blocking) |
