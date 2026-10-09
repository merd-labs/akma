# Confirmation UI preparation for genuine engine integration

## UI implementation baseline

This follow-up continues PR #25 on `feat/confirmation-ui-bridge`, after `ece24ebdc4762fad90a8a6b2f4b50a506899490e`. At that inspection, verified `origin/main` was `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`. Runtime PR #26 is `ae7f0946c6a6be26fd159650900da6769eb81008`.

PR #26's Activity and overlay call `selectAction()`, whose domain implementation stages a request and immediately calls `confirmDraft()`. This conflicts with the required separate human confirmation. The UI branch retains #25's staged `selectDraft()`, canonical action/tone/context preview and displayed-token Confirm/Cancel. No UI call to `selectAction()` is present.

The follow-up adopts guarded **Retry local model** controls, visible only in model-unavailable/error states. Ready, loading, analyzing, drafting and pending-confirmation states cannot trigger a reinitialization through these controls. Both interfaces still observe their Application-owned coordinator; PR #26 supplies one lazy real engine/coordinator and starts initialization in Application. This UI branch does not replace that Application or invent a production engine.

**Cancel and clear session** now invokes the existing cancellation/session-clearing helper for pending selection and active processing. Message, analysis, draft, notices and pending confirmation clear while genuinely initialized readiness is preserved. Busy cancellation requires the exact current state snapshot, so stale callbacks cannot cancel a later session. Overlay rebindings cancel pending button input; Activity busy controls are keyed by rendered state. Manual Paste, keyboard request, Edit/Copy, panel Close and service/notification behavior are retained.

Only MainActivity, OverlayPanel, ReplyPresentation, scoped UI binding tests and this evidence file change. No engine, provisioning, domain contract, Gradle, manifest or historical source file is edited.

## Local verification — UI branch only

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk \
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Observed exit **0**, `BUILD SUCCESSFUL in 3m 52s`; 51 tasks, 16 executed and 35 up-to-date. JUnit XML: **126 tests, zero failures/errors/skips**. This retains all previous regression tests and adds five tests for clear-on-Cancel, rejected late native output, stale busy cancellation, retry/error privacy, canceled initialization and live-state reconstruction. Existing cancellation assertions were strengthened for the approved clearing policy.

Lint XML: **zero errors/fatal findings, 18 warnings**. UI-only APK: **26,218,046 bytes**, SHA-256 **`5466772af7cacacc735bd4290918d2cee694b8c7424fb1a8edaf09295436138a`**. It was not installed on a physical device and contains no configured inference engine.

The tests use explicitly synthetic engines only. A private-looking synthetic exception becomes the constant coordinator failure notice; its content does not appear in the UI status. This does not establish handling of every native `Error` or physical interruption of synchronous inference.

`git diff --check` passed. Staged/committed secret scans and hosted results are recorded on PR #25 after publication. Local success on this UI-only branch does **not** prove the combined engine build.

## Initial integration handoff (superseded by combined verification below)

1. Primary and Elijah publish the agreed release branch and immutable SHA containing accepted runtime/configuration changes and this UI head. That remote inspection found no shared release branch. `git merge-tree --write-tree --name-only` reports conflicts in MainActivity and OverlayPanel; it was diagnostic only, not a merge or checkout.
2. Resolve those two UI files to this branch's interaction shell, including guarded Retry and separate confirmation. Preserve PR #26's Application engine/coordinator wiring, owner-controlled runtime and build configuration. The domain owner should remove or disable the automatic-confirm `selectAction()` shortcut; this UI does not call it.
3. Re-run assembly, all unit tests and lint against that exact combined source. Verify Elijah's documented candidate artifact size/hash before preparing the APK; never stage weights. Model compatibility remains provisional until observed on the actual Pova 2.
4. Reserve a fresh exclusive physical-device slot before ADB. Record actual identity/API, installed APK SHA, offline initialization, synthetic Analyze/Select/Confirm/Generate/Edit/manual Copy, cancellation, dismissal, notification Close, keyboard focus and permission recovery.

**At the UI-only handoff, NOT TESTED:** combined runtime build, genuine generation with this UI, physical confirmation controls, current-APK device smoke tests, Windows execution and arbitrary OS process death. Earlier Pova 2 overlay evidence in `confirmation-ui.md` belongs to an older UI-only APK. PR #26's Camon 30 evidence is owner-reported and is not this integration's Pova 2 acceptance.

No merge, force-push or reviewer assignment is performed. Miguel/team own review and release integration. The initial common-baseline dependency did not authorize editing another owner's runtime/configuration or claiming completion.

## Combined candidate verification — October 10, 2026 (PHT)

Primary published `integration/offline-mvp`, PR [#27](https://github.com/merd-labs/akma/pull/27), at **`53b087c535f441815f600fd9ced816b4cdc54a39`**. Its ancestry includes the complete UI implementation **`5262031be1293874a51de04cb4f9fc8825ae7690`**. Actual source inspection confirms separate selection/confirmation, Application-owned `LiteRtReplyEngine` and one shared coordinator for Activity/overlay. No UI automatic `selectAction()` call remains. Primary also filters obscured Copy touches. This agent did not merge another owner's branch or change runtime/configuration.

A separate owned `test/confirmation-runtime-smoke` worktree starts directly at that published SHA. The pinned public candidate was downloaded outside Git and verified before being hard-linked into ignored assets: **1,597,931,520 bytes**, SHA-256 **`faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`**. No weights or APK are staged. This candidate does not establish Pova compatibility or a model lock-in.

The exact assembly/test/lint command above first **FAILED**, exit 1 in 3m47s: `compressDebugAssets` reported `No space left on device`. After the failed process exited, this agent removed only disposable failed build/cache artifacts in the owned validation worktree and replaced its duplicate model copy with a hard link. No teammate files, tests or configuration were removed or disabled.

The same combined command then **PASSED**, exit **0**, `BUILD SUCCESSFUL in 10m 37s`, 51 tasks executed. JUnit XML contains **228 tests, zero failures/errors/skips**. Lint XML contains **zero errors/fatal findings and 12 warnings**. These results apply to `53b087c`, not the UI-only branch or a later native-recovery implementation.

The resulting model-enabled debug APK is **1,676,683,588 bytes**, SHA-256 **`5deadcea7de1b1dbcb6b4c3154c4760888652db481fde01f700f3132552f72e5`**. Additional observed checks:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 scripts/provision/verify-release.sh \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm \
  1597931520 faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9
/home/apollo/Android/Sdk/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
/home/apollo/Android/Sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```

All three exited **0**. The packaging verifier checks embedded model size/header/SHA, uncompressed storage and ARM64 ELF packaging. Signing verifies with APK signature scheme v2. Badging reports **`ph.merd.akma`**, label **Akma**, minSdk **30**, targetSdk **36**. Packaging/signing do not prove native loading or inference. Primary's separately built same-source APK has a different hash (`d8f9b23a8701773a37438e2fab03d0bd5eb9ef42c74c6198370d4b121e973824`); do not conflate the artifacts.

Hosted combined CI is **PASS** for [push 37974765011](https://github.com/merd-labs/akma/actions/runs/37974765011) and [PR 37974869030](https://github.com/merd-labs/akma/actions/runs/37974869030), Android and documentation jobs. UI implementation CI also passed [push 37972684925](https://github.com/merd-labs/akma/actions/runs/37972684925) and [PR 37972689124](https://github.com/merd-labs/akma/actions/runs/37972689124).

**Physical acceptance: NOT TESTED for this APK.** Miguel reports no fresh exclusive phone slot. This agent runs no ADB and does not interrupt Quaternary's separate slot. No new device screenshot, offline initialization, confirmation/generation, Edit/Copy or cancellation/dismissal inference result is claimed. Earlier TECNO LE7/API 30 Paste/keyboard/Close evidence belongs only to its older UI-only APK.

**Release blockers:** owner native-cancellation/recovery work and compiled-manifest guard correction remain pending. Unit-test cancellation doubles do not prove JNI termination. Integrate the reviewed owner fixes on PR #27, rebuild/test/lint and verify a new exact APK, then reserve an exclusive physical slot for the complete offline journey, cancellation/Close and keyboard/permission recovery. Miguel/team retain review and merge ownership; no reviewers are assigned or merge performed.
