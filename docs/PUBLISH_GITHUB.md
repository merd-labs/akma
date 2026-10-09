# Publish Akma — reviewed operator workflow

**Organization slug is NOT known to this package.** The ZIP contains repository files; it does not create a remote repository or validate access. Do not publish into another organization or overwrite an existing working repository. A verified GitHub CLI login is required.

## Option A — brand-new repository (Ubuntu / Bash)

```bash
ORG='REPLACE_WITH_ACTUAL_ORG_SLUG'
mkdir -p "$HOME/projects/akma"
cd "$HOME/projects/akma"
# Extract the contents of Akma_MERD_Bootstrap.zip into this directory (no parent nesting).
# Before any Git operation, check files and identities:
git status --short 2>/dev/null || true
gh auth status
gh repo view "$ORG/akma"  # Should report NOT FOUND for a genuinely new repository.
# Only after verifying the repo does NOT already exist:
git init -b main
git add .
git diff --cached --stat
git diff --cached --check
git status --short
git commit -m 'chore: bootstrap Akma hackathon project'
gh repo create "$ORG/akma" --private --source=. --remote=origin --push
```

## Option B — repository already exists

Do NOT run `gh repo create`, `git init`, a force push, or blind `unzip -o` into it. Clone it normally if needed, make a short-lived branch named `chore/akma-bootstrap`, compare existing tracked files against the provided package, and migrate with review. Preserve any code already produced by registered MERD members during the competition, Git history, dependencies, config and licenses. Prompt: `prompts/08_AKMA_REPO_MIGRATION.md`.

```bash
ORG='REPLACE_WITH_ACTUAL_ORG_SLUG'
gh repo view "$ORG/akma" --json name,url,visibility
# If the remote uses another name (e.g. old working-name repo), do not rename it blindly.
# Instead confirm migration/rename with Miguel and update only approved metadata.
```

## Windows 11 PowerShell

Use Git Bash to execute Option A or use equivalent PowerShell commands. After extracting into a NEW empty directory:

```powershell
$Org = 'REPLACE_WITH_ACTUAL_ORG_SLUG'
gh auth status
gh repo view "$Org/akma"    # Expect not found ONLY if creating new repo
# After reviewing the extracted content and confirming no preexisting repository:
git init -b main
git add .
git diff --cached --stat
git diff --cached --check
git commit -m 'chore: bootstrap Akma hackathon project'
gh repo create "$Org/akma" --private --source . --remote origin --push
```

## Required verification

```bash
git remote -v
git status --short
gh repo view "$ORG/akma" --json name,visibility,url
```

Private initially is optional; **public GitHub repository is mandatory before the October 10, 10:00 AM PHT cutoff**. After a secret/privacy review, the owner may intentionally change visibility in the GitHub UI and confirm it with `gh repo view`. Do not change visibility automatically. Check working demo/video/social post and AI disclosures before final submission.
