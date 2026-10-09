# Confirmation UI preparation for genuine engine integration

## Scope and current baseline

This follow-up continues PR #25 on `feat/confirmation-ui-bridge`, after `ece24ebdc4762fad90a8a6b2f4b50a506899490e`. Verified `origin/main` remains `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`. Runtime PR #26 is `ae7f0946c6a6be26fd159650900da6769eb81008`.

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

## Integration instructions and gates

1. Primary and Elijah publish the agreed release branch and immutable SHA containing accepted runtime/configuration changes and this UI head. Current remote inspection finds no shared release branch. `git merge-tree --write-tree --name-only` reports conflicts in MainActivity and OverlayPanel; it was diagnostic only, not a merge or checkout.
2. Resolve those two UI files to this branch's interaction shell, including guarded Retry and separate confirmation. Preserve PR #26's Application engine/coordinator wiring, owner-controlled runtime and build configuration. The domain owner should remove or disable the automatic-confirm `selectAction()` shortcut; this UI does not call it.
3. Re-run assembly, all unit tests and lint against that exact combined source. Verify Elijah's documented candidate artifact size/hash before preparing the APK; never stage weights. Model compatibility remains provisional until observed on the actual Pova 2.
4. Reserve a fresh exclusive physical-device slot before ADB. Record actual identity/API, installed APK SHA, offline initialization, synthetic Analyze/Select/Confirm/Generate/Edit/manual Copy, cancellation, dismissal, notification Close, keyboard focus and permission recovery.

**NOT TESTED:** combined runtime build, genuine generation with this UI, physical confirmation controls, current-APK device smoke tests, Windows execution and arbitrary OS process death. Earlier Pova 2 overlay evidence in `confirmation-ui.md` belongs to an older UI-only APK. PR #26's Camon 30 evidence is owner-reported and is not this integration's Pova 2 acceptance.

No merge, force-push or reviewer assignment is performed. Miguel/team own review and release integration. Pending common-baseline publication is an external dependency, not permission to edit another owner's runtime/configuration or claim completion.
