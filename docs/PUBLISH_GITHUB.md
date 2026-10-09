# Publish Akma — existing repository, explicit approval

Read-only inspection verified the existing repository as **`merd-labs/akma`**, public, with default branch `main` and origin `https://github.com/merd-labs/akma.git`. GitHub CLI authentication succeeds. This does not establish remote write authorization or permission to change visibility.

The owner confirmed that the supplied ZIP was extracted, committed and deleted. Preserve existing history and historical source drafts. Do not run `git init`, `gh repo create`, remote rename, blind archive overwrite or force push in this repository.

## Review before pushing

The bootstrap branch is `chore/akma-bootstrap`. Miguel owns Gradle, wrapper and manifest; Elijah reviews Android integration. Build and review locally first:

```bash
git status --short
git remote -v
gh repo view merd-labs/akma --json nameWithOwner,url,visibility,defaultBranchRef
git diff --check
git diff --cached --check
git diff --cached --stat
```

Inspect staged content for secrets, private keys, credentials, model weights, keystores and personal messages. Review `docs/BOOTSTRAP_VERIFICATION.md` for actual build/test results. No APK or model weights belong in Git.

## Approval gate

Only push after Miguel explicitly approves all three: repository **`merd-labs/akma`**, branch **`chore/akma-bootstrap`**, and existing **public visibility**. Authentication, a confirmed URL or implementation approval is not push approval.

After that approval, the operator may run:

```bash
git push -u origin chore/akma-bootstrap
```

PowerShell uses the same Git/GitHub CLI commands. Prepare a PR titled **Bootstrap Akma Android baseline**, with actual verification, placeholders and blockers. Request Elijah's Android integration review and Miguel's shared configuration review. Do not auto-merge or change repository visibility.

Public repository availability alone does not satisfy submission. Verify the actual offline demo, video, model/library licensing and AI disclosure before the October 10, 10:00 AM PHT cutoff.
