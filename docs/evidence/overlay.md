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

The agent released the ADB slot at `2026-10-09T21:06:38+08:00` after stopping the toggle run when Messenger became foreground. Further automation requires a new coordinated slot.

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
adb -s <physical-serial> shell dumpsys activity services ph.merd.akma
adb -s <physical-serial> shell dumpsys battery
adb -s <physical-serial> shell dumpsys deviceidle
adb -s <physical-serial> shell dumpsys deviceidle whitelist
adb -s <physical-serial> shell settings get global low_power
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
| Messenger Copy / explicit Paste | PASS: synthetic source confirmed by Miguel; exact native Paste verified in retest below | Initial keyboard defect reproduced and fixed; see post-fix APK SHA |
| Repeated toggles | PASS on fixed APK: 20 panel cycles and 20 whole-assistant cycles | Initial run stopped after one cycle when Messenger became foreground; retained as incomplete historical run |
| Force-stop / process recreation | PASS on fixed APK: fresh manual Open with empty input | Debug SIGKILL denied; unexpected-death recovery remains unverified |
| Notification Close | PASS: Miguel tapped notification Close; zero Akma windows and no live OverlayService | Notification shade content was not read or captured |
| Manual permission revocation/denial | PASS: app-op deny, zero windows/service, Activity fallback and rejected Open | Permission changed by Miguel in Android settings only |
| Background with Messenger foreground | PASS: one Akma overlay and `OverlayService isForeground=true` | Own metadata only; no Messenger content capture |
| Screen off/on | PASS: 30 seconds off with one window/foreground service; bubble remained after normal unlock | USB charging; prolonged HiOS/Doze endurance remains unverified |
| Genuine local reply and manual Copy back to Messenger | BLOCKED | Production `UnavailableReplyEngine`; no fabricated reply or model lock-in |

The host helper reads UI only while Akma's Activity is foreground and stops if another app is resumed. Metadata checks filter Akma's own overlay windows. No clipboard inspection or third-party conversation dump is used. Screenshots are ignored local evidence under `app/build/evidence/pova2/`: `bubble.png`, `panel-reopened-empty.png`. The latter shows empty overlay and Activity inputs after Close. A visible “Grammarly has stopped” toast belongs to another installed app and is not evidence of an Akma crash. No action was taken against that app.

The sanitized power snapshot showed USB charging (`status: 2`), 27% battery, temperature 253 tenths of a degree Celsius, screen on, deep/light idle states ACTIVE, battery saver `0`, and no Akma entry in the power whitelist. These observations establish the current test conditions, not survival under HiOS battery restrictions. No power setting was modified.

Remaining physical checks require the handset operator. Keep screenshots limited to Akma and synthetic content; do not capture personal notifications. HiOS/XOS investigation must use observed behavior and user controls, without silent whitelisting, ADB battery bypasses or automatic restart. Infinix Zero 5G and Camon 30 are not tested by this run.

## Focused Paste regression and retest

Miguel confirmed that the copied Messenger group-chat text was synthetic, then reported that **Paste message** did not open the software keyboard. The initial APK reproduced this on LE7/API 30: native Paste inserted the synthetic text and the message field was focused, but `mInputShown` remained false. This is an actual physical defect, not an emulator inference.

`OverlayPanel` now posts a normal `InputMethodManager.showSoftInput(message, SHOW_IMPLICIT)` request after the explicit Paste click. It checks that the field is still attached and its window still focused; closing before delivery cannot reopen the keyboard. It never uses SHOW_FORCED or reads the clipboard outside the native user action. No Activity/manifest/Gradle change is needed.

Miguel replied “Ready” for the retest; a new slot was announced at <https://github.com/merd-labs/akma/pull/11#issuecomment-6081518189>. No other active local ADB client was observed. The same build/test/lint command passed again: `BUILD SUCCESSFUL in 1m 17s`, 15 executed / 36 up-to-date tasks, 40 tests with zero failures/errors, lint zero errors / 17 warnings.

Fixed APK SHA-256: `f358bcd75a7d3319d8a52fca0a2df75abce2a1d9942cfab41e4c1e87ba7cb2af`. Installation returned `Success`. From an empty panel with keyboard hidden, one explicit Paste inserted exactly `Synthetic Akma test: can we move our meeting on friday` (the actual synthetic Messenger fixture). The field was focused and `mInputShown=true`. **PASS**. Close panel then cleared content, hid the keyboard, retained one nonfocusable bubble, and reopening showed empty input. **PASS**. The OS clipboard stayed available; no application clipboard clear or background read was added.

Regression evidence: local screenshots `paste-keyboard-before-fix.png` and `paste-keyboard-fixed.png`, captured only with Akma foreground and confirmed synthetic input. The successful post-fix screenshot shows the native software keyboard and accessible Close. Post-fix replay: **PASS**, 20 Close-panel/reopen cycles, with one root throughout and empty message after every reopening; then **PASS**, 20 Activity Close-overlay/Open-overlay cycles, with zero windows after Close and exactly one nonfocusable bubble after Open. Command:

```bash
PYTHONPATH=app/build/evidence/pova2 python3 -u app/build/evidence/pova2/toggle_smoke.py
```

The local ignored harness locates fresh app controls, taps the bubble using observed owned window geometry, and waits up to eight seconds for window counts. It requires Akma foreground before any UI dump. The earlier interrupted run remains recorded and is not counted as passing.

Process-death probe: **BLOCKED** on the physical LE7. `run-as ph.merd.akma kill -9 <observed-own-PID>` returned `kill: unknown pid`; the app and its overlay remained alive. Retrying with the debug UID shell builtin returned `Permission denied`. No root or security-control bypass was attempted. This is a failed host debug probe, not a demonstrated Akma crash or passing unexpected-death test. An explicitly labelled own-app force-stop/relaunch test follows; it cannot prove unexpected-death restart semantics.

Own-app force-stop/relaunch: **PASS**. With the synthetic fixture pasted, `am force-stop ph.merd.akma` removed the process and all Akma windows. Zero windows remained for a 10-second observation. Explicit Activity launch and Open then created one window with empty input. Screenshot: `process-reopened-empty.png` (fixed APK). This is a fresh process after a user-equivalent app stop, not proof of recovery from SIGKILL/OEM termination.

```bash
PYTHONPATH=app/build/evidence/pova2 python3 -u app/build/evidence/pova2/process_smoke.py
adb -s <physical-serial> shell am force-stop ph.merd.akma
adb -s <physical-serial> shell am start -n ph.merd.akma/.MainActivity
```

Direct Messenger retest: **PASS**. Miguel confirmed synthetic Paste and keyboard display over Messenger and explicitly authorized live display and Logcat inspection. ADB observed Messenger resumed, one Akma panel, Akma as the focused window, and `mInputShown=true`. Authorized live inspection showed the same synthetic fixture and native keyboard over Messenger. Akma-only Logcat sampled two lines, zero E-level entries and no `FATAL EXCEPTION`; this bounded sample is not a guarantee of no earlier crash. The live capture included unrelated material behind the panel, so it was discarded after inspection and is not handoff evidence. Clean Akma-only before/after screenshots are retained outside Git.

```bash
adb -s <physical-serial> shell dumpsys activity activities
adb -s <physical-serial> shell dumpsys window
adb -s <physical-serial> shell dumpsys input_method
adb -s <physical-serial> shell logcat -d --pid=<observed-own-PID> -v brief -t 200
# One explicitly authorized live screencap; discarded after verification.
adb -s <physical-serial> exec-out screencap -p
```

No notification shade content or third-party UI XML was read. No production screenshot or Logcat collection feature was added.

Notification Close: **PASS** on the fixed APK. Miguel manually closed the panel, then tapped Close on Akma's foreground notification. ADB confirmed zero Akma overlay windows and no live `OverlayService`. No shade UI dump, screenshot or notification listener was used. One bubble was then reopened and Android's own overlay-permission settings opened for manual revocation testing.

Manual permission revocation/denial: **PASS**. With the bubble active, Miguel disabled overlay access in Android settings. ADB observed `SYSTEM_ALERT_WINDOW: deny`, zero Akma windows and no live overlay service. The first UI probe stopped because Akma was not resumed; explicitly launching Akma produced the permission-required fallback and Grant button. The harness initially treated the label's `enabled=true` as the button's state; this assumption failed because Compose exposes a separate TextView child. A direct tap on Open produced no window/service during a two-second observation. Screenshot: `permission-denied.png`. No app-op set/grant command was used on the physical phone. Manual regrant is requested next; Open must remain user-invoked.

Manual permission regrant: **PASS**. Miguel enabled overlay access and returned to Akma without pressing Open. ADB observed `allow`, the Activity's granted state, zero windows and no live overlay service. Only a subsequent explicit Open created one bubble. Regrant alone did not reopen the assistant.

Screen off/on: **PASS** for this bounded USB-charging run. Miguel switched the screen off manually; ADB observed `mScreenOn=false` and retained one Akma window plus `OverlayService isForeground=true` throughout 30 seconds. Miguel then unlocked normally and confirmed the bubble remained. No lockscreen capture, unlock bypass, forced Doze, battery exemption or OEM setting change was used. Command:

```bash
PYTHONPATH=app/build/evidence/pova2 python3 -u app/build/evidence/pova2/screen_smoke.py
```

This establishes short screen-off survival under charging, not extended HiOS battery management, memory pressure or airplane-mode inference. No XOS secondary device was tested.

After normal unlock, ADB confirmed screen on and the same foreground service. The bubble opened an empty panel. Final panel Close plus Activity Close removed all Akma windows/service. Screenshot `after-screen-on-empty.png` is clean local evidence. The retest ADB slot was released at `2026-10-09T21:34:26+08:00`; no ADB/model operation remains running for this task. Permission remains enabled only by Miguel's manual consent.

## Git and hosted CI handoff

Implementation commit: `759c3f362e32e1ac87c7527b7bc90912181e748b`, pushed non-force to the verified `merd-labs/akma` branch `feat/overlay-paste`. Draft PR #12 stays against `chore/akma-bootstrap`; bootstrap PR #4 remains open. Jairus (`jairuss0`) is the requested independent reviewer. No merge or publicity action was taken.

Hosted implementation push: Android build/unit-test job **PASS** in PR workflow run `37934145509`. Both Android jobs also passed for evidence head `c3ce75f` and final source head `4080a8a`; final-source run IDs are `37936306770` (push) and `37936313949` (PR). Documentation job **FAIL**, exit 2: `git show --format= --check HEAD` reported 16 historical whitespace findings, beginning at `docs/reference/elijah/01_PRODUCT_REQUIREMENTS.md:3`. The shallow checkout treats the commit as a root and scans preserved originals; local diff whitespace checks pass. The owner has separate open PR #13 for checkout depth; its Android and documentation jobs are observed passing. It has not been merged into this branch. The full PR therefore must not be reported as green.

All five changed files are scoped text source/tests/evidence. Staged contents were reviewed for credentials, private keys, model weights, APKs, keystores and personal chat text; none were staged. `git diff --check`, cached whitespace check and active-doc branding scan passed (historical references/provenance excluded). No shared-file changes or new production dependencies were introduced. Coordinator API usage and independent review were requested on <https://github.com/merd-labs/akma/pull/7#issuecomment-6081424227>.

Outstanding: unexpected-death recovery (debug SIGKILL denied), prolonged HiOS behavior, genuine offline inference and draft Copy. These are not passing hardware checks. Notification/UI/ID changes, if needed, go to their owners. Tests validate cleared draft/readiness behavior with test-only engines, not real on-device inference.

## Platform references

Android 10+ restricts clipboard access to the app with focus or the default IME. A foreground service alone is insufficient: [clipboard privacy](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data).
`AppOpsManager.startWatchingMode` observes only this app's UID: [API documentation](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,%20java.lang.String,%20android.app.AppOpsManager.OnOpChangedListener)).
Text may be changed in guarded `afterTextChanged`, not `onTextChanged`: [TextWatcher](https://developer.android.com/reference/android/text/TextWatcher).
