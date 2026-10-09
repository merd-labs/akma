# Domain contract test evidence

## Status and ownership

Implemented: 27 additional JVM unit tests against the existing Kotlin domain source. The full suite contains 51 tests. The three-action product regression is enabled and fails; this branch is not ready to merge.

Owner: Miguel, Codex Tertiary. Review requested from Miguel and Elijah. Only `app/src/test/**` and this evidence document change. No production code, contracts, Gradle files, manifests, model assets, or historical reference drafts change.

Repository: `merd-labs/akma`. Feature branch: `test/domain-contracts`. Dedicated worktree: `/home/apollo/Projects/Competitions/Year-2026/appbuildersph-hackathon-2026/akma-domain-tests`.

Source base: `02df71a`, the local `chore/akma-bootstrap` commit. Tests run against that source plus the two new test files in this branch. Authentication and repository inspection identify the active GitHub account as `CodeExplorer430`, with repository push access. No credentials are recorded here.

The delivered pack became available after planning. `COMMON_GITOPS_RULES.md`, `RUNBOOK_WORKTREES.md`, `SPEC_RECONCILIATION.md`, and the tertiary test prompt were read before edits. Reconciliation says the existing contract remains in force until an approved replacement merges. The owner explicitly selected a failing three-action product regression; existing four-action contract tests remain intact to expose the conflict.

## Commands and actual results

Baseline run before implementation, in the original bootstrap checkout:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_HOME=/home/apollo/Android/Sdk \
./gradlew --no-daemon :app:testDebugUnitTest --rerun-tasks
```

Result: exit 0, `BUILD SUCCESSFUL in 1m 11s`. XML reports: 24 tests, 0 failures, 0 errors, 0 skipped.

Full run after implementation, in the dedicated worktree, using the same command:

Result: exit 1, `BUILD FAILED in 1m 23s`. Gradle reports `51 tests completed, 1 failed`. XML reports start at `2026-10-09T11:40:45.504Z` (19:40:45 PHT).

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ReplyCoordinatorContractTest | 17 | 0 | 0 | 0 |
| ReplyCoordinatorTest | 11 | 0 | 0 | 0 |
| ReplyValidationBoundaryTest | 10 | 1 | 0 | 0 |
| ReplyValidationTest | 13 | 0 | 0 | 0 |
| Total | 51 | 1 | 0 | 0 |

The single failure is `ReplyValidationBoundaryTest.requestedThreeActionCapRejectsFourReviewedActions`:

```text
java.lang.AssertionError: Expected rejection: Too many actions.
```

It supplies four distinct IDs from the approved allowlist, with valid labels. Current `ReplyValidation.MAX_ACTIONS` is 4, so validation accepts them. Requested product behavior rejects four. The regression is not ignored or weakened. Owner reconciliation is tracked in [issue #3](https://github.com/merd-labs/akma/issues/3).

Local report: `app/build/reports/tests/testDebugUnitTest/index.html`. Counts come from `app/build/test-results/testDebugUnitTest/TEST-*.xml`; generated reports are not committed.

The build warns about SDK XML version support and the existing deprecated `SOFT_INPUT_ADJUST_RESIZE` usage in the overlay. Neither warning prevents compilation. `:app:assembleDebug` and physical device tests were not run for this unit-test-only change.

## Verified behavior

- Empty and whitespace message rejection; 1,500-character acceptance and 1,501-character rejection for input/context/instruction fields. Analysis fields, action labels, generated drafts, and editable drafts have boundary coverage.
- Each existing approved action ID reaches drafting unchanged. Unknown, blank, duplicate, case-changed, unavailable, and caller-disallowed action IDs are rejected by existing validation/coordinator tests.
- Professional, friendly, and concise enum values, original message, and selected action propagate unchanged to a recording test engine. These assertions test requests, not the style or quality of generated text.
- Uninitialized operations do not reach the engine. Model-unavailable failures during initialization, analysis, and drafting prevent copying and further unauthorized engine calls. Initialization retry is exercised.
- Duplicate initialization, analysis, and draft taps do not start extra processing. Cancellation during initialization and drafting recovers and permits retry. Existing tests cover analysis cancellation and timeout.
- A deliberately non-cooperative analysis test double finishes after cancellation. Its old result is not published, and the engine mutex prevents overlap with the retry.
- Malformed structured analysis and blank/oversized drafts fail visibly and cannot be copied. Error strings from initialization, analysis, and drafting do not expose synthetic private exception content.
- Oversized edits preserve the existing draft. A replacement message clears analysis and copyable output.

## Unimplemented and unverified capabilities

The source has no seven-category action catalog, Other fallback, language selector, JSON parser, or deterministic date/commitment validator. These are tracked in [issue #2](https://github.com/merd-labs/akma/issues/2). Tests do not invent those interfaces or assert their behavior through substitute implementations.

Filipino/Taglish selection and model language quality are unverified. Structural malformed-output tests use actual `AnalysisResult` validation, not JSON parsing. `validateDraft` checks only blankness and character length; no test claims it rejects invented dates or commitments.

All fixtures are synthetic and remain under `app/src/test`. Production uses `UnavailableReplyEngine` and returns no generated drafts. No local model inference, LLM semantic correctness, Pova 2 Android 11 behavior, secondary device behavior, or offline demo is claimed.

## Delivery dependency

At verification, remote `main` is `60580cd` and contains no Android implementation. Remote `chore/akma-bootstrap` is absent. A scoped draft PR must target the owner-published bootstrap branch, or `main` after the bootstrap merges. Creating a PR against current `main` would include unrelated bootstrap changes.

The feature branch may be pushed without force under the task authorization. PR creation remains dependent on the bootstrap owner publishing a suitable base. No other owner's branch is published by this task. No merge is performed.
