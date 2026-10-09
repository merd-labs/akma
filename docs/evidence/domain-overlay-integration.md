# Domain and overlay integration test evidence

Date: October 10, 2026 (PHT). Branch: `test/domain-overlay-integration`.

## Source provenance and diagnosis

The original failure was reproduced in an isolated, clean diagnostic worktree at reviewer composite `409ad98879a405d349379f8889e7d35809b02ac0`. This commit contains domain PR #14 `10f7dc4799b1cbb527f7f0a4484fbd2239c60c29` and overlay PR #12 `8405ed8b14183d864bc007a67afca481bd2da53c`. The reviewer's untracked adversarial probe was not copied, changed, or included.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk ./gradlew --no-daemon :app:testDebugUnitTest --tests 'ph.merd.akma.overlay.OverlayDismissalTest'
```

Observed exit **1**. JUnit XML: **4 tests, 1 failure, 0 errors/skips**, timestamp `2026-10-09T16:18:50.658Z`. The failed method is `dismissalClearsCopiedContentButKeepsInitializedModel`; its `assertTrue(canCopy)` fails at `OverlayDismissalTest.kt:30` (`java.lang.AssertionError`). The reviewer's separate 67-test run was not repeated here and is not counted as this agent's result.

The test fixture contradicts the approved domain contract, rather than exposing incorrect catalog mapping:

- `AnalysisResult.source` defaults to UNSPECIFIED, so normalization fails before publishing analysis.
- `Other` is not canonical `other`; `clarify` belongs to interview invitations, not Other. Other uses `ask_to_clarify` with label `Ask to clarify`.
- An action selection only stages confirmation. Without `confirmDraft` on the captured pending token, an editable/copyable draft cannot exist.

Primary repaired the fixture in `f611ddb94d11158dcbc251c1dc5523f0b6bc83ab`, preserving the original dismissal assertions. This agent waited for publication and did not duplicate or overwrite that change. See [Primary's repair evidence](https://github.com/merd-labs/akma/pull/12#issuecomment-6085165551).

The implementation worktree starts at published combined base `c2858acddb68630e6a62c732f998206e2f9c8748`. Git ancestry checks verified the original #14/#12 heads, #17 `9f7112d0a5a6afa9a3308ec822f1bb59d43ac117`, and accepted domain main `c0d2ec80914fa276a70ab5afcd79dfb2dd702a29`. PR #12 subsequently merged as `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`; main contains the tested base. No other owner's branch was merged by this agent.

## Focused and full validation

The repaired original tests and six new integration tests were run together:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk ./gradlew --no-daemon :app:testDebugUnitTest --tests 'ph.merd.akma.overlay.OverlayDismissalTest' --tests 'ph.merd.akma.overlay.OverlayDomainIntegrationTest'
```

Observed exit **0**, `BUILD SUCCESSFUL in 3m 45s`, 22 tasks executed. JUnit XML: **10 tests, 0 failures/errors/skips** (4 original and 6 new).

The complete gate then ran:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/apollo/Android/Sdk ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Observed exit **0**, `BUILD SUCCESSFUL in 4m 30s`, 51 tasks (30 executed, 21 up-to-date). XML results under `app/build/test-results/testDebugUnitTest/`:

| Suite | Tests | Failures/errors/skips |
| --- | ---: | --- |
| ActionSafetyTest | 13 | 0/0/0 |
| DraftConfirmationTest | 14 | 0/0/0 |
| ReplyCoordinatorContractTest | 17 | 0/0/0 |
| ReplyCoordinatorTest | 11 | 0/0/0 |
| ReplyLifecycleSecurityTest | 13 | 0/0/0 |
| ReplyValidationBoundaryTest | 10 | 0/0/0 |
| ReplyValidationTest | 13 | 0/0/0 |
| OverlayDismissalTest | 4 | 0/0/0 |
| OverlayDomainIntegrationTest | 6 | 0/0/0 |
| OverlaySessionTest | 12 | 0/0/0 |
| **Total** | **113** | **0/0/0** |

Debug assembly passed. Lint XML reports **0 errors, 17 warnings**, all located in unchanged Gradle, manifest, resources, Activity, and overlay source. Categories: dependency/version updates, backup rules, unused resources, application icon, view constructor, KTX, and translation. No warning is located in the new test file. Gradle also warns about future Gradle 9 incompatibility; SDK tools report an XML-version warning, and the existing overlay uses deprecated `SOFT_INPUT_ADJUST_RESIZE`. None was hidden or fixed outside scope.

## Added regression coverage

`OverlayDomainIntegrationTest` exercises the production `ReplyCoordinator` and `clearOverlayReplySession` helper with synthetic engines:

1. Canceled/dismissed pending tokens cannot invoke drafting in a replacement session; a fresh token binds the replacement message, canonical action, and tone.
2. Misleading Accept/Decline labels normalize to canonical labels; duplicate selection/confirmation invokes the engine once; repeated dismissal clears copied output.
3. A non-cooperative draft finishing after dismissal cannot restore output; replacement analysis waits for engine serialization and drafts with the new source message.
4. Dismissal after initialization failure clears content, keeps ModelUnavailable, redacts exception text, and permits a successful initialization retry.
5. Model-unavailable analysis followed by dismissal cannot pretend readiness or invoke more processing until reinitialized.
6. Dismissal during reinitialization rejects late readiness and the old pending token; readiness requires a fresh successful initialization.

The full suite also validates three displayed actions, all seven categories/labels, summary sanitation, unsupported/duplicate IDs, malformed output, immutable confirmation requests, timeouts, cancellation, and state clearing. No tests were ignored, deleted, or weakened. Existing production types/catalog/coordinator, Gradle, manifest, UI, runtime, and historical drafts are unchanged by this branch.

## Limits and hosted checks

These are JVM orchestration tests, not Android window/input/device tests or actual model inference. The non-cooperative operation is simulated with `NonCancellable`; it proves serialization and late-result suppression, not forced native interruption. Synthetic LOCAL_MODEL provenance in the adversarial-label fixture tests the display boundary and does not claim genuine classification.

Structural tests do not guarantee factual/semantic faithfulness or absence of invented dates, availability, payments, or commitments. Real offline inference, phone performance, and the separate Confirm/Cancel UI bridge remain **NOT TESTED** here. No production Kotlin change required coordination with Primary; Primary's accepted fixture repair is inherited intact.

Hosted CI is inspected after push; the associated PR records run URLs and observed outcomes separately from these local results. Miguel/team retain review and merge control. No reviewer assignment, force push, or merge is performed by this agent.
