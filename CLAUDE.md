# Claude Code guidance — Akma

Follow `AGENTS.md` and the MERD-approved docs in `docs/`. Avoid competing with the Spec Kit workflow or overwriting other agents' branches. Keep a clear report of modifications and tested behavior. No automatic push/merge/release or permissions escalation without explicit human approval.

Use JDK 17 and the checked-in wrapper for `:app:assembleDebug :app:testDebugUnitTest`. Miguel owns shared Android configuration and optional tooling setup; Elijah reviews integration. Do not interpret the model-unavailable Activity as completed inference or overlay work.
