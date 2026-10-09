# Offline MVP integration ledger

This ledger records observed integration results, not release approval. The single release
branch is `integration/offline-mvp`, [draft PR #27](https://github.com/merd-labs/akma/pull/27).
Miguel/MERD owns final review and the merge into `main`. No reviewers were assigned.

## Source selection and ownership

The Android baseline was `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`. The later main
README-only update `b31a6f0` was incorporated without altering its team-role changes.

| Component | Selected source | Integration decision |
| --- | --- | --- |
| Domain/overlay integration tests, #21 | `c2bc09564db0519a93bf40c74cd6ce789737e8ec` | Preserve useful confirmation and session regressions. |
| Provisioning, #22 | `e34a6d31b98e81982bc48383a0271376c76760b2` | One retained bundled provisioner; verified original private filename; integrity receipt caching. |
| Output security, #23 | `0c5e82dd31c747d0f0aba5b5d70bda26af57d587` | Components and owner domain/overlay/CI patches applied; patch integration commit `ac313f0`. |
| Confirmation UI, #25 | `5262031be1293874a51de04cb4f9fc8825ae7690` | Authoritative Activity/Views UI with separate captured-token Confirm and Cancel. |
| Native LiteRT adapter, #26 | Selected paths from `ae7f0946c6a6be26fd159650900da6769eb81008` | Runtime/application/prompts/dependencies only. Single-tap confirmation bypass excluded. |
| Competing protocol, #20 | Excluded | No second parser, coordinator, Gson dependency, or model-loading pipeline. |
| Copy security, #23 follow-up | Selected UI changes from `2798cad` | Full/partial obscured-touch filtering on Activity and overlay Copy; API33 sensitive clipboard metadata. Preserve current #25 Cancel/Retry controls. |
| Native recovery, #28 | `f37decaf0f021ba7d98c75588030f6646b780d44` plus `e2d4673` | Retain typed failures, serialized cleanup and early cancellation; add bounded missing-callback quarantine. |
| Compiled manifest guard | `cd6c846` | Accept signature's exact symbolic/decimal/hex representation; reject weaker levels and misleading strings. |

The initial native/UI source checkpoint is `53b087c535f441815f600fd9ced816b4cdc54a39`.
Domain owner [PR #28](https://github.com/merd-labs/akma/pull/28), final published
`f37decaf0f021ba7d98c75588030f6646b780d44`, was incorporated through Git. Its native/readiness
recovery and cancellation-before-start changes remain. Primary coordinated the remaining
bounded cancellation follow-up `e2d4673`: five-second terminal wait, uncertain-handle quarantine,
late-callback rejection and restart-required readiness. The missing-callback reproducer first
FAILed (one test); the fix and nearest lifecycle gate PASSed (31 tests, zero failures/errors/skips).
Primary now owns the coordinated final adapter/coordinator wiring on this integration branch;
owner worktrees and histories are untouched. The alternate security combined branch is not
imported wholesale. Quaternary owns provisioning and the
exclusive physical-device slot; Rhence coordinates acceptance. Primary runs no ADB commands
during that slot.

Shared `LocalReplyEngine`, Kotlin models, manifest, archived Elijah drafts and wrapper remain
unchanged. Application ID is `ph.merd.akma`, product Akma, minSdk 30. Action selection calls
`draft()` to create `pendingConfirmation`; only a separate `confirmDraft(id)` starts generation.
Categories are labelled deterministic; actual model summaries are labelled untrusted. Drafts
cross output validation before editing and explicit clipboard copying. There is no fabricated
success, cloud inference, automatic sending, or outside-app monitoring.

## Toolchain

Observed local environment: Ubuntu 24.04.5, JDK 17, Gradle Wrapper 8.14, AGP 8.11.1,
Kotlin/Compose compiler 2.4.0, LiteRT-LM Android 0.18.0, Compose BOM 2025.10.00,
Activity 1.11.0, Lifecycle 2.9.4, coroutines 1.10.2 and JUnit 4.13.2. compile/targetSdk 36,
build tools 35.0.0. The actual runtime AAR's Kotlin metadata version is 2.4, explaining the
compiler pin; runtime selection remains provisional until Pova acceptance. The future
Kotlin 2.5/Gradle compatibility warning is retained. Combined Windows execution is NOT TESTED.

## Observed local checks

JDK and SDK were selected with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` and
`ANDROID_HOME=/home/apollo/Android/Sdk`. These are this host's command environment, not
paths embedded in the application or repository configuration.

```sh
./gradlew --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- Security/provisioning composite `ac313f0`: PASS; 200 tests, zero failures/errors/skips;
  lint zero errors and 17 warnings; 16m35s.
- Native/UI `53b087c`: PASS; 228 tests, zero failures/errors/skips; lint zero errors and
  12 warnings; 4m56s. This initial full gate built a model-free APK.
- With the real ignored model asset, `:app:assembleDebug`: PASS, 2m24s.
- `:app:assembleRelease :app:lintRelease`: first attempt FAIL because the shared host disk
  filled and Gradle could not write its registry cache. After reclaiming only owned disposable
  artifacts, retry PASS, 11m22s; release lint zero errors and 12 warnings. No tests/checks
  were disabled and no teammate files/processes were deleted or stopped.
- `:app:testDebugUnitTest --tests 'ph.merd.akma.security.ManifestGuardTest'` after the
  compiled-manifest correction: PASS, 2m05s; eight tests, zero failures/errors/skips.
  This focused gate is not a rerun of the complete 228-test suite.
- Copy-protection integration: `:app:compileDebugKotlin :app:testDebugUnitTest` with filters
  `ph.merd.akma.ui.DraftConfirmationUiTest` and `ph.merd.akma.overlay.OverlayDomainIntegrationTest`:
  PASS, 3m40s. This is a focused binding gate; it does not prove physical obscured-touch behavior.
- Fourteen equivalent/weak permission fixtures derived from the decoded actual APK manifest:
  PASS using JDK 17. Actual debug guard PASS with debuggable warning; actual release guard
  `--release` PASS. Permission policy was not widened.
- JDK17 `VerifyReleaseArtifactTest`: PASS, 13 fixture cases; inference NOT TESTED by fixtures.
- `git diff --check`, `actionlint .github/workflows/ci.yml`, Bash syntax and
  `gitleaks git --log-opts='origin/main..HEAD' --redact --no-banner`: PASS at the native/UI
  checkpoint (13 commits scanned, no leaks). Final combined head needs its own full gate.

## Hosted CI

Both Android and documentation jobs, including the merged-manifest guard, PASS on `53b087c`:
[push run 37974765011](https://github.com/merd-labs/akma/actions/runs/37974765011) and
[PR run 37974869030](https://github.com/merd-labs/akma/actions/runs/37974869030).
These hosted APKs contain no model weights and do not prove inference. The later guard-only
head `cd6c846` has separate [push](https://github.com/merd-labs/akma/actions/runs/37978104417)
and [PR](https://github.com/merd-labs/akma/actions/runs/37978110349) runs: both jobs PASS.
The PR Android build/test/lint and merged-manifest guard steps were inspected individually.
Owner-branch CI is not combined-branch CI.

## Exact model and initial artifacts

The pinned artifact is
`Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`, revision
`19edb84c69a0212f29a6ef17ba0d6f278b6a1614`, 1,597,931,520 bytes, SHA-256
`faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`.
Downloaded bytes passed exact size/header/SHA verification, independently of metadata.
The model and APKs are outside Git; the ignored asset hard-links the verified read-only cache.

| Artifact from source `53b087c` | Size, bytes | SHA-256 |
| --- | ---: | --- |
| Initial model-enabled debug APK | 1,676,683,590 | `d8f9b23a8701773a37438e2fab03d0bd5eb9ef42c74c6198370d4b121e973824` |
| Initial model-enabled non-debuggable release variant, development-signed | 1,670,797,290 | `562c938ba667b7141b90cc132d14dbc472fd4ca6ff2bca8c673d4988bc099782` |

Stable local artifacts are under `~/.cache/akma-releases/53b087c/`. The release variant is
signed with the existing Android development key, not a production distribution key.
Both passed embedded model verification (exact hash, uncompressed asset and ARM64 ELF).
Debug v2 signature passed; release v3 signature passed using the explicit JDK17 apksigner JAR.
Actual AAPT identity passed: `ph.merd.akma`, Akma, minSdk 30, targetSdk 36. A failed redundant
APK copy was removed after ENOSPC; no incomplete artifact was handed off as verified.

The final scoped adapter changes add constant actionable provisioning-failure notices, invalidate
verification receipts after runtime/model-load failures, retain the owner's tested prompt helper
and security regressions, and separate initialization from generation deadlines. The app uses
conservative 900-second initialization and 240-second generation bounds; those limits are not
performance measurements. Cancellation cleanup has its own five-second quarantine bound.
Constructor compatibility is preserved by appending the optional initialization deadline.
The full combined gate on the production tree committed as
`3a3071139b6474c8ba393ccdf800e98be55c2d5a` PASSed:

```sh
./gradlew --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false \
  :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

Observed exit 0, `BUILD SUCCESSFUL in 4m 21s`: 280 tests across 25 XML suites,
zero failures/errors/skips; debug and release lint each zero errors/fatal findings and
12 warnings. Actual debug/release merged-manifest guards PASS; actionlint, Bash syntax,
whitespace and 20-commit redacted Gitleaks scan PASS. The production tree matches the tested
source; no source changed between the gate and commit. The APK's optional AGP version-control
metadata reports `NO_VALID_GIT_FOUND` for this worktree, so provenance is recorded by the
source-tree check and this external SHA ledger, not claimed as embedded commit metadata.

The verified immutable artifact is
`~/.cache/akma-releases/3a30711/akma-model-release-development-signed.apk`:
1,670,830,058 bytes, SHA-256
`3e9fe18697409195323af83c05286109b68d0bbc13622643c23321b3889672d0`.
JDK17 apksigner v3 verification PASS. Actual AAPT identity PASS (`ph.merd.akma`, minSdk 30,
targetSdk 36); decoded compiled manifest `--release` security guard PASS. Embedded model
size/header/SHA, uncompressed asset and ARM64 ELF packaging PASS. This non-debuggable release
variant uses the existing development signing key; production distribution signing remains
unresolved. Only owned redundant unsigned/model-intermediate outputs were reclaimed after
verification; both earlier handed-off artifacts remain intact.

This artifact supersedes the initial candidate for final acceptance. Device inference for
this exact SHA is NOT TESTED by Primary. Synchronous JNI cancellation/start/close calls cannot
be forcibly interrupted by a coroutine: the five-second cleanup bound applies to waiting for
a terminal callback after `cancelProcess` returns. Native abort, permanent JNI hangs and
process-wide OOM may still require force stop. No test establishes those native guarantees.

The initial debug APK was handed to Quaternary/Rhence for physical testing. Miguel confirms
testing is in progress. Real offline generation, two-step confirmation, editing/copying,
native cancellation and second-request recovery on this exact artifact remain NOT TESTED
by Primary until actual acceptance evidence is available. Previous Camon measurements and
older UI-only Pova results do not validate this artifact. Native recovery changes require
a newly hashed model-enabled APK and repeat acceptance.

## Final-source hosted gate

Both jobs PASS at source `3a3071139b6474c8ba393ccdf800e98be55c2d5a`:
[push run 37981023799](https://github.com/merd-labs/akma/actions/runs/37981023799) and
[PR run 37981028941](https://github.com/merd-labs/akma/actions/runs/37981028941).
PR #28 is now MERGED into the integration branch by the preserved Git merge
`9157656e93226e914a2c90c27b13b9c67ccaf634`; this is not a merge into main.
Remote main remains `b31a6f045d75bf686b6a6aaeabb721937c57721f`.
PR #27 remains draft and mergeable. This subsequent ledger-only commit has its own hosted
runs; report their actual outcome separately rather than borrowing the source-head result.

## Remaining acceptance and integration order

1. Preserve the verified combined source and frozen model-enabled artifact above.
2. Inspect the final ledger-only commit hosted jobs to complete the current-head CI handoff.
3. Decide production distribution signing with Miguel; the current candidate is development-signed.
4. Quaternary/Rhence complete exclusive Pova 2 API30 radios-off generation, confirmation,
   cancel/error/retry, edit/manual-copy and session-clear acceptance; record matching APK SHA.
5. Observe both hosted jobs on the final integration SHA. Keep #27 draft until gates complete.
6. Miguel/MERD reviews and approves the one integration PR before a main merge. Do not merge
   the incompatible original UI/runtime/protocol stacks independently afterward.

Physical model suitability and final recovery acceptance, current-head hosted CI and production
distribution signing remain open. Source compilation or successful model packaging alone is not a release PASS.

## Final runtime and live UI reconciliation

Primary fetched live state on October 10 before editing. PR27 was `1c8e1d1`, main
`b31a6f0`, PR29 `864d938b933d8900ecf21de5c5c30a5eebf44c72`, PR30 `ef1d213`,
and the newly published live UI PR31 `224caa2`. PR31 includes both PR27 and PR30;
its design kit must not be reapplied. PR28 is already an ancestor through `9157656`.
Source ownership was coordinated on PR29 and PR31 before production edits.

The PR29 merge had one actual Kotlin conflict, `LiteRtReplyEngine.kt`. Resolution:

- Keep PR27's `RuntimeRecovery`, `NativeHandleSlot`, bounded terminal wait, uncertain-handle
  quarantine, restart notice, receipt invalidation and typed provisioning failures.
- Port callback conversion failure handling, early oversized-output cancellation and
  first-text callback timing through the existing native operation. Cancellation runs on
  the owning coroutine; a callback conversion failure is never treated as native completion.
- Keep the approved shared contract and deterministic category selection from copied input.
  Exclude PR29's HYBRID enum/validation/test changes because model purpose must not redefine
  the domain category. Existing assertions are retained; no failing test is disabled.
- Use one strict Gson 2.14.0 streaming parser for model output. Require string fields and
  complete JSON documents; reject duplicate expected fields and malformed output. Preserve
  the validated plain-text draft fallback, output sanitization, canonical actions and
  immutable two-step confirmation. The competing PR20 pipeline remains excluded.
- Retain PR29's historical Camon evidence with its stated failures and artifact identity.
  Retain its Windows build helper, but refuse to overwrite an existing mismatched model asset.

The new early-overflow regressions first reproduced two failures in the original bridge:
12 focused tests, two failures, exit 1, `BUILD FAILED in 4m 39s`. The first revised compile
also failed on Kotlin generic inference in `select`; the result type was made explicit.
These failures remain recorded rather than omitted from the final result.

The official final-device gate is now Rhence's owner-reported Infinix X6815B, Android 11/API30;
identity and hardware still require observation in his exclusive slot. English offline
fidelity is required; Taglish is optional and must not be promised without evidence.
Quaternary released the Pova slot. His older frozen release `3e9fe186...` completed English
Reschedule/Professional with human fidelity PASS, but Filipino fidelity FAIL. Those results
cannot establish acceptance for a new source, APK or Infinix device.

Miguel selected the existing Android development signing key for the demo. A non-debuggable
release variant signed with that key is development-signed, not a production distribution
identity. New artifacts require new hashes and matching physical acceptance; previous frozen
artifacts remain intact. Full combined gates, hosted CI and final team approval are pending
until the actual results below are recorded.

Focused reconciliation gate: `:app:testDebugUnitTest --tests ph.merd.akma.domain.NativeReplyOperationTest --tests ph.merd.akma.domain.LocalModelOutputTest` PASS, exit 0; 21 tests, zero failures/errors/skips. Staged redacted Gitleaks scan, whitespace, actionlint and PowerShell syntax PASS. JDK17 standalone artifact verifier fixtures PASS (13 cases), not inference proof.

### Combined source checkpoint and observed gate

Local combined source `47d9c07bf81830e5ac4618ded295ec87195be69b` contains PR29's selected
changes through semantic merge `46082ea`, PR31 visual/runtime-compatible source
`01c226a4bee49ee424d0ebef974a692581f580ab` through `47de7b1`, and PR32 test source
`520762775d0f72ddca20928019bbbab2f307cd41` through `47d9c07`. PR31's later launcher-icon
and draggable-bubble commits were not silently adopted during the gate. No component
source was personally edited by Primary.

```sh
./gradlew --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false \
  :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest \
  :app:testReleaseUnitTest :app:lintDebug :app:lintRelease --console=plain
```

With JDK17 and Android SDK selected, observed exit 0, `BUILD SUCCESSFUL in 17m 47s`,
107 tasks. Debug and release each ran 327 tests across 32 suites: zero failures, errors
or skipped tests. Each lint report has zero errors and 16 warnings. Both actual merged
manifest guards PASS. This gate included the real ignored model asset; hosted builds
still contain no weights. It does not prove generation on Rhence's Infinix.

Debug APK artifact verification PASS: exact model size/header/SHA and ARM64 ELF,
1,679,726,759 bytes, whole-APK SHA-256
`e7416d6958fe394fb954493e4d4cb7594bd4445828fd0dcb0e50c6f94b283399`.
JDK17 apksigner verification PASS; development certificate SHA-256
`a7cabcfe6ca089204bed0b79e2ac0be6e2d98e3eb337ea11f9c043342cd55cff`.
This debug artifact is a checkpoint, not the non-debuggable final demo APK.
Committed-range redacted Gitleaks PASS: 27 commits, no leaks. Approved contract,
coordinator, wrapper and archived Elijah drafts match the previous PR27 head.

The branch is published for owner coordination while explicitly BLOCKED on a real
whitespace defect: `OnboardingScreens.kt:290`, extra EOF blank line. PR31 hosted run
37992678139 logs show the check exiting 2. Danielle owns the component; Primary requested
its correction and separately requested authority for only the prepared one-line cleanup.
No failing check is bypassed, no main merge is performed, and this published checkpoint
must not be called green or release-approved. The owner platform bridge and Activity
Close/session wiring remain integration dependencies.

PR33 `e934934` is excluded: its opening-brace extraction causes normal JSON to select an
empty object, then reports fabricated default Other/summary as LOCAL_MODEL. Its analysis
prompt keys also conflict with the retained protocol. The integrated parser fails visibly;
model-purpose/category changes require coordination rather than another success fallback.
