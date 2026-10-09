# Overlay hardening: measured effect of `filterTouchesWhenObscured` and `FLAG_SECURE`

Method: two throwaway APKs (`ph.merd.securityprobe.victim`, `ph.merd.securityprobe.attacker`; sources in `docs/security/probe/`,
driver `probe_run2.sh.txt`). Victim = a `TYPE_APPLICATION_OVERLAY` window with button **A** (plain) and **B**
(`setFilterTouchesWhenObscured(true)`), the same construction as Akma's panel. Attacker (different UID) = a translucent,
`FLAG_NOT_TOUCHABLE` overlay above both buttons (classic tapjacking). Taps via `adb shell input tap` on the victim's window frame.
Akma (`ph.merd.akma`) was never installed, stopped, cleared or touched; probes were uninstalled afterwards. No Akma code was changed.

| Device / API | Tapjack: A (plain) | Tapjack: B (`filterTouchesWhenObscured`) | `FLAG_SECURE` overlay vs `screencap` |
|---|---|---|---|
| Emulator, Android 16 / API 36 | tap **delivered** through attacker (A: 1 → 2), alpha 0.5 and 0.9 | tap **dropped** (B stays 1) | non-secure: full-colour block captured (400 magenta px); secure: **0** magenta px (blacked out) |
| Tecno Pova 2 LE7, Android 11 / API 30 (probe apps only) | tap **delivered** (A: 1 → 2), alpha 0.5 and 0.9 | tap **dropped** (B stays 1) | non-secure: block captured (400 px); secure: `screencap` **exit 1, empty file** (screenshot refused) |
| Android 14 / API 34 | **NOT TESTED** (no image, no device) | NOT TESTED | NOT TESTED |

Not measured: `screenrecord`/MediaProjection of a secure overlay (expected black, **NOT TESTED**); window-alpha > 0.8 untrusted-touch
blocking on Android 12+ (probe set view alpha, not `LayoutParams.alpha`); a real third-party overlay app.

## Conclusions for Akma (what the data does and does not support)

1. **`filterTouchesWhenObscured` works** on API 30 and 36 for a view in an overlay window. Use it on controls whose tap has consequences:
   **Confirm** (to be added by the UI owner) and **Copy draft**. Do **not** set it panel-wide or on the bubble: a legitimate
   full-screen dimmer/blue-light-filter overlay (a different UID) then makes every control silently unresponsive. Only the
   exact tap point being covered triggers the filter; partial overlap elsewhere does not.
2. **`FLAG_SECURE` works** on the overlay window (screenshots black/refused) but it also blanks the **demo screen recording**
   (`docs/submission/SCREEN_RECORD_PLAN.md` requires showing the overlay). Recommendation: **do not enable FLAG_SECURE in the
   competition build**; if enabled later, make it a build-time switch that is on for release and off for the recording build.
   Residual risk accepted: a user-granted screen-capture tool can see pasted text and drafts.
3. Foreground-service early return (`OverlayService.kt:68-71`): see patch; the race is **REASONED only** — could not be provoked.

Patch for Secondary (owner of `OverlayPanel.kt` / `OverlayService.kt`): `docs/security/patches/overlay-hardening.patch`
(applies to current `main`; `:app:assembleDebug :app:testDebugUnitTest` exit 0 with it in a scratch composite). Contents: `filterTouchesWhenObscured` on the Copy button
(and a comment to do the same for Confirm), and `startForeground` before the early-return stop path. No FLAG_SECURE (see 2).
