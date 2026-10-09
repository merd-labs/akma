# Prompt 02 — Elijah: Android overlay and permissions

Work on `feat/android-overlay` in isolated worktree. Read AGENTS, PRD, CONTRACT, ARCHITECTURE and SECURITY. Own `overlay/` and manifest changes only after coordinating with Android skeleton owner. No model/runtime changes.

Create user-invoked, non-exported overlay using WindowManager TYPE_APPLICATION_OVERLAY; request/verify SYSTEM_ALERT_WINDOW via Android Settings. Implement compact bubble and expand/collapse View panel; focusable multiline input for manual paste with keyboard; copy/reply handoff through shared coordinator contract. No background clipboard monitoring, accessibility, notification scraping, auto-send or permanent background services. Handle permission revoked, orientation, HiOS/XOS OEM process death, and Android 14 restrictions; normal Activity fallback is permitted when clearly presented.

**Acceptance:** on Pova 2, bubble over ONE chosen chat app, tap opens text input, paste works, soft keyboard appears, close/reopen 3x without crash, taps outside bubble are not swallowed. Record actual device results; use stub coordinator only visibly marked mock until local AI merged. Report missing tests, file ownership and exact build output.
