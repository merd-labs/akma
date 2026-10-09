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
| Compiled manifest guard | `cd6c846` | Accept signature's exact symbolic/decimal/hex representation; reject weaker levels and misleading strings. |

The initial native/UI source checkpoint is `53b087c535f441815f600fd9ced816b4cdc54a39`.
Domain owner [PR #28](https://github.com/merd-labs/akma/pull/28) is a dependency, not yet
accepted here. Its initial `ae1e7c1` checkpoint still has an unbounded terminal-callback
wait during cancellation. Primary requested bounded cleanup, uncertain-handle quarantine,
verification invalidation and corresponding regressions. The domain owner retains these
runtime/coordinator paths. The security owner retains its scoped follow-up; Primary does
not import its competing combined branch wholesale. Quaternary owns provisioning and the
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

The initial debug APK was handed to Quaternary/Rhence for physical testing. Miguel confirms
testing is in progress. Real offline generation, two-step confirmation, editing/copying,
native cancellation and second-request recovery on this exact artifact remain NOT TESTED
by Primary until actual acceptance evidence is available. Previous Camon measurements and
older UI-only Pova results do not validate this artifact. Native recovery changes require
a newly hashed model-enabled APK and repeat acceptance.

## Remaining acceptance and integration order

1. Integrate the verified final native-recovery dependency and scoped security follow-up.
2. Run full combined unit tests, build, lint, manifest, whitespace and secret checks.
3. Build and verify the new exact model-enabled artifact, including release signing identity.
4. Quaternary/Rhence complete exclusive Pova 2 API30 radios-off generation, confirmation,
   cancel/error/retry, edit/manual-copy and session-clear acceptance; record matching APK SHA.
5. Observe both hosted jobs on the final integration SHA. Keep #27 draft until gates complete.
6. Miguel/MERD reviews and approves the one integration PR before a main merge. Do not merge
   the incompatible original UI/runtime/protocol stacks independently afterward.

Physical model suitability, final recovery behavior, final combined CI and production signing
remain open. Source compilation or successful model packaging alone is not a release PASS.
