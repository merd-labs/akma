# AI-assisted engineering and ownership

Agent capacity is not a concurrency target. Each human owns and reviews one bounded branch or worktree. `main` always remains installable once first Android project is bootstrapped.

| Owner | Branch | Scope |
|---|---|---|
| Miguel | `feat/local-inference` | Model candidate and LocalReplyEngine adapter, no UI or shared Gradle edits without coordination |
| Elijah | `feat/android-overlay` | Overlay, permissions, service lifecycle; owns initial Gradle skeleton and wrapper |
| Danielle | `feat/assistant-ui` | Compose Activity and UI assets; depends only on shared contract |
| Rhence | `test/workflow-qa` | Test fixtures, acceptance log, documentation/pitch, no app source mutations |

Before work, read README, PRD, CONTRACT and DECISIONS. Every agent reports files changed, test commands/results, assumptions, remaining blockers, and whether its output is mocked or based on real model inference. Human review and merge after checks. Protect `docs/CONTRACT.md`, Gradle wrapper/dependency versions and model metadata with one owner.

Typical Git worktree (after repo cloned and baseline commit):

```bash
git fetch origin
git worktree add -b feat/local-inference ../akma-inference origin/main
```

Windows PowerShell: `git worktree add -b feat/assistant-ui ..\akma-ui origin/main`.

Do not use Git `reset --hard`, force pushes, rebase other developers' branches or commit secrets. No agent may push/release/submit without explicit human approval.
