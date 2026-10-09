# Explicit draft confirmation UI evidence

## Source and scope

Branch: `feat/confirmation-ui-bridge`. Verified common base: `c2858acddb68630e6a62c732f998206e2f9c8748`, published by Primary with accepted bootstrap/CI, domain #14/#17 and overlay #12 ancestry. Overlay #12 subsequently merged into `main` as `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`. No other owner's branch was merged by this agent.

MainActivity and OverlayPanel now display the canonical selected action, immutable chosen tone and original message before separate **Confirm and generate draft** and **Cancel selection** controls. Context is labeled untrusted. Generating a draft does not send a message or accept an invitation. Existing manual Edit and Copy follow generation. Pending selection blocks action/tone changes and reanalysis; editing the message invalidates selection.

Both surfaces capture the displayed `DraftConfirmation.id` and recheck current coordinator state before `confirmDraft(id)` or `cancel()`. Stale IDs are never replaced with a newer token. Activity controls are keyed by token; overlay controls are recreated from state. Confirm rejects fully and partially obscured touch events. Existing Paste, keyboard focus, service/notification dismissal and session clearing are retained. Domain contracts, engine/runtime code, Gradle and manifest files are unchanged.

## Local gates — PASS

Executed from this branch's isolated worktree:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk \
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Observed exit **0**, `BUILD SUCCESSFUL in 6m 5s`, 51 tasks executed. JUnit XML totals: **121 tests, 0 failures, 0 errors, 0 skipped**. Fourteen new binding tests use the real coordinator with explicitly synthetic test-only engines; they do not install a fake production engine.

Coverage includes no generation before confirmation; canonical labels despite misleading engine labels; immutable tone/context and reconstructed binding; repeated and cross-surface confirms; stale IDs; cancellation before confirmation; pending/busy selection guards; edited context invalidation; dismissal/session clearing; and rejection of a non-cooperative late draft. Touch-flag tests exercise the guard predicate, not Android input dispatch. Reconstructed-binding tests do not prove physical Activity rotation or process restoration.

Lint XML: **0 errors/fatal findings, 18 warnings**. Warning IDs/counts: `AndroidGradlePluginVersion` 1, `GradleDependency` 3, `NewerVersionAvailable` 2, `DataExtractionRules` 1, `UnusedResources` 2, `MissingApplicationIcon` 1, `ViewConstructor` 1, `UseKtx` 1, `SetTextI18n` 6. Warnings were not suppressed or treated as passing physical acceptance. SDK XML-version and existing deprecated API warnings also appeared during the build.

`git diff --check` passed. The production/configuration diff is limited to MainActivity, OverlayPanel and ReplyPresentation. The existing dismissal-fixture repair was inherited from Primary's baseline, not implemented here.

## Actual physical APK and device

- APK: `app/build/outputs/apk/debug/app-debug.apk`, **26,109,226 bytes**.
- SHA-256: **`9a0b5e7ee6431f05430da154086884ce4612a91bb6292d3e5e9d3e42c9342602`**.
- AAPT metadata: `ph.merd.akma`, version `0.1.0` / code `1`, minSdk **30**, targetSdk **36**.
- Selected physical USB device: **TECNO LE7**, manufacturer **TECNO MOBILE LIMITED**, Android **11**, API **30**, ABI **arm64-v8a**. The selected device was authorized, physically connected by USB and not the separately attached emulator. Its serial is excluded from this evidence.
- `adb install -r` returned **Success**. The installed base APK was pulled from `pm path ph.merd.akma`; its SHA-256 matched the built APK exactly.

Miguel confirmed an exclusive device slot and paused other ADB/model work. Initial reservation and final release are recorded in [slot reservation](https://github.com/merd-labs/akma/pull/11#issuecomment-6085439815) and [slot release](https://github.com/merd-labs/akma/pull/11#issuecomment-6085873842). Miguel explicitly confirmed that other ADB work remained paused for the final notification/cleanup checks. No further ADB work followed release.

All ADB commands targeted that physical serial. Commands included `shell getprop` for identity/API/ABI, `install -r`, `shell pm path ph.merd.akma`, `pull` for APK verification, `shell am start -n ph.merd.akma/.MainActivity`, scoped window/service/IME metadata, explicit UI taps, `exec-out screencap -p`, `shell am force-stop ph.merd.akma`, and `logcat -d --pid=<current Akma PID> -s AndroidRuntime:E`. Serial/PID placeholders here deliberately omit device identifiers. Temporary UI XML was removed; UI dumps and screenshots were restricted to Akma foreground with recognized synthetic input. No raw logcat, private chat data, notification contents or model files are committed.

## Physical observations

| Check | Observed result |
| --- | --- |
| Existing manual overlay permission | `SYSTEM_ALERT_WINDOW: allow`; no ADB permission changes. |
| Explicit Messenger synthetic Copy/Paste | Miguel reported Paste working. Akma-only UI inspection matched a harmless 55-character synthetic fixture, including its trailing newline. |
| Input focus/software keyboard | Akma panel owned focus, one overlay existed, IME reported its input view shown; `fresh-paste.png` visibly shows the software keyboard. |
| Three panel Close/reopen cycles | PASS: panel became a bubble and reopened; dismissed synthetic message did not reappear. |
| Three whole-assistant Close/Open cycles | PASS: zero windows after Close, one bubble after Open, then panel/bubble transitions. |
| Manual notification Close | PASS: zero Akma overlays and no Akma service after Miguel tapped Close. |
| Force-stop/relaunch | PASS: Activity showed `0/1,500 characters`, no automatic overlay and no service. This proves clean relaunch, not arbitrary low-memory recreation. |
| Current relaunched process AndroidRuntime errors | Zero `FATAL EXCEPTION` matches in the current Akma PID's error log. This is not a crash-history audit. |

The requested synthetic sentence and the already-copied fixture differed in wording and newline. The initial literal-match assertion failed. The fixture was then restricted to harmless synthetic text before any screenshot. After six successful toggle cycles, one immediate UI-dump assertion following Paste also failed; a subsequent Akma-only inspection matched all 55 characters, and the keyboard screenshot passed visual inspection. These helper failures are retained as limitations. No reproducible app defect was established; the snapshot mismatch's cause is unknown.

Screenshots are privacy-reviewed local artifacts under the ignored `app/build/evidence/pova2-bridge/` directory: `fresh-paste.png` shows synthetic input and keyboard; `paste-keyboard.png` shows shared synthetic Activity/panel context after bringing Activity foreground, **without a visible keyboard**. Neither shows personal chats or notification contents. They are provided through local handoff links, not committed as application assets.

## Outstanding gates — NOT TESTED

The installed production build still uses **UnavailableReplyEngine**. Physical Analyze/Select/Confirm/Cancel/Generate/Edit/Copy of a real draft, semantic faithfulness and offline inference are **NOT TESTED**. No fake engine was added to make those steps appear functional. Binding unit tests cannot establish actual on-device inference or rendered confirmation-control behavior.

Permission denial/regrant, physical obscured-touch dispatch, Activity rotation with pending confirmation, unexpected OS process death, screen-off endurance, extended HiOS battery restrictions and API 34 were **NOT TESTED on this APK**. Earlier overlay evidence does not replace retesting this build. USB attachment also limits battery conclusions.

The agreed domain/overlay prerequisites are already merged. Miguel/team should review this focused UI bridge, coordinate the real engine adapter with its owner, then rerun local/hosted gates and the full physical confirmation-to-manual-copy sequence on the integrated APK. No merge or reviewer assignment is performed by this agent.
