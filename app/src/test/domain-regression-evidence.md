# Domain lifecycle and regression evidence

Date: October 9, 2026. Branch: `fix/domain-regression-stability`.
Stacked base: PR #14, `10f7dc4799b1cbb527f7f0a4484fbd2239c60c29`.
Reconciled test source: PR #6, `f79102537af7d01220b062f1d7f00ddbe9fe2271`.
Neither existing PR branch was changed. Shared coordinator ownership and recovery behavior were announced in [issue #8](https://github.com/merd-labs/akma/issues/8#issuecomment-6084064833) before production edits.

## Reproduced defect and fix

On PR #14's coordinator, restarting analysis retained the previous analysis and copied draft while processing. Cancellation recovered the previous phase/output. Reinitialization similarly retained output and prior readiness despite an interrupted initialization.

Before the production change, this command exited 1 (`BUILD FAILED in 1m 40s`):

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk ./gradlew --no-daemon :app:testDebugUnitTest --tests 'ph.merd.akma.domain.ReplyLifecycleSecurityTest.reanalysisClearsCopiedOutputImmediatelyAndCancellationReturnsReady'
```

JUnit recorded 1 test, 1 failure, 0 errors/skips at `2026-10-09T15:40:27.630Z`: `java.lang.AssertionError` because the previous analysis remained present. This same assertion passes after the fix.

`ReplyCoordinator.process` now immediately clears analysis, draft, and pending confirmation for initialization/reanalysis. Reanalysis cancellation/error recovery returns to Ready with no old output. Initialization invalidates readiness before work and recovers to ModelUnavailable until initialization succeeds. Draft failure/cancellation still recovers to ChoosingAction with valid current analysis and requires fresh human confirmation. Public signatures and the existing action catalog/validation contract are unchanged.

## PR #6 reconciliation

All 27 test methods from its two added classes were retained and adapted to PR #14's reviewed contract. Valid synthetic fixtures declare deterministic provenance and canonical categories; a separate explicit confirmation starts drafting. Caller allowlists narrow catalog membership. Model-provided blank/oversized labels are discarded and checked against canonical labels; category/summary length and blank failures remain assertions. The four-action test uses four valid interview IDs so its failure cannot be masked by an unrelated unsupported-ID error. Malformed cases assert the specific intended validation error. All catalog actions are exercised within their seven categories; all three tones propagate unchanged.

The original malformed-action fixture in `ReplyCoordinatorTest` now declares provenance so it exercises its invalid ID rather than failing earlier on unspecified source. No ignored tests or hidden expected failures were added.

## Full local gate

Command run in the isolated worktree:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Observed exit 0, `BUILD SUCCESSFUL in 2m 53s`, 51 Gradle tasks (34 executed, 17 up-to-date). JUnit XML under `app/build/test-results/testDebugUnitTest/` reports:

| Suite | Tests | Failures/errors/skips |
| --- | ---: | --- |
| ActionSafetyTest | 13 | 0/0/0 |
| DraftConfirmationTest | 14 | 0/0/0 |
| ReplyCoordinatorContractTest | 17 | 0/0/0 |
| ReplyCoordinatorTest | 11 | 0/0/0 |
| ReplyLifecycleSecurityTest | 13 | 0/0/0 |
| ReplyValidationBoundaryTest | 10 | 0/0/0 |
| ReplyValidationTest | 13 | 0/0/0 |
| **Total** | **91** | **0/0/0** |

Debug APK assembly passed. Lint XML reports **0 errors, 13 warnings**, located in unchanged Gradle, manifest, resource, Activity, and overlay files (dependency updates, backup configuration, unused resources, icon, view constructor, KTX, translation). Gradle also reports deprecated features for a future Gradle 9 upgrade; no build configuration changes were made.

The 13 new lifecycle/security methods cover immediate clearing, initialization readiness, timeout/error recovery, exception redaction, serialized retries after non-cooperative operations, suppression of late canceled failures, summary status/instruction spoofing, mutable engine-list isolation, immutable confirmation requests, repeated confirmation, and rejected busy message changes. Existing confirmation tests cover replaced/canceled/stale tokens and changed message/tone/action. Existing action-safety tests cover canonical labels, three-action cap, Other, unsupported IDs/categories, and Unicode sanitation.

## Limits and integration blockers

- All engines/messages are synthetic test fixtures. Real model inference, Android device behavior, Filipino/Taglish selection, and Pova 2 performance are **NOT TESTED** here.
- A delayed non-cooperative operation is simulated with `NonCancellable`. Mutex/generation tests establish serialization and late-result suppression, not forced interruption of native code. Timeout errors may publish only after such work returns; there is no hard native abort guarantee.
- Canonical IDs/labels and explicit confirmation constrain coordinator behavior. They do **not** establish semantic factual faithfulness or prove generated prose contains no invented dates, availability, payments, commitments, or names. Sanitized summaries can still contain false prose, explicitly marked untrusted.
- PR #14's UI confirmation controls remain a separate integration blocker. This branch adds no UI/runtime/parser/language-detection implementation. Issue #2 retains those remaining obligations; do not close it based on these tests.
- Hosted CI is checked after push. The inherited shallow documentation checkout has an existing failure tracked in issue #15; PR #13 fixes that independently. No workflow, historical document, or other owner's branch is edited here.

Recommended team integration order: CI repair #13, domain safety #14, this stacked regression PR, then bootstrap #4 after integrated checks pass. The team can retire duplicate PR #6 after confirming these reconciled tests. No merge, reviewer assignment, or force push is performed by this agent.
