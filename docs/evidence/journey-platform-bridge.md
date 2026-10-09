# Journey platform bridge evidence

## Source and ownership

- Worktree: isolated `journey-platform-bridge`; branch `feat/journey-platform-bridge`.
- PR: https://github.com/merd-labs/akma/pull/34, targeting `integration/offline-mvp`.
- Agreed original UI base: `224caa2cd9b0aebb7fcd7efe374affe422196b84`, containing accepted PR27/PR30 controls.
- Adopted published runtime/visual/domain candidate: `ac64970e776188fc48e55caef7508d3dc99c44ad` through ordinary merge `ebdc664`.
- Miguel delegated only removal of the extra EOF blank line in Danielle's `OnboardingScreens.kt`. Exactly one LF was removed. Primary independently published the identical correction as `55ccdbf90332cbffdf77ab2c50b93a7865b22612`.
- Secondary owns the callback adapter, Views overlay behavior, protected native controls and scoped tests. Danielle retains Activity, Compose components and resources. Primary retains runtime/domain/configuration integration.
- The accepted `AkmaViewStyle`, typography, icons, bubble and bottom-sheet visuals are preserved. Later competing demo-engine/domain/Compose-overlay changes are not adopted.

## Implemented behavior

`JourneyCoordinatorAdapter` binds events to the existing application-owned coordinator. Creating bindings never initializes another engine or starts analysis/generation. The overlay uses this adapter. Action selection stages confirmation only; the separate protected Confirm control uses its displayed confirmation ID. Tone is immutable while confirmation is pending. Live-state guards reject stale Copy, Paste, input, cancellation and action callbacks. Continuous typing and draft editing remain possible before another composition.

Close/cancel clears the active session and invalidates pending/late results using existing coordinator APIs. Overlay collapse, notification Close, permission revocation and service destruction retain the established service lifecycle. Confirm/Copy reject fully or partially obscured touches; the Views overlay provides a notice. Native dispatch and blocked-notice visibility still require physical testing.

The Views bottom sheet retains `Gravity.BOTTOM`, `MATCH_PARENT` width and `SOFT_INPUT_ADJUST_RESIZE`. Its scroll body is bounded using unresized display height and the union of keyboard/system-bar/cutout insets. It adds no second keyboard position offset. Close remains outside the scroll body. Explicit Paste still requires focus and calls Android's native Paste; its user-triggered IME request remains intact. Tests verify height arithmetic, not actual OEM keyboard/window behavior.

The overlay displays real coordinator loading/analysis/generation phases, bounded-wait explanations and safe notices. It does not show native exception text or construct prompts. `AkmaApplication` retains one genuine LiteRT engine/coordinator shared by Activity and overlay. No model/runtime/manifest/Gradle changes are authored here.

## Activity adoption required from Danielle

Construct `JourneyCoordinatorAdapter` for the existing `session.replies`, supplying the explicit platform Paste callback, `copyDraft(context, displayed)` callback, and Close navigation callback. Build `callbacks(displayed, tone, toneChanged, actionSelected)` for each displayed state. Pass them into `JourneyPanel`; use the same callbacks' `onCopy` for its protected Copy slot. Use `adapter.confirm(confirmation.confirmationId)` only in the separate protected Confirm slot. Supply the optional blocked callback to `confirmationButton`/`copyButton` for a host notice.

The adapter's `onClose` clears/cancels the shared session before navigation. Also route any navigation that dismisses Reply through that callback. Do not pair action selection with confirmation, and do not replace a captured ID with a later pending ID. Activity adoption is not implemented by Secondary because Activity presentation is Danielle's assigned scope.

## Regression proof

Ten synthetic-engine adapter tests cover explicit staging and canonical labels, rapid/cross-surface confirmation, locked tone/action, stale tokens/cancellation/input/Copy, cancellation of non-cooperative late work, explicit Copy failure, continuous typing/editing, recreation, busy-state guards, retry/Paste duplication and clearing before Close navigation. Four viewport tests cover keyboard height, tall/landscape bounds, repeated keyboard changes and degenerate bounds. Existing confirmation, overlay session, dismissal, domain/native integration and safety tests remain enabled.

Synthetic engines are test fixtures only. These tests prove neither native inference nor phone clipboard/service behavior.

## Verification history

- Earlier stable bridge `e799bd8`: local debug build, full debug unit tests and lint PASS; 314 tests, zero failures/errors/skips; lint zero errors and 14 warnings. This used the earlier accepted base and is not evidence for the combined successor.
- Earlier local focused attempt FAIL: `compileDebugKotlin` reported removed `OverlayStyle.kt` after Secondary changed source during an active build. Source was frozen and full gates rerun successfully. No tests/checks were disabled.
- Earlier bridge hosted push run 37995363679: Android and documentation SUCCESS. The pull-request run 37995489626 also completed with Android and documentation SUCCESS before the combined successor push.

### Combined source: PASS

Frozen source: `5df025616ca2f84bb9afb41b4ff59c5fdff5264d`. Ordinary merge `be6c0339846277c3216131b1b35e2be512192510` adopts published `55ccdbf`; `git diff --exit-code 5df0256 HEAD` returns 0 with no output, proving the merge changes no tracked content. The final evidence commit changes documentation only.

Environment: Linux desktop, OpenJDK 17.0.20.1, installed Android SDK, checked-in Gradle Wrapper 8.14 and accepted owner configuration. No configuration edits or warning suppression.

```sh
./gradlew --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

With installed JDK 17 selected through `JAVA_HOME` and SDK selected through `ANDROID_HOME`, command exits 0: `BUILD SUCCESSFUL in 9m 59s`; 51 tasks, 30 executed and 21 up-to-date. Fresh unit XML: **341 tests, zero failures/errors/skips**, including 10 adapter tests and 4 viewport tests. Lint XML: **zero errors/fatal issues and 16 warnings**. Existing compiler, wrapper/SDK and ADJUST_RESIZE deprecation warnings are retained; no CI gate is disabled.

Additional observed checks:

```sh
git diff --check 224caa2 HEAD
git diff --exit-code 5df0256 HEAD
git diff 55ccdbf HEAD | gitleaks stdin --redact --no-banner
java scripts/security/ManifestGuard.java --policy scripts/security/manifest-policy.txt \
  app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
java -jar "$ANDROID_HOME/build-tools/35.0.0/lib/apksigner.jar" verify \
  app/build/outputs/apk/debug/app-debug.apk
```

Whitespace, unchanged merge tree, redacted secret scan, merged debug manifest policy and JDK17 APK signature verification PASS. Manifest guard reports the expected debug-only debuggable warning. Tracked/staged file inspection finds no model weights, credential files, local.properties, private keys or keystores. Application ID is `ph.merd.akma`, label Akma, minSdk 30 and targetSdk 36.

Debug artifact: `app/build/outputs/apk/debug/app-debug.apk`, **82,127,657 bytes**, SHA256 **`a3d26e2eb0a8c68c4eaa641b3da7eaf6ee1c3a9b6b9917cdda3cc41f82e30acf`**. ZIP inspection finds **no model weights**; native libraries include arm64-v8a, armeabi-v7a, x86 and x86_64. This development artifact is not a verified offline-generation demo or release artifact. It was not installed on a phone.

Hosted final-head CI is reported in the PR checks and updated PR body after pushing; earlier hosted results above are not attributed to the new head. Local release gates are NOT RUN for this combined bridge; hosted CI runs both debug and release gates.

## Physical acceptance

NOT TESTED for this bridge APK. No fresh exclusive Rhence/Pova 2 slot has been granted; no ADB command, installation, screenshot or private-chat capture was performed for this candidate. Previous phone evidence belongs to different source/APK hashes and is not reused here.

Required next acceptance: coordinate exclusive device access with all benchmark/ADB owners; verify actual model/API/device; freeze a model-enabled APK and its SHA with Primary; manually grant permission; use only a synthetic messaging conversation; exercise Paste/focus/IME, Analyze, tone/action selection, immutable confirmation, Confirm/Cancel, real generation, Edit/Copy, Close/notification dismissal, revocation, backgrounding, recreation and repeated toggles. Observe OEM battery restrictions without bypassing user controls. Capture only synthetic Akma content. Windows build and physical keyboard/security behavior are NOT TESTED here.

## Integration instructions and blockers

Primary should ordinary-merge the reviewed PR34 head into the agreed shared release branch, preserving accepted visuals/runtime and all checks. Danielle must adopt the Activity adapter/Close clearing before exact final candidate gates. Then freeze the exact model-enabled APK and obtain exclusive physical acceptance. Miguel/team own human review and merges; no reviewers are assigned by this agent and no main merge is performed.
