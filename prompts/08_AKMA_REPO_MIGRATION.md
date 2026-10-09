# Prompt 08 — Akma rename/bootstrap agent (Miguel's Codex CLI)

You are MERD's lead repository bootstrap agent. The team selected **Akma** as the official name (earlier working name: ContextAI). We are already within the authorized hackathon period. Operate only against the actual local repository and the files supplied in `Akma_MERD_Bootstrap.zip`; never guess the organization slug or assume remote write access.

**Before edits:**
1. Inspect `pwd`, `git status --short`, current branch, remotes, and `gh auth status` without printing secrets. Determine whether this is (a) a new empty repository or (b) a working repository with commits/code.
2. Read `AGENTS.md`, `README.md`, `docs/BRAND_AND_PROVENANCE.md`, `docs/PRD.md`, `docs/CONTRACT.md`, `docs/START_HERE.md`, `docs/DECISIONS.md`, and `docs/PUBLISH_GITHUB.md`.
3. Report any existing changes and obtain human approval before replacing/conflicting with teammate work or creating/renaming/publishing a remote. Never overwrite history or force push.

**Goal:**
- Adopt official display name **Akma**; repository slug `akma`; Android application ID/namespace `ph.merd.akma` (unless already published under another ID, in which case flag migration impacts).
- Ensure README, agent files, active docs, scripts, GitHub templates, prompts and planned Gradle config use the new identity consistently.
- Preserve `docs/reference/elijah/` unchanged as historical original source material, including prior brand references. Preserve existing current project code and git history.
- Keep Kotlin-native Android, Jetpack Compose + Views overlay, explicit paste, manual user-approved copy, no backend, no cloud core inference, no AccessibilityService and no system keyboard in initial MVP.
- Primary phone: Tecno Pova 2 LE7, Android 11/API 30, 6 GB RAM and 128 GB capacity; available memory/storage and AI runtime success are NOT verified.
- Do not lock a local AI library or model until Pova 2 compatibility has been observed.
- Ensure Linux Ubuntu 24.04.5 and Windows 11 Pro 25H2 setup docs exist.

**Execution:**
- If project is new and docs-only, review package then create smallest Android Studio Gradle project on an isolated branch only after determining compatible installed JDK/SDK/Kotlin/AGP/Gradle versions; check in a genuine Gradle wrapper JAR and scripts. Do not fabricate builds or use invented versions.
- If app code already exists, do a reviewed name/namespace migration, preserving tests and build configuration. Never blindly replace a string inside model, class, or binary names.
- Prioritize `:app:assembleDebug` and `:app:testDebugUnitTest` before extensive Spec Kit or Skills tooling setup. Do not claim a passing Android CI while the repo is only documentation.
- Assign Spec Kit integration changes and Matt Pocock Skills setup to one owner. Use documented installed CLI syntax; don't generate duplicated agent configs. Do not start agent-driven large parallel implementations before the Android baseline/contract is merged.

**Checks and handoff:**
- Run `git diff --check`, check active docs for stale ContextAI references (excluding preserved `docs/reference/elijah/` and the provenance explanation), inspect staged files for secrets/weights, report actual verification output, and describe all known blockers.
- Never commit model weights, `.env`, `auth.json`, private keys, keystores or personal messages.
- Summarize files changed, exact commands/tests, pass/fail/not-run status, outstanding work and recommended PR/reviewers.
- Prepare the commit on a short-lived branch, but only push to the confirmed GitHub organization/repository after Miguel explicitly approves the remote, branch and visibility. No force push, auto-merge, or unrequested repository deletion.
