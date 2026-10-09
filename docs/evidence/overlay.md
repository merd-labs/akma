# Overlay lifecycle evidence

Owner: Miguel / Codex Secondary. Human review: Miguel and independent reviewer Jairus (`jairuss0`); Android integration review: Elijah.
Branch: `feat/overlay-paste`; bootstrap dependency: `02df71a`, draft PR #4.
Identity remains Akma / `ph.merd.akma`. The supplied phase-two pack is read-only input, not part of this change.

## Implemented behavior

- Only the existing Activity's non-null, actionless Open intent starts the assistant. One attached root switches between a nonfocusable 64 dp bubble and a focusable native Views panel. Duplicate Open/expand/collapse requests do not attach duplicate windows.
- **Close panel** clears the application message, analysis, draft and notices, cancels pending work, hides the keyboard and returns to the bubble. Activity **Close overlay** and notification **Close** stop the entire assistant and clear the same session.
- Permission revocation, unexpected detachment and service destruction release the owned window and foreground notification. Cleanup unregisters the app's permission watcher and cancels the collector. Null/unknown restart intents stop the service; `START_NOT_STICKY` remains.
- Clearing uses the existing main-thread `ReplyCoordinator.cancel()` followed by `setMessage("")`. Tests prove that late noncooperative results cannot restore cleared content and that initialized model readiness remains available. No shared coordinator API was changed.
- **Paste message** requests input focus, checks window focus and invokes Android's native paste action only on that click. No clipboard listeners or background reads exist. Copy remains an explicit draft-button action and never sends a message. Closing clears application state, not the OS clipboard needed for manual chat Paste.
- Close stays outside the scrolling body. Guarded `afterTextChanged` synchronizes text and preserves selection. Input is not saved or autofilled; process death loses the session and requires explicit Open.

No shared manifest, foreground-service type, notification permission, Gradle, Activity, model or domain contract changes were made. The production engine remains unavailable and produces no AI output. The bubble is stationary; dragging is deferred. The installed application ID remains `ph.merd.akma`; the newer pack's proposed `ph.merd.akmaai` requires the manifest/Gradle owner's coordinated decision and is not silently migrated here.

## Earlier local checks (pre-bubble APK)

Ubuntu host, installed JDK 17, existing SDK/Gradle pins. Executed in the overlay worktree:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
git diff --check
```

Initial gate: **PASS**, exit 0, `BUILD SUCCESSFUL in 4m 31s`, 51 executed tasks.
Unit tests: **32 passed**, zero failures/errors/skips: 24 existing domain tests and 8 overlay lifecycle tests. Overlay tests exercise the production ownership gate with synthetic callbacks; they do not instantiate Android windows or prove hardware behavior.
Lint: **PASS**, zero errors, 13 warnings. Build also reports existing SDK XML tooling mismatch, unstripped AndroidX native library, deprecated resize constant and Gradle deprecations. Dependencies were not upgraded to suppress warnings.
Header gate: **PASS**, exit 0, `BUILD SUCCESSFUL in 2m 17s`. Final gate including the explicit Paste button: **PASS**, exit 0, `BUILD SUCCESSFUL in 1m 38s`, 16 executed and 35 up-to-date tasks. All 32 tests passed again. Final lint: zero errors and **16 warnings**; the three added warnings concern inline UI text, following the existing overlay pattern.

The lifecycle tests cover explicit/null/Close/unexpected intents, denied permission, repeated Open, repeated Close/destruction, partial attachment failure, revocation and 20 fresh sessions without retained attachments.

## Earlier emulator checks (pre-bubble APK)

Device: `emulator-5554`, `sdk_gphone64_x86_64`, Android API 36. This is **not** the Android 11/Pova 2 acceptance device.

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n ph.merd.akma/.MainActivity
adb -s emulator-5554 shell dumpsys window windows
adb -s emulator-5554 shell dumpsys activity services ph.merd.akma
```

Observed install: **PASS**, `Success`. Open after notification consent attached one `APPLICATION_OVERLAY` window.
A first UI dump returned a null root; a first immediate Close assertion raced window removal. The host harness now removes stale XML dumps and waits up to eight seconds for the expected window count. These harness failures were not passing app tests.
The emulator selected input text but did not display a native floating Copy/Paste toolbar. This directly motivated the explicit Paste button. Keyboard Ctrl+C copied only the selected synthetic `SyntheticOverlayCheck` text; after deleting it, the new Paste button restored that exact text. This checks clipboard interaction, not AI generation.

Observed checks:

| Scenario | Result | Limit |
|---|---|---|
| 20 Open/Close cycles | PASS: one window on Open, zero after Close | Initial lifecycle run and final APK run both passed; final replay below |
| Denied permission | PASS: Activity fallback and grant-settings path | Initial denial simulated with ADB app-op; Activity refreshed after resume |
| User grant in Android settings | PASS: grant observed; no automatic Open on return | API 36 emulator only |
| Explicit Paste and keyboard | PASS: exact synthetic text pasted; Close remains accessible | Native floating selection toolbar absent |
| Overlay over another app | PASS: Settings foreground, panel shown; Close removes it | Wait for Settings transition; no chat-app acceptance claim |
| Notification Close | PASS: expanded Akma notification Close removes window | Notifications were explicitly allowed |
| Active permission revocation | PASS: no overlay window or live overlay service remains | Revocation simulated with ADB app-op |
| Process kill and recreation | PASS: no automatic reopen, empty input on fresh manual Open | Debug process killed with `run-as`; not an OEM low-memory test |

Additional executed platform commands:

```bash
adb -s emulator-5554 shell cmd appops set ph.merd.akma SYSTEM_ALERT_WINDOW ignore
adb -s emulator-5554 shell cmd appops get ph.merd.akma SYSTEM_ALERT_WINDOW
adb -s emulator-5554 shell cmd appops set ph.merd.akma SYSTEM_ALERT_WINDOW allow
adb -s emulator-5554 shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_C
adb -s emulator-5554 shell input keyevent KEYCODE_DEL
adb -s emulator-5554 shell am start -a android.settings.SETTINGS
adb -s emulator-5554 shell cmd statusbar expand-notifications
adb -s emulator-5554 shell cmd statusbar collapse
adb -s emulator-5554 shell pidof ph.merd.akma
# Used the observed numeric PID with: adb shell run-as ph.merd.akma kill -9 <PID>
```

UI actions used fresh `uiautomator dump` XML and `input tap`/long-press gestures. A denied-permission harness initially brought an already-resumed Activity to the front without refreshing it; Home/resume corrected the setup. A tap during the Settings transition was retried after the transition settled. These attempts are not counted as passing checks.

Final APK SHA-256: `ab6feebbc168454961409e136b7049a9787029b27efe94bbe3a69c1c46aed02c`.
Final APK toggle replay: **PASS**, exit 0, 20 cycles with exactly one window on Open and zero after Close. Command `python3 app/build/evidence/overlay/toggle_smoke.py`. A faster replay using cached bounds missed Close after 16 successful cycles and timed out; this is not counted as a passing run. The successful final replay locates fresh visible controls before each tap. Fast-tap/OEM behavior remains a physical-device gate. The host replay and helper are ignored local evidence artifacts, not app source.
Screenshots use only synthetic input and are kept outside Git under ignored `app/build/evidence/overlay/`; APKs, private chats and model weights are not committed. Final screenshots: `explicit-paste.png`, `overlay-over-settings.png`, `permission-denied.png`, `notification-closed.png`, `process-reopened-empty.png`. Local UI helper is `ui_helper.py`; APK is `app/build/outputs/apk/debug/app-debug.apk`.

`git diff --check` passed. Active-doc scan for historical branding (excluding preserved reference files and provenance explanation) returned no matches. Only two overlay classes, one scoped test file and this evidence document are staged; no manifest/Gradle/contract changes or binary artifacts.

## Physical Pova 2 run — 2026-10-09

Operator: Miguel. Messenger was selected temporarily for testing. The exclusive overlay slot was announced on benchmark PR #11: <https://github.com/merd-labs/akma/pull/11#issuecomment-6081257150>. No active local ADB client/model benchmark was observed before starting. This is a coordination notice, not proof that another host is idle. No model benchmark, OEM-control bypass or unrelated process termination was performed.

The sole authorized non-emulator ADB target was selected explicitly for every command. Its serial is omitted from this report. Verified properties:

- Manufacturer: `TECNO MOBILE LIMITED`; model: `TECNO LE7`.
- Android: `11`; API: `30`; ABI: `arm64-v8a`.
- Display build: `LE7-H697GHIJKL-R-GL-220929V706`; `ro.kernel.qemu` was empty.
- Available memory/storage and inference compatibility remain unverified. The simultaneously connected API 36 emulator was not used for these observations.

Exact installed debug APK SHA-256: `bb41ae8fe1fde8c3e4deef57832533736a4b1b5fc476dea27912a1f17e5e7570`.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
# <physical-serial> below means the verified LE7; never omit -s with multiple devices.
adb -s <physical-serial> shell getprop ro.product.manufacturer
adb -s <physical-serial> shell getprop ro.product.model
adb -s <physical-serial> shell getprop ro.build.version.release
adb -s <physical-serial> shell getprop ro.build.version.sdk
adb -s <physical-serial> shell getprop ro.product.cpu.abi
adb -s <physical-serial> shell getprop ro.build.display.id
adb -s <physical-serial> shell getprop ro.kernel.qemu
adb -s <physical-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <physical-serial> shell am start -a android.settings.action.MANAGE_OVERLAY_PERMISSION -d package:ph.merd.akma
adb -s <physical-serial> shell cmd appops get ph.merd.akma SYSTEM_ALERT_WINDOW
adb -s <physical-serial> shell am start -n ph.merd.akma/.MainActivity
adb -s <physical-serial> shell dumpsys window windows
adb -s <physical-serial> shell dumpsys input_method
adb -s <physical-serial> shell input text SyntheticOverlayClearCheck
```

Build/test/lint: **PASS**, exit 0, `BUILD SUCCESSFUL in 1m 48s`; 51 tasks, 16 executed and 35 up-to-date. **40 unit tests passed**, zero failures/errors: 12 lifecycle, 4 real-coordinator dismissal, 11 coordinator and 13 validation tests. Lint: zero errors, 17 warnings. Test engines are synthetic fixtures only, never a production AI substitute. Install: **PASS**, `Success`.

Miguel manually allowed overlay access in Android settings and confirmed completion. ADB then observed `SYSTEM_ALERT_WINDOW: allow`; no ADB permission grant was used on this phone.

| Scenario | Observed result | Limit |
|---|---|---|
| Explicit Open | PASS: one 192×192 px / 64 dp native bubble | Actual LE7/API 30 window metadata and screenshot |
| Bubble to panel | PASS: same single overlay root becomes focusable panel | Default UIAutomator dump omits nonfocusable bubble; helper taps the observed owned `mFrame` center |
| Input focus and software keyboard | PASS: input `focused=true`, `mInputShown=true`; typed synthetic fixture visible | Native field tap, not background capture |
| Close panel / clear session | PASS: panel returns to one nonfocusable bubble; reopening message is empty | Physical message proof; draft and late-result cleanup additionally covered in unit tests |
| Messenger Copy / explicit Paste | PENDING human confirmation and focused Paste test | Miguel reports Danielle sent a message in group chat and he copied it; synthetic content must be confirmed before capture |
| Repeated toggles | INCOMPLETE: one panel cycle passed; run stopped on own-Activity foreground guard | Messenger became foreground; no third-party UI dump or screenshot was taken |
| Notification Close, manual revocation/denial, process recreation | NOT RUN on this physical APK yet | Earlier emulator results do not satisfy this gate |
| Background / screen off / HiOS restrictions | NOT RUN yet | USB charging prevents an extended battery/Doze acceptance claim |
| Genuine local reply and manual Copy back to Messenger | BLOCKED | Production `UnavailableReplyEngine`; no fabricated reply or model lock-in |

The host helper reads UI only while Akma's Activity is foreground and stops if another app is resumed. Metadata checks filter Akma's own overlay windows. No clipboard inspection or third-party conversation dump is used. Screenshots are ignored local evidence under `app/build/evidence/pova2/`: `bubble.png`, `panel-reopened-empty.png`. The latter shows empty overlay and Activity inputs after Close. A visible “Grammarly has stopped” toast belongs to another installed app and is not evidence of an Akma crash. No action was taken against that app.

Remaining physical checks require the handset operator. Keep screenshots limited to Akma and synthetic content; do not capture personal notifications. HiOS/XOS investigation must use observed behavior and user controls, without silent whitelisting, ADB battery bypasses or automatic restart. Infinix Zero 5G and Camon 30 are not tested by this run.

## Platform references

Android 10+ restricts clipboard access to the app with focus or the default IME. A foreground service alone is insufficient: [clipboard privacy](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data).
`AppOpsManager.startWatchingMode` observes only this app's UID: [API documentation](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,%20java.lang.String,%20android.app.AppOpsManager.OnOpChangedListener)).
Text may be changed in guarded `afterTextChanged`, not `onTextChanged`: [TextWatcher](https://developer.android.com/reference/android/text/TextWatcher).
