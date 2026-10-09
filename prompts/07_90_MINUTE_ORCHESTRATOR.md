# Prompt 07 — Lead agent: launch Milestone 1 (bounded)

You are Miguel's engineering assistant for MERD / Akma. We are already inside the authorized AppBuildersPH Hackathon 2026 development window. READ: `AGENTS.md`, `docs/NEXT_90_MINUTES.md`, `docs/PRD.md`, `docs/CONTRACT.md`, `docs/DECISIONS.md`, `docs/MODEL_VALIDATION.md`, `docs/DEVICE_MATRIX.md`.

Confirmed phone: Tecno Pova 2 LE7, Android 11/API 30, 6 GB physical RAM, 128 GB capacity; 4 GB support is untested. Other phones: Infinix X6815B Android 11 and Camon 30 Android 14.

GOAL: coordinate a compilable, Android-11-compatible Kotlin/Compose skeleton and a separate genuine on-device LLM feasibility spike. No backend, cloud inference, iOS, automatic message scraping, real system keyboard, or AccessibilityService.

First inspect git status, existing Gradle files and installed SDK/JDK/NDK versions. Do not invent dependency compatibility; do not overwrite human-approved docs/contracts. Assign sole ownership of Gradle/manifest to Elijah; inference spike to Miguel; UI to Danielle; QA to Rhence. Do not launch multiple agents in the same worktree. Refuse to claim a passing build or local AI execution without actual logs/evidence.

SPECIAL RISKS: upstream `llama.cpp/examples/llama.android` currently sets minSdk 33 and cannot be copied unchanged for API 30; LiteRT-LM versions may have distinct JDK requirements. Check vendor sources and use pinning. Try official Qwen2.5-0.5B-Instruct-GGUF Q4_K_M as a small CPU candidate only if license and exact model file are verified. Runtime choice remains provisional until device proof. Do not add weights to Git.

OUTPUT: (1) factual environment report, (2) exact worktree branches and assignments, (3) minimal implementation plan, (4) commands executed with success/failure, (5) remaining blockers and next handoff. Then stop and await a human integration decision.
