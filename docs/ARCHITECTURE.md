# Architecture — Native Android, offline by design

```text
Source messaging app (user copies manually)
             |
        Android clipboard
             | explicit paste in focused UI
MainActivity (Compose onboarding/model/permissions)
             |
OverlayService + WindowManager (Android Views; manual show/hide)
             |
       ReplyCoordinator (state)
             |
        LocalReplyEngine  <--- interface boundary
          |          |
   analyze (optional) draft (must be local AI)
          |          |
    validated result / recoverable error
             |
       editable draft → clipboard copy → manual send
```

- App package: `ph.merd.akma` (MERD-selected application ID; verify uniqueness before publishing).
- Explicit overlay permission using `Settings.canDrawOverlays()` and `ACTION_MANAGE_OVERLAY_PERMISSION`. Service is non-exported; no background auto-start.
- Overlay is not a clipboard surveillance mechanism. On Android 10+, background clipboard is restricted. User pastes to visible, focused field.
- Avoid keeping user messages on disk, Logcat, analytics, crash reports or cloud. Clipboard is OS-managed after copying and cannot be guaranteed private.
- No `INTERNET` permission in main release variant. If model import/download is needed, provision explicitly from trusted local file to app-private storage and document provenance.
- Prefer exactly one model instance, loading on worker dispatcher, serialized requests (Mutex), cancellation, no repeated multi-GB allocation on bubble open/close.
- For Android 14, respect foreground service restrictions; avoid persistent background services by default. Build Activity fallback if OEM process killer terminates overlay.
- Compose for visible Activity; Views in overlay to avoid ComposeView lifecycle owner wiring during hackathon.
- If runtime is changed, implement contract and record ADR in docs/DECISIONS.md; do not rewrite UI.
