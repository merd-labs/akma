# MERD Akma — shared coding-agent rules

Read `README.md`, `docs/START_HERE.md`, `docs/PRD.md`, `docs/CONTRACT.md`, `docs/DECISIONS.md`, `docs/AGENT_WORKFLOW.md` before modifying code.

Scope: Android-native Kotlin, Compose main Activity, Android Views overlay, user-invoked copying/pasting, local phone LLM, no backend or cloud AI. Preserve the contract. Code generated must be reproducible and reviewed by a human. Never claim a fake/mocked response is from real AI. Android 11 Pova 2 test gate precedes model lock-in.

Only edit your assigned files/branch. Do not edit shared Gradle files, the contract, models, manifests or lockfiles without the owner coordinating. Build/test locally when possible and report exact command and actual result; note when checks cannot run. Never commit secrets, `.env`, `local.properties`, model weights, keystores, personal chat messages, or credential files. Do not add AccessibilityService, notification listeners, silent clipboard capture, auto-send or network inference.

Keep changes small, reviewable, compile-ready. Status must distinguish implemented, stubbed/mock and planned. Prioritize one live offline demo; cutoff October 10, 10:00 AM PHT.

Official identity: Akma (`ph.merd.akma`, verified repository `merd-labs/akma`). Original source drafts under `docs/reference/elijah/` are preserved; do not edit them as part of a rename. See `docs/BRAND_AND_PROVENANCE.md`.

Bootstrap ownership: Miguel coordinates Gradle, wrapper and manifest; Elijah reviews Android integration. Spec Kit and Matt Pocock Skills have one owner, Miguel, and remain deferred until the baseline merges. Activity and overlay use a model-unavailable engine; this is not an offline AI demo. Synthetic engine output belongs only in unit tests.
