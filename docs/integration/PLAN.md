# Akma integration plan

Status: planning implemented; production integration blocked. Owner: Miguel, Codex Primary. The baseline observations below describe `02df71a`; see the [live integration ledger](LEDGER.md) for current PR heads, checks, approval gates and domain/UI dependencies. This branch changes integration documents only.

## Verified baseline and authority

- Repository: [merd-labs/akma](https://github.com/merd-labs/akma), origin `https://github.com/merd-labs/akma.git`, existing public visibility unchanged. GitHub CLI account: `CodeExplorer430`, with repository access verified.
- Android implementation: `6414718e6d78d41deeba4abb8841e57e5d709264`. Reviewed baseline head: `02df71ad7328e55e0762d3fde8f747ef44a04348`.
- Remote bootstrap is now pushed. [Draft bootstrap PR #4](https://github.com/merd-labs/akma/pull/4) targets `main`, currently documentation-only at `60580cd689dfc47498c3b2f5d5c1b5488e1ee371`.
- Integration branch: `feat/integration-glue`, based on verified `origin/chore/akma-bootstrap` at `02df71a`. Do not use documentation-only main for Android feature branches. After an approved bootstrap merge, verify ancestry and PR diff before changing the integration PR base to main.
- Integration worktree: `/home/apollo/Projects/Competitions/Year-2026/appbuildersph-hackathon-2026/akma-worktrees/integration`. The original checkout and its untracked `AkmaAI_Phase2_GitOps_v3/` intake package are preserved.

Read inputs: repository [AGENTS.md](../../AGENTS.md), [README](../../README.md), [start guide](../START_HERE.md), [PRD](../PRD.md), [contract](../CONTRACT.md), [decisions](../DECISIONS.md), [agent workflow](../AGENT_WORKFLOW.md), brand/provenance and publishing guidance; delivered Phase 2 v3 README, COMMON_GITOPS_RULES, RUNBOOK_WORKTREES, SPEC_RECONCILIATION and primary bootstrap/integration prompts; actual Kotlin sources and Git history. The intake package is local review input, not a merged product specification.

The current owner instruction preserves application ID `ph.merd.akma` and product Akma, matching source and the baseline APK. The older intake package's `ph.merd.akmaai` proposal is superseded. No namespace/package/configuration migration is planned by this integration task.

Hard submission: October 10, 10:00 AM PHT. Internal target: 9:00 AM. Official build window starts October 9, 2:30 PM (19.5 hours). Prioritize one real offline reply over tooling or refinements.

## Ownership and worktrees

Paths below are relative to the parent of the audited `akma` checkout. Existing worktrees were observed; suggested paths are reservations, not claims that a worktree or implementation exists. Each teammate uses an isolated worktree in their own clone and verifies their own GitHub CLI identity.

| Human / session | Branch | Owned scope | Observed or suggested worktree |
| --- | --- | --- | --- |
| Miguel / Codex Primary | `feat/integration-glue` | This plan; later only explicitly delegated coordinator/composition integration files and focused tests | Existing `akma-worktrees/integration` |
| Miguel / Codex Secondary | `feat/overlay-paste` | `app/src/main/java/ph/merd/akma/overlay/**`, platform/clipboard helpers by agreement, scoped tests, `docs/evidence/overlay.md` | Suggested `akma-worktrees/overlay`; not observed |
| Miguel / Codex Tertiary | `test/domain-contracts` | `app/src/test/**`, `docs/evidence/domain-tests.md`; coordinate test-file allocation before integration tests | Existing `akma-domain-tests`, observed at `02df71a` |
| Miguel / Codex Quaternary | `test/device-benchmark` | `scripts/bench/**`, `docs/evidence/device/**`; one physical-device slot at a time | Existing `akma-worktrees/device-benchmark`, observed at documentation-only `60580cd` |
| Miguel / Claude Code | `docs/security-review` | `docs/reviews/SECURITY_REVIEW.md`, security findings/issues; no production changes | Existing `akma-security-review`, observed at `02df71a` |
| Miguel / Google agent | `docs/submission-readiness` | `docs/submission/**`, `docs/AI_DISCLOSURE.md`; README only by owner coordination | Existing `akma-docs-submission`, observed at `48c4e0e`; [draft PR #1](https://github.com/merd-labs/akma/pull/1) |
| Elijah / model research | `research/model-feasibility` | `docs/model-research/**`, approved isolated prototype; model/license/API30 measurements | Suggested own-clone model-research worktree; not observed |
| Elijah / runtime adapter | `feat/local-reply-engine` | Dedicated inference classes, allocated tests and runtime documentation; proposes build changes to Miguel | Suggested own-clone runtime worktree; not observed |
| Danielle / UI | `feat/assistant-ui` | Compose components/themes/resources and allocated UI tests; coordinate Activity integration points | Suggested own-clone UI worktree; not observed |
| Rhence / product QA | `docs/akma-master-context` | `docs/product/**`, `docs/qa/**`, `docs/pitch/**`; proposed master context and decision log | Suggested own-clone product worktree; not observed |

Miguel also coordinates `chore/akma-bootstrap` and exclusively coordinates manifest, Gradle/dependency pins, wrapper, lockfiles and shared Kotlin types. Existing older assignments in AGENT_WORKFLOW require reconciliation by Miguel; this proposed allocation does not authorize concurrent edits to overlapping paths. `MainActivity.kt` ownership stays with the UI/platform owners unless explicitly delegated. Clipboard helper ownership must be agreed between overlay and UI owners before either changes it.

The device owner must verify an Android base before app-dependent work; its current documentation-only branch is recorded, not reset or rebased here. Preserve `/tmp/akma-bootstrap-baseline` and other agents' dirty or staged work. No other worktree is created by this task.

## Current integration flow and seams

| Component | Current behavior | Future integration responsibility |
| --- | --- | --- |
| [LocalReplyEngine.kt](../../app/src/main/java/ph/merd/akma/domain/LocalReplyEngine.kt) | `initialize(): Result<Unit>`, `analyze(AnalyzeRequest): Result<AnalysisResult>`, `draft(DraftRequest): Result<String>` | Elijah implements these unchanged signatures; shared request/result/tone types are protected |
| [AkmaApplication.kt](../../app/src/main/java/ph/merd/akma/AkmaApplication.kt) | One lazy process coordinator with `UnavailableReplyEngine`, SupervisorJob and main dispatcher | Candidate composition seam for binding Elijah's validated adapter, only after Miguel delegates this exact file |
| [UnavailableReplyEngine.kt](../../app/src/main/java/ph/merd/akma/domain/UnavailableReplyEngine.kt) | All operations fail with model unavailable; no canned output | Retain honest unavailable behavior until real initialization succeeds |
| [ReplyCoordinator.kt](../../app/src/main/java/ph/merd/akma/domain/ReplyCoordinator.kt) | Shared StateFlow, explicit operations, IO dispatcher, engine mutex, 60-second timeout and stale-result generation guard | Preserve operation/state behavior; edit only if real adapter evidence requires a scoped, approved change |
| [ReplyValidation.kt](../../app/src/main/java/ph/merd/akma/domain/ReplyValidation.kt) | Structural input/action/output validation | Keep unchanged unless a separately reviewed owner decision requires an update |
| [MainActivity.kt](../../app/src/main/java/ph/merd/akma/MainActivity.kt) and [OverlayPanel.kt](../../app/src/main/java/ph/merd/akma/overlay/OverlayPanel.kt) | Compose and Views observe the same coordinator; explicit action/tone, draft editing and Copy controls | Danielle and overlay owner supply stable compatible UI; integration agent does not edit layouts |
| [ReplyPresentation.kt](../../app/src/main/java/ph/merd/akma/ui/ReplyPresentation.kt) and OverlayService | Explicit clipboard write; user-started non-exported foreground overlay and Close | Platform owner verifies focused Paste, keyboard, permission recovery and Close on API30 |

Intended sequence:

1. The user manually copies outside-app text, opens Activity or permitted overlay and explicitly pastes into its focused native field. Akma does not read external screens or poll the clipboard.
2. The user invokes model initialization. Only a real successful runtime/model load enters Ready. Overlay permission is a separate Activity gate; denial retains Activity fallback.
3. Analyze passes user-provided input through existing validation and the real adapter. A validated result exposes actions; the user selects an offered action and tone. Do not automatically select or generate a reply.
4. Draft passes the original request, selected action ID and tone unchanged to local generation. Show Drafting until a validated, nonblank result enters Editing.
5. The user edits the draft and explicitly presses Copy. Mark Copied only after clipboard write succeeds. Pasting back and sending remain manual.

Preserve ModelUnavailable, ModelLoading, Ready, Analyzing, ChoosingAction, Drafting, Editing, Copied and recoverable Error. Busy operations reject duplicates; Cancel invalidates stale results and restores the recovery phase. Engine errors must not expose prompt text. No mock-ready state, hidden cloud fallback or bypass of the initialized gate is permitted.

Native cancellation remains a dependency: coroutine timeout/cancellation alone cannot stop a blocking JNI inference call. Elijah must demonstrate cooperative cancellation or safe completion; retain serialization until native work actually returns. Never overlap native operations merely because the UI has recovered. The current contract has no explicit close/cancel API, so lifecycle/resource management must be resolved within the adapter or separately reviewed, not invented in shared types here.

Current limits: 1,500 UTF-16 characters for bounded text; maximum four actions; unique, nonblank action IDs/labels; default reviewed IDs `reschedule`, `acknowledge`, `clarify`, `accept`, `decline`; nonblank bounded category, summary and draft. The UI currently supplies the message, action and tone; optional history/relationship/instruction fields are not a promise of implemented UI controls. Structural validation does not establish semantic faithfulness, JSON parsing, language matching or seven-category support.

## Rhence master context: proposed update

Rhence owns a reviewable proposed `docs/product/AKMA_MASTER_CONTEXT.md` and decision log. Preserve the original delivered `reference/Akma-Master-Context_Rhence_original.md`, archived Elijah drafts, current PRD and contract. No product requirements are silently replaced by this plan.

| Proposal | Integration treatment |
| --- | --- |
| Interview invitation; meeting/schedule request; reschedule request; follow-up/reminder; complaint; casual greeting/check-in; Other | Seven target categories, supported only after measured runtime capability or an approved, clearly disclosed deterministic catalog |
| Three contextual action suggestions | Proposed presentation target; current PRD permits 2–4 and Kotlin maximum is four. Owner decision tracked in [issue #3](https://github.com/merd-labs/akma/issues/3); no cap change here |
| Category-specific catalog and Other fallback | Rhence approves exact IDs/labels with runtime/UI owners. Current five-ID allowlist is not a seven-category catalog. See [issue #2](https://github.com/merd-labs/akma/issues/2) |
| Professional, Friendly, Concise | Existing enum; preserve user selection through generation |
| English/Filipino/Taglish, roughly 80-word drafts, no invented facts or commitments | Quality targets requiring model/device evidence and human assessment; not current guarantees |
| Regenerate, Shorter, More formal and bubble-on | Deferred until the real core path and physical lifecycle pass; do not invent refinement APIs or universal overlay claims |

If classification is infeasible but local drafting works, coordinate a deterministic catalog with Rhence and Danielle. Label analysis/action suggestions explicitly as presets and obtain approval for how both UI surfaces show provenance using the existing contract. This does not permit fake model initialization or deterministic canned drafts. Unsupported or malformed output fails visibly.

## Gates before production writes

All four gates must supply reviewable evidence, with exact branch/head and owner acknowledgement:

1. **Elijah runtime/device:** real adapter implementing the current contract; exact runtime version/backend/ABI and minSdk30 compatibility; model source/revision, quantization, license and checksum; reproducible local provisioning; actual Pova 2 load, warmed latency and peak memory; three distinct synthetic generations with Wi-Fi and mobile data disabled; cancellation/retry behavior and crash findings. Model/runtime selection remains provisional until this passes.
2. **Danielle UI:** stable branch/head that builds against the unchanged contract and preserves pending/loading/error/cancel, explicit action/tone, editable draft and manual Copy. Resolve integration points with the overlay owner rather than changing the UI from this branch.
3. **Rhence product:** reviewed catalog/category decisions, provenance disclosure and synthetic acceptance matrix; resolve three-action conflict explicitly. Record measured language/faithfulness limits.
4. **Miguel delegation:** exact coordinator/composition/test files assigned, dependencies reviewed, merge order and device slot agreed. Shared configuration/application-ID migration belongs to its owner in a separate coordinated change.

No real runtime branch or stable UI handoff was available in the inspected baseline/remotes. ADB currently lists only an API36 emulator; no physical Pova 2 is attached. [Integration blocker #5](https://github.com/merd-labs/akma/issues/5) records required owner inputs. Production remains read-only while these gates are open.

## Verification evidence and next gate

The coordinator directly ran the following during the preceding baseline audit at the same `02df71a` head:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Observed: exit 0, BUILD SUCCESSFUL in 2m 13s; 24 tests, zero failures/errors; lint zero errors and 13 warnings. Local XML was re-inspected during this handoff: 11 coordinator tests and 13 validation tests pass. [BOOTSTRAP_VERIFICATION.md](../BOOTSTRAP_VERIFICATION.md) separately records the earlier frozen-worktree 4m 52s gate and supplemental API36 emulator checks. No new Gradle build is required for this document-only change; new hosted runs are reported through the PR, not presumed successful.

Hosted handoff observation: bootstrap [push run](https://github.com/merd-labs/akma/actions/runs/37925350338) and [PR run](https://github.com/merd-labs/akma/actions/runs/37925388484) pass the Android build/unit/lint job. Documentation jobs fail on trailing whitespace in the preserved Elijah archives. The push checkout log confirms `fetch-depth: 1` and `git fetch --depth=1`; `git show --format= --check HEAD` consequently checks the shallow root as newly added content, including unchanged archives. Local full-history checks pass and `git diff --exit-code 60580cd 02df71a -- docs/reference/elijah` confirms archive preservation. Integration's initial hosted documentation jobs also fail; Android jobs were still pending at that observation. Miguel must coordinate a CI history/diff correction in its own scope; this branch does not edit CI or archived originals. The PR comments record subsequent check results.

Verified baseline pins remain unchanged: JDK17, Gradle8.14, AGP8.11.1, Kotlin/Compose compiler2.2.20, Compose BOM2025.10.00, Activity1.11.0, Lifecycle2.9.4, Coroutines1.10.2, JUnit4.13.2, minSdk30 and compile/target36. Baseline APK compiles; real inference, Windows build and physical phone behavior remain unverified.

Handoff checks executed before pushing bootstrap: `git diff --check 60580cd..02df71a` passes; `gitleaks git --redact --log-opts='60580cd..02df71a'` scans both commits with no leaks; `git merge-base --is-ancestor 6414718 02df71a` succeeds. Integration delivery must additionally pass scoped staged whitespace/secret checks and an unchanged-production diff before its commit/push.

The separate domain-test issue #3 reports 51 tests with one failing three-action expectation on that owner's branch. This is reported evidence for a different branch, not a bootstrap failure or a reason to modify its tests. Coordinate the product/contract decision before integration consumes that branch.

After approved production integration:

- Build APK, focused coordinator/adapter tests and lint on JDK17 using the wrapper. Allocate tests with the domain-test owner. Cover request/action/tone propagation, unavailable initialization, malformed/unsupported analysis, duplicate IDs, blank/oversize results, duplicate clicks, cancellation/stale results, timeout and recovery. Synthetic doubles establish control flow only, not real model quality.
- Reserve the Pova 2 LE7 Android11/API30, Helio G85, 6 GB physical RAM and 128 GB capacity with the device owner. Measure free RAM/storage. Test Activity and overlay, focused manual Paste, keyboard focus, permission denial/revocation, Close and process recovery.
- After disclosed model provisioning, disable Wi-Fi/mobile data and run three new synthetic messages through action/tone selection, real generation, edit, Copy and manual paste/send. Record code/model hashes, device/API, load/generation latency, memory, actual outputs and limitations without personal chats or secrets. No cloud, inaccessible outside-app data, AccessibilityService, notification monitoring, automatic sending or system keyboard.
- Treat Infinix Zero 5G Android11 and Camon30 Android14 as secondary gates. API36 emulator checks are supplemental; missing device checks remain explicitly untested.

## Delivery and stop condition

Commit and push only assigned integration documents on `feat/integration-glue`. Update the existing non-duplicate draft PR against the verified remote bootstrap branch while main lacks Android. Miguel and the team handle review and merge approval; this coordinator does not request or assign reviewers. Do not merge without explicit team approval, force-push, delete worktrees/branches, alter visibility or stage the unrelated intake package.

Stop after the planning PR and blocked issue handoff. Elijah supplies runtime/device evidence, Danielle supplies stable UI, Rhence proposes reviewed product decisions, and Miguel assigns exact integration files before any production code resumes.
