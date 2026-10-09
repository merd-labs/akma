# Domain native resilience evidence

## Source and ownership

This change starts from Primary's published `integration/offline-mvp` commit
`53b087c535f441815f600fd9ced816b4cdc54a39` (PR #27). That source combines the domain/action
contract, overlay, output safety, explicit confirmation UI, verified bundled provisioning,
and LiteRT-LM 0.18.0 adapter. The isolated branch is `fix/domain-native-resilience`.

Primary coordinated the optional runtime recovery seam in
[the ownership handoff](https://github.com/merd-labs/akma/pull/26#issuecomment-6086888339).
Existing `LocalReplyEngine`, `ReplyState`, shared model signatures, `docs/CONTRACT.md`,
Gradle, manifest, UI, assets, and provisioning internals are unchanged. The adapter retains
one authoritative bundled provisioner, prompt-input neutralization, deterministic category
disclosure, and canonical catalog actions.

## Reproduction before production changes

On immutable main `3edd10f02d5e22638c2d8d1f81dd132f84416dbc`, the isolated native reproducer
ran:

```sh
./gradlew --no-daemon :app:testDebugUnitTest --tests 'ph.merd.akma.domain.NativeFailureRecoveryTest'
```

Observed **FAIL: 3 tests, 3 failures, zero errors/skips**. Synthetic thrown
`UnsatisfiedLinkError`, `NoClassDefFoundError`, and `OutOfMemoryError` each left
`ModelLoading`. The reproducer recorded the escaped errors in its supervisor scope,
so the failure was an observed coordinator state assertion rather than an unrelated
uncaught-test-harness failure. Local reproducer commit: `56badc8a57897bec1da48218b4770251e7f34306`.
[Published reproduction handoff](https://github.com/merd-labs/akma/pull/26#issuecomment-6086857745).

The integrated security patch already maps these errors to an Error phase. Its broad
Throwable catch does not establish actual runtime recovery. This change preserves that
Error phase and requires unavailable readiness after recovery, actual native invalidation,
and successful fresh initialization before reuse.

## Implemented behavior

- Expected linkage and memory failures keep their original type and use constant notices.
  Ordinary exceptions are redacted. Cancellation propagates; unrelated fatal Errors reach
  the owning scope rather than becoming friendly errors.
- Native handles detach before close. Failed or uncertain cleanup quarantines the runtime,
  blocks initialization/reuse, and requests process restart. Force stop and reopen Akma
  when process restart is required; reopening only the overlay does not reset the runtime.
- UI timeout waiting and the native worker are sibling jobs. The UI stops loading at its
  deadline while the worker retains serialization through native completion and cleanup.
  Queued cancellation never invokes inference. Reset never closes an active native call.
- The SDK callback bridge explicitly invokes `cancelProcess` on cancellation and waits for
  its terminal callback before closing the conversation. Failed cancellation immediately
  marks ownership uncertain. Late callbacks and duplicate terminal events cannot publish
  old output. The bridge bounds accumulated raw output without silently truncating it.
- A canceled operation cannot restore its old message, analysis, draft, or confirmation.
  A late native fault can invalidate the shared engine; its safe health notice preserves
  the current message and never publishes the old result.
- Immutable action/tone/source confirmations and one-time tokens are preserved. Canonical
  labels, seven categories including Other, three displayed actions, and stale-request
  rejection remain covered by the existing suite.
- Model output crosses `ModelOutputSafety` before Editing/copyability. Both raw engine draft
  and cleaned draft obey the 1,500 UTF-16-character limit. Malformed Unicode and unusable
  output fail without copyable text. User edits remain explicit manual edits.

The [upstream SDK callback/Flow implementation](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
provides the cancellation API; callback lifecycle tests do not prove JNI termination.

## Checks and actual results

Host: **Ubuntu 24.04.5 LTS**, **OpenJDK 17.0.20.1**, checked-in Gradle Wrapper.
JDK 17 and the installed Android SDK were selected through environment variables.

The first focused domain/overlay/UI command completed **169 tests with 2 failures**.
During compilation, the host disk filled; Kotlin's daemon failed with ENOSPC and its
fallback compiler completed. The two failing new assertions compared Throwable identity
across coroutine suspension. They now require exactly one propagated error, the exact
original type, and the original throwable directly or as its preserved cause. Cancellation
waiting and no-premature-close assertions remain. This follows
[coroutine stack recovery](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/internal/StackTraceRecovery.kt),
which may copy an exception. No existing valid assertion was removed or suppressed.

The first complete checkpoint `ae1e7c141a56fedd77f88ec70e3a0c10913d900d` passed 259 tests,
assembly, and lint. A final focused startup-cancellation reproducer then ran:

```sh
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests 'ph.merd.akma.domain.NativeReplyOperationTest.cancellationBeforeStartupNeverInvokesNativeGeneration'
```

Observed **FAIL: 1 test, 1 failure**. The production callback bridge invoked startup once
with an already-canceled context. Cancellation checkpoints now run before native preparation,
initialization, and callback startup. A conversation whose generation never started is closed
safely without quarantining the warm runtime. Completed UI waits also release coordinator-held
flight/result references instead of retaining a prior session through the deferred result.
No claim of native memory zeroization follows from this reference cleanup.

Final complete gate:

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Observed **PASS**, exit 0, `BUILD SUCCESSFUL in 4m 12s`:

- **260 tests**, **0 failures**, **0 errors**, **0 skipped**, from 22 JUnit XML suites.
- Domain: 130 tests; overlay: 22; UI: 19. These 171 tests are a subset of the complete run,
  not an additional standalone passing run.
- Four new regression suites add 32 tests. Existing native/output security regressions
  remain; the non-cooperative timeout regression now additionally requires Error and
  non-busy state before native completion.
- `assembleDebug`: PASS, **model-free debug APK**. No model weights are in this worktree
  or this commit. This APK is not an offline inference or submission artifact.
- `lintDebug`: PASS, **0 errors, 0 fatal issues, 12 warnings**.

Actual merged-manifest gate:

```sh
java scripts/security/ManifestGuard.java --policy scripts/security/manifest-policy.txt \
  app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
```

Observed **PASS** using JDK 17. The guard explicitly warns that this APK is debuggable;
final submission requires the owner's non-debuggable artifact gate. Whitespace and
committed-range secret checks are recorded in the PR handoff. Hosted checks must be
verified against the pushed dependent SHA; Primary's base CI is not this branch's CI.

## Remaining limitations and integration

**NOT TESTED for this change:** physical Pova 2/LE7/API 30, Camon 30, Infinix Zero 5G,
emulator, Windows 11, real native OOM/linkage faults, and model-enabled device inference.
No ADB operations were issued during the other owner's exclusive device slot. Earlier
APK or device evidence from other source commits does not validate this new callback path.

Synthetic engines, errors, callbacks, and small artifact fixtures test domain/lifecycle
behavior only. They are never production inference. They cannot establish model semantic
faithfulness, factual correctness, language quality, generation latency, real memory
pressure, or cancellation behavior inside JNI. A terminal callback that never arrives
keeps native work serialized; the UI times out, but the process may require force stop.
Native abort/SIGSEGV and process-wide memory exhaustion are not catchable recovery promises.

Recommended order: review/integrate this focused dependency into PR #27, rebuild the
model-enabled candidate, record the new source/APK SHA, then repeat the coordinated Pova 2
radios-off journey and cancellation/session-clear/retry checks. Confirm Reschedule/tone/source
before generation, review the actual generated text, edit/copy manually, and verify a second
request succeeds after cancellation. Run combined tests/build/lint/manifest and final artifact
checks again if Primary changes the integration source. Miguel/MERD owns final review and merge;
this branch does not merge or assign reviewers.
