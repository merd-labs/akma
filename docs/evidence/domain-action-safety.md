# Domain action safety evidence

## Status

Implemented on `fix/domain-action-safety`: authoritative seven-category local catalog, maximum three displayed actions, canonical ID/label binding, safe analysis normalization, explicit provenance, and a second-confirmation API before every draft. Owner: Miguel. Reviewer: Elijah (`jairuss0`, verified repository collaborator with profile name Jairus).

Dedicated worktree: `/home/apollo/Projects/Competitions/Year-2026/appbuildersph-hackathon-2026/akma-domain-action-safety`. Verified origin: `https://github.com/merd-labs/akma.git`. Source base: `02df71ad7328e55e0762d3fde8f747ef44a04348`, `chore/akma-bootstrap`. `main` remains the documentation-only initial commit during preflight.

The owner explicitly authorizes this production contract change. Coordination notice was posted before production edits: [issue #8 comment](https://github.com/merd-labs/akma/issues/8#issuecomment-6081151090). Active contract documents fields, method behavior, exact catalog meanings, sanitization, and integration requirements. Historical source drafts remain unchanged.

## Local command and actual results

Executed in the dedicated worktree:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_HOME=/home/apollo/Android/Sdk \
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Result: exit 0, `BUILD SUCCESSFUL in 2m 30s`; 51 Gradle tasks executed. XML unit-test results:

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ActionSafetyTest | 13 | 0 | 0 | 0 |
| DraftConfirmationTest | 14 | 0 | 0 | 0 |
| ReplyCoordinatorTest | 11 | 0 | 0 | 0 |
| ReplyValidationTest | 13 | 0 | 0 | 0 |
| Total | 51 | 0 | 0 | 0 |

There are 27 new regression tests plus 24 baseline tests updated for the approved contract. The baseline four-action acceptance test now accepts three catalog actions; four actions have an explicit rejection test. The change follows the approved product requirement, rather than disabling a regression. Blank model labels no longer fail because all model labels are discarded and replaced locally.

Lint: 0 errors, 12 warnings. Warnings concern dependency updates, existing backup configuration, unused resources, missing application icon, overlay constructor tooling, URI KTX usage, and a hardcoded overlay string. None is repaired outside this task. Additional build warnings concern SDK XML support, existing deprecated overlay API usage, native library symbol stripping, and Gradle deprecations. Debug APK exists only as a local build artifact; it is not committed or published.

Reports are local: `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/tests/testDebugUnitTest/index.html`, and `app/build/reports/lint-results-debug.xml`.

## Proven behavior

- Deceptive `{id:"accept", label:"Decline"}` publishes canonical Accept; canonical Decline sends `decline`. No action selection alone calls the engine.
- Every category has exact reviewed actions/labels. Four supplied actions, unknown/duplicate/category-incompatible IDs, extended caller allowlists, unknown categories, unmarked provenance, and malformed display text fail safely.
- Only documented `invitation` alias normalizes to `interview_invitation`. Explicit Other fallback contains three deterministic local actions; it does not rescue invalid output or model failures.
- Control/format characters and bidi marks cannot reach normalized summary text. Summary content is bounded to 200 code points without splitting an emoji. Raw oversized fields fail before sanitization; unpaired surrogates and empty sanitized summaries fail.
- All tone values and the immutable original message/selected ID reach drafting unchanged after human confirmation. False raw `requiresUserDecision` cannot bypass the gate.
- Confirmation cancellation, replacement action/tone, message changes, reanalysis, initialization, invalid selection, stale confirmations, and repeated confirmation taps cannot reuse a pending request.
- Duplicate processing, timeouts, malformed drafts, unavailable models, canceled work, and a late non-cooperative draft preserve the engine mutex and prevent stale output from becoming copyable.

Test source and production source at the successful gate are identified by SHA-256 below. Later documentation-only edits do not change those tested inputs.

```text
1bb1837efe6c83e402d0a9667a7adabfec1a7b62ddc41056e9e57ed2b6f27afe  app/src/main/java/ph/merd/akma/domain/ActionCatalog.kt
476b27e16d459a010cfe28753437deb43b25650234edf34a4a74b9a4725cf7b2  app/src/main/java/ph/merd/akma/domain/LocalReplyEngine.kt
9ee30a04296d027044a0c62a22d522dca07e5cd9fe115f3aabfe95b22bfb579f  app/src/main/java/ph/merd/akma/domain/ReplyCoordinator.kt
95a4fe9f829c9d09cf68364d5787cf2b8f895cc3650420f8c589828297acc978  app/src/main/java/ph/merd/akma/domain/ReplyValidation.kt
9d5c5cc3369b1dbcb52384aee6e2d6d92453cbe3f5127005607ccaf19be81edb  app/src/main/java/ph/merd/akma/domain/UnavailableReplyEngine.kt
4256c8762c04adc7ff32cc65bc873b547e6d6c51ca79091027f54dc9f97ff976  app/src/test/java/ph/merd/akma/domain/ActionSafetyTest.kt
85181ae36801ab9830a9ab1034ebded58c596a91173c4cefeb7e36d43858d20a  app/src/test/java/ph/merd/akma/domain/DraftConfirmationTest.kt
996b7ade4e538922bdbb3622a936fdfce797978db572c0d511f0a4940454899a  app/src/test/java/ph/merd/akma/domain/ReplyCoordinatorTest.kt
736f58871544e7f0c482091a8c6c77c3b3e9cde754682ca5632daa60b6158450  app/src/test/java/ph/merd/akma/domain/ReplyValidationTest.kt
```

## Remaining dependencies and limits

Current Activity/overlay have no Confirm/Cancel controls. Their existing action clicks now stop at `pendingConfirmation`; no engine work starts. Elijah/Danielle must review the contract, display the canonical pending action and exact tone, and wire a separate human Confirm using the displayed confirmation ID. They must not immediately confirm from an action click or look up a replacement ID from a stale dialog. Do not release this as a complete drafting journey until those UI changes integrate.

Production still uses `UnavailableReplyEngine`. No runtime adapter, classifier, parser, language selector, or cloud fallback is added. Fixtures are synthetic, test-only, and exercise structural control flow. No physical Pova 2/secondary-device checks, offline inference, model language quality, or real semantic faithfulness are claimed. Structural validation does not guarantee that generated prose avoids invented dates, availability, payments, or other commitments.

Related issues: [#3](https://github.com/merd-labs/akma/issues/3) (three-action reconciliation), [#8](https://github.com/merd-labs/akma/issues/8) (label/display safety), and [#2](https://github.com/merd-labs/akma/issues/2) (remaining language/parser/semantic dependencies).

PR #6 remains unchanged during production work. After the production fix merges with human approval, update that test-only branch from the merged base without force-push, reconcile old category/label/confirmation assumptions, run the entire combined suite, and update evidence. This deferred work cannot be reported complete before that merge.

## CI inspection

Before implementation, inspected PR #6 run `37925498078`. Android failed on its deliberate three-action regression; documentation failed on trailing whitespace in preserved historical source drafts. No workflow or historical-document repair is authorized in this branch. New-branch CI results will be recorded after publication; local gates alone do not establish hosted success.
