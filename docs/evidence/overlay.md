# Overlay lifecycle evidence

Owner: Miguel / Codex Secondary. Human review: Miguel and Elijah.
Branch: `feat/overlay-paste`; bootstrap dependency: `02df71a`, draft PR #4.
Identity remains Akma / `ph.merd.akma`. The supplied phase-two pack is read-only input, not part of this change.

## Implemented behavior

- Only the existing Activity's non-null, actionless Open intent attaches the window. Close, unexpected actions and null restart intents stop the service. `START_NOT_STICKY` is preserved.
- Each service instance owns at most one panel and reply-state collector. A stopped instance cannot reopen; partial attachment failures also release resources.
- Panel Close, notification Close, permission revocation, unexpected window detachment and service destruction use idempotent cleanup. Cleanup unregisters the app's permission watcher, cancels the collector, releases input focus and removes the window and foreground notification. Pending inference is cancelled only when this service owned a panel.
- The explicit **Paste message** button requests input focus, checks window focus and invokes Android's native paste action only on that click. It is disabled during inference. There are no clipboard listeners or background reads. Copy remains an explicit draft-button action and never sends a message.
- Close stays outside the scrolling body. Text synchronization runs in guarded `afterTextChanged`, preserving selection when possible. No private input is written to disk; process death loses input and requires another explicit Open.

No shared manifest, foreground-service type, notification permission, Gradle, Activity, model or domain contract changes were made. The engine remains unavailable and produces no AI output. A draggable bubble is deferred; this change fixes the existing panel.

## Local checks

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

## Emulator checks

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

## Physical acceptance and owners

All physical checks are **NOT RUN**: no physical phone is attached. Pova 2 LE7/API 30 remains the required gate; Infinix Zero 5G/Android 11 and Camon 30/Android 14 remain secondary. Available memory/storage, HiOS/XOS behavior, local runtime compatibility and inference success are unverified.

Reserve one phone operator. On Pova 2, install the verified debug APK, deny overlay consent in Android settings and confirm Activity fallback; then grant consent and explicitly Open. Use a synthetic Viber/test-chat message, focus the overlay input and tap Paste message. Check keyboard/scrolling and accessible Close, 20 Open/Close cycles, notification Close, permission revocation, screen off/on, rotation and process recreation. Confirm no overlay returns automatically and no private text survives process death. Record expected/observed behavior, device/API, APK SHA and synthetic screenshots.

After the runtime owner supplies genuine local inference, generate and edit a reply, tap Copy and manually paste into the test chat without sending. Copy-reply UI and airplane-mode inference acceptance are **BLOCKED** by the unavailable production engine; no production fixture is supplied.

For HiOS/XOS battery restrictions, use only documented user settings and manual restart. Do not whitelist silently, disable OEM controls through ADB, add automatic restart, or request battery exemptions without owner coordination. Any required Activity/manifest/notification change must be a minimal patch request to its sole owner; no such patch is currently established by observed evidence.

## Platform references

Android 10+ restricts clipboard access to the app with focus or the default IME. A foreground service alone is insufficient: [clipboard privacy](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data).
`AppOpsManager.startWatchingMode` observes only this app's UID: [API documentation](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,%20java.lang.String,%20android.app.AppOpsManager.OnOpChangedListener)).
Text may be changed in guarded `afterTextChanged`, not `onTextChanged`: [TextWatcher](https://developer.android.com/reference/android/text/TextWatcher).
