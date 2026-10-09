# Spec Kit and Matt Pocock Skills — minimal workflow

**Important:** Spec Kit and Matt Pocock's skills solve overlapping process problems. During a 19.5-hour hackathon, use Spec Kit for **one concise constitution/spec and task checklist**; Matt Pocock skills for targeted clarification, TDD/review, not a full parallel planning bureaucracy.

Official references:
- https://github.com/github/spec-kit
- https://github.github.com/spec-kit/installation.html
- https://github.github.com/spec-kit/reference/integrations.html
- https://github.com/mattpocock/skills

## Specify CLI

Owner: Miguel. Setup is deferred until the Android baseline and contract merge. Local audit finds Specify 1.1.1; `specify init --help` supports `--here`, `--integration`, `--script py`, `--force` and `--non-interactive`. No integration was initialized during bootstrap. Review installed help again before setup; `--force` can overwrite files.

Check already-installed versions. Install from `uv tool install specify-cli` if needed. In this nonempty repo, back up/commit first and review generated changes:

```text
specify init --here --integration codex --script py --force
```

Current Spec Kit supports `codex`, `claude`, and `agy` (Antigravity) integrations, but multi-install may require explicit `--force`. Assign **one human owner** to install and configure, not 4 agents editing `.specify` concurrently. **Do not run Speckit init from all four workstations independently**; teammates should just `git pull` the committed result.

Typical instructions for the *agent*, after setup (use correct agent's command syntax): constitution → specify → clarify only blocking ambiguity → plan → tasks → implement. Keep the entire feature limited to one vertical slice; do not use 5 cycles of requirements interviews during the remaining hours.

## Matt Pocock Skills

The maintainer currently documents a managed Codex plugin and a Claude Code plugin, plus editable `npx skills@latest add mattpocock/skills` installation. Confirm versions before executing. Inspect current vendor docs and select ONE route per agent. Install only the skills actually needed. Avoid duplicate plugin AND copied skills for the same harness. Run `/setup-matt-pocock-skills` once per repo when installed, select GitHub Issues and docs under `docs/`, commit reviewed output.

Recommended: install only a small relevant set (e.g. setup, review, TDD) and inspect their actual names in your installed version rather than assuming skill names. No extra project-management framework. Do not overwrite PRD/architecture or task owners without agreement.

**Authority:** `docs/PRD.md`, `docs/CONTRACT.md`, `docs/DECISIONS.md` are MERD-approved; `docs/reference/elijah/*` are unedited source drafts. Agent generated files must not silently supersede these.

## Verified upstream installation forms (check installed CLI help first)

Spec Kit, after committing/backing up this nonempty repository:

```text
specify init --here --integration codex --script py --force
specify integration install claude --script py
```

Only install extra integrations if the current CLI declares them compatible; inspect the generated diff. For Matt Pocock's skills choose ONE installation mechanism per agent, not both:

```text
codex plugin marketplace add mattpocock/skills
codex plugin add mattpocock-skills@mattpocock
# Or, for editable copies: npx skills@latest add mattpocock/skills
```

For Claude, the maintainer currently documents `claude plugin install mattpocock-skills@claude-plugins-official`. Run the setup skill once per repo if installed, choosing GitHub Issues and `docs/`. Installing Spec Kit/skills is lower priority than producing the first compiling Android APK and proving real offline inference.

References: https://github.github.com/spec-kit/reference/core.html ; https://github.github.com/spec-kit/reference/integrations.html ; https://github.com/mattpocock/skills
