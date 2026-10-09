# Prompt 03 — Danielle: UI/UX implementation

Work on `feat/assistant-ui` in isolated worktree. Read AGENTS, PRD, CONTRACT, ARCHITECTURE. Own Compose Activity screen, colors/typography/assets and view state rendering; DO NOT alter local engine implementation, wrapper versions, shared models or Android service without agreement. Use the provided mockups as visual references, not screenshots of actual implemented behavior. Android only: no iPhone visuals.

Deliver a polished but minimal interaction: privacy/onboarding; overlay permission/model readiness; explicit message paste; intent/action cards with labels; professional tone; real generation progress; editable draft; copy; recoverable error and no-model state. Support 1080x2460 tall phones and safe area/punch-hole; make hit targets accessible and text legible. No false on-device success indicators or static output presented as AI. UI can initially bind to an explicitly marked FakeReplyEngine in debug-only development, never in release/demo.

**Handoff:** screenshots of built app (not generated marketing image), changed files, tested screens/devices, known UX gaps, merge dependencies.
