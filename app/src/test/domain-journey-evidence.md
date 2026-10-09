# Live journey reliability evidence

Owner: Codex Tertiary under Miguel. Branch: `fix/domain-journey-reliability`.

## Exact source and scope

The isolated worktree starts at PR #31 commit `224caa2cd9b0aebb7fcd7efe374affe422196b84`.
It contains live Figma Activity integration, PR #30's design kit and runtime integration
`1c8e1d1e1e925d38f6968a215209847b54c1b714`. Git ancestry was verified locally;
both published heads still matched when the final local gate started.

Primary coordination: [integration notice](https://github.com/merd-labs/akma/pull/27#issuecomment-6089150757).
UI coordination: [PR #31 notice](https://github.com/merd-labs/akma/pull/31#issuecomment-6089151302).

Only two new test files and this evidence file change. Production domain, UI, runtime,
Gradle, manifests, assets, contracts and historical drafts are unchanged. No existing
assertion or test was deleted, disabled or weakened. No production contract defect
reproduced on this source; changing working production behavior was not justified.
PR #29's older runtime implementation was inspected but not merged. The tested bounded
native cancellation, quarantine and provisioning verification invalidation remain intact.

## What the tests exercise

[DomainJourneyIntegrationTest](java/ph/merd/akma/ui/DomainJourneyIntegrationTest.kt)
uses the actual coordinator, catalog, validation, output safety and final panel mapper.
Its eight JUnit tests include:

- All seven categories and three tones: 21 synthetic complete journeys, counted as
  one JUnit test, with canonical labels, explicit immutable confirmation, editing,
  copy eligibility and session clearing.
- `accept` mislabeled `Decline`, malicious summary/status instructions, controls and
  bidi characters cannot select an action or forge panel status.
- Four actions, duplicates, unknown/category-incompatible IDs, unsupported categories,
  unspecified provenance, malformed Unicode and oversized analysis fail safely.
- Message replacement, stale surface controls, canceled tokens and repeated confirmation
  preserve immutable action, tone and source binding.
- ChatML/bidi/control output is sanitized before editable/copyable presentation;
  empty/malformed/oversized drafts are rejected and the exact 1,500-character boundary works.
- Every provisioning failure category, missing model and initialization exception requires
  successful initialization before the journey resumes. Duplicate retry is blocked.
- Oversized messages do not reach analysis or replace an accepted pending source.
- Non-cooperative canceled generation cannot restore output after another session starts.

[NativeJourneyIntegrationTest](java/ph/merd/akma/domain/NativeJourneyIntegrationTest.kt)
uses production `NativeReplyOperation`, `NativeHandleSlot`, coordinator and panel mapper
with synthetic callbacks and handles. Its six JUnit tests verify native callback linkage/OOM
classification, terminal callback exceptions, fresh confirmation, timeout/late output,
serialized reset/retry, missing-terminal quarantine, successful cancellation and failed
native cancellation. Its engine fixture is not `LiteRtReplyEngine` or JNI.

Static inspection confirms Activity and OverlayService obtain `AkmaApplication.replies`,
and OverlayPanel receives that same coordinator. Activity uses the protected confirmation
and copy controls. JourneyPanel does not invoke the existing refinement component or expose
Regenerate, Shorter, More Formal or language controls. These inspections are not platform tests.

## Observed local commands

Host: Ubuntu 24.04.5 LTS, OpenJDK 17.0.20.1, checked-in Gradle Wrapper and configured Android SDK.
`JAVA_HOME` selected JDK 17 and `ANDROID_HOME` selected the local SDK for each Gradle command.
No model weights were staged in this worktree; the assembled APK is model-free.

Baseline, before adding tests:

```bash
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

PASS, exit 0, `BUILD SUCCESSFUL in 4m 28s`: 300 tests in 28 JUnit XML suites,
zero failures/errors/skips. Lint: zero errors/fatal findings, 14 warnings.

Focused new tests:

```bash
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests ph.merd.akma.ui.DomainJourneyIntegrationTest --tests ph.merd.akma.domain.NativeJourneyIntegrationTest
```

PASS, exit 0, `BUILD SUCCESSFUL in 1m 24s`: 14 tests in two XML suites,
zero failures/errors/skips. No failing expectation was changed. Redundant Kotlin
non-null assertions were subsequently removed; the final full gate includes that cleanup.

The JDK 17 source launcher also ran the repository manifest guard against the actual
merged debug manifest: PASS. Its debug-only debuggable warning remains expected;
this model-free debug artifact is not the final submission APK.

Final full gate (same command as the baseline, without test filters): PASS, exit 0,
`BUILD SUCCESSFUL in 5m 19s`. The 30 JUnit XML suites contain **314 tests, zero
failures, zero errors and zero skipped tests**. Both new suites are included (8 + 6).
`:app:assembleDebug` and `:app:lintDebug` PASS; lint retains zero errors/fatal findings
and 14 warnings. This is the complete combined-source suite, not a focused count.

`git diff --check` PASS. Hosted checks will be recorded in the PR handoff after push;
local results do not imply hosted or device success.

## Limits and integration

Physical Activity/overlay binding, native termination, blocking JNI/start/close,
process-wide memory pressure and exact-candidate offline inference are NOT TESTED.
No ADB, installation or device settings changes were made. Pova 2, Camon 30, Infinix,
emulator and Windows execution are NOT TESTED by this branch. Existing PR #31 reports
one Windows symlink privilege failure; the original security assertion remains intact.
The project has no configured instrumentation runner/test dependencies, and this branch
adds no shared build configuration to manufacture a platform result.

Synthetic tests cannot establish semantic faithfulness or absence of invented dates,
availability, payments or commitments. The existing structural-limit counterexample
remains in the full suite. Classification provenance and human review remain essential;
Filipino/Taglish semantic behavior is not established.

This is a scoped stacked PR against `feat/ui-live-integration`. Primary should integrate
PR #31 into the coordinated runtime branch, then incorporate this branch's owned commit
(or retarget the PR once the UI source is its base). Re-run the complete gates after any
runtime reconciliation; do not replace newer recovery wholesale with PR #29's older adapter.
Create and verify a new model-enabled APK for that final source, then coordinate Rhence's
exclusive physical gate before Miguel/MERD review and any main merge. No reviewers were
assigned and no merge was performed by Tertiary.
