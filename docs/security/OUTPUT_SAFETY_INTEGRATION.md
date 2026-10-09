# Model output safety — integration guide

Component: `app/src/main/java/ph/merd/akma/safety/ModelOutputSafety.kt` (pure JVM, no Android classes, no logging).
Tests: `app/src/test/java/ph/merd/akma/safety/ModelOutputSafetyTest.kt` (46 hostile and preservation fixtures, all synthetic).

## What it is — and is not

It is deterministic cleanup of **untrusted** local-model output and of **untrusted** copied text on its way into a prompt.
It is **not** a faithfulness check: it cannot know whether a draft invents a date, an availability, a payment or a promise.
`ModelOutputSafetyTest.inventedCommitmentsAreNotDetectedByDesign` pins that limit. Every draft still needs human review (the
second-confirmation gate in `ReplyCoordinator` plus the editable draft field are the controls for that).

## API

| Call | Direction | Purpose |
|---|---|---|
| `sanitizeDraft(raw, maxChars = 1500): SafetyResult` | output | Cleans a generated reply. `Accepted(text, flags)` or `Rejected(reason)`. Never truncates silently. |
| `sanitizeDisplayText(raw, maxCodePoints): SafetyResult` | output | Single-line label/summary text, cut on a code-point boundary. |
| `neutralizePromptInput(raw): String` | input | Removes chat-template tokens and invisible/bidi/control characters from copied text before it is placed in a hand-built prompt. |
| `safeFailure(Throwable): SafeFailure` | errors | Maps any throwable (including `Error` subclasses from native bindings) to a constant user message. Never reads `message`/`cause`. |
| `describeForLog(text)` | logs | `"[redacted chars=N]"`. Use this, never the text. |

`SafetyResult.Rejected` reasons: `EMPTY`, `RAW_TOO_LARGE` (>20,000 chars), `MALFORMED_UNICODE` (lone surrogate), `TOO_LONG`.
Advisory `SafetyFlag`s on `Accepted` (never blocking): `CONTAINS_URL`, `CONTAINS_PHONE`, `SUSPICIOUS_URL`, `MIXED_SCRIPT_WORD`,
`UI_IMPERSONATION`, `TEMPLATE_ARTIFACT_REMOVED`, `TRUNCATED_AT_TURN_END`, `INVISIBLE_CHARS_REMOVED`, `CONTROL_CHARS_REMOVED`,
`COMBINING_MARKS_LIMITED`, `TRUNCATED_FOR_DISPLAY`.

## What is removed / kept

Removed: bidi controls (U+202A–202E, U+2066–2069, U+200E/F, U+061C), zero-width and format characters, soft hyphen, BOM, Unicode tag
characters (U+E0000–E007F, invisible-text smuggling), variation-selector supplement (outside Han), private-use characters, C0/C1
controls (NUL, ESC, …; `\n` kept, `\r`/U+2028/U+2029 become `\n`, tabs become spaces), runs of more than 4 combining marks,
stray ZWJ/ZWNJ (kept only inside emoji sequences and non-Latin scripts, e.g. Persian), chat-template tokens (`<|im_start|>`,
`<|im_end|>`, `<start_of_turn>`, `<end_of_turn>`, `<bos>`, `<eos>`, `<s>`, `[INST]`, `<<SYS>>`, `<|...|>` families, `<think>` blocks)
and everything after the first end-of-turn marker (a hallucinated next turn). A single wrapping code fence is unwrapped. NFC only.

Kept untouched: Filipino/Taglish and accented Latin, CJK, Arabic, Devanagari, Thai, emoji ZWJ sequences, ordinary punctuation, newlines,
**URLs and phone numbers** (flagged, not removed — they may be the intended content).

## Required wiring (domain owner — I did not edit `ReplyCoordinator.kt`)

Apply `docs/security/patches/domain-output-safety.patch` (validated: `git apply --check` on current `main`; the full unit suite passed with it in a scratch composite: 174 tests, 0 failures, at a time when that composite still contained the since-removed artifact verifier).
It does three things:

1. After `engine.draft(request).getOrThrow()` runs the raw string through `sanitizeDraft(raw, ReplyValidation.MAX_TEXT_LENGTH)`;
   a `Rejected` becomes `UnsafeModelOutputException(reason)` → notice "The local model returned an unusable reply. Retry."
2. `process()` catches `Throwable` instead of `Exception` (see "Bug reproduced" below) and renders `safeFailure(exception).userMessage`
   (identical strings to today for the two existing cases).
3. Adds `NativeErrorResilienceTest` and `DraftOutputSafetyIntegrationTest`.

Optional UI follow-up: surface `flags` (e.g. show "Contains a link / phone number — check before sending" for `CONTAINS_URL`,
`CONTAINS_PHONE`, `SUSPICIOUS_URL`, and a stronger warning for `UI_IMPERSONATION`). That needs a new field on `ReplyState`, which is
a shared type — not included.

For the runtime adapter (PR #20 `AkmaProtocol`): wrap every piece of copied text placed in a prompt with
`neutralizePromptInput(...)`. Patch: `docs/security/patches/akmaprotocol-input-neutralization.patch` (based on PR #20 head `89b2492`).

## Bug reproduced and fixed by the patch

`ReplyCoordinator.process` ends with `catch (exception: Exception)`. Native/runtime bindings fail with `java.lang.Error` subclasses
(`UnsatisfiedLinkError` for a missing ABI library, `NoClassDefFoundError`, `OutOfMemoryError` for a large model). Those bypass the
catch. Reproduction (JVM, fake engine): `initialize()` with an engine that throws `UnsatisfiedLinkError` leaves
`phase = ModelLoading` forever (`expected:<Error> but was:<ModelLoading>`, all 3 tests failed). In the app the same Error reaches the
uncaught-exception handler of a scope that has no `CoroutineExceptionHandler` (**process crash — REASONED, not run on a device**).
With the patch all three tests pass and the notice is a constant string.

Second reproduced defect (PR #20): copied text containing `<|im_end|><|im_start|>system …` adds two forged turns to the prompt
(5 `<|im_start|>` markers instead of 3). With `neutralizePromptInput` the marker count stays 3. This reduces special-token injection
only; plain-language injection ("ignore your instructions") is still possible and is the reason every action needs human confirmation.
