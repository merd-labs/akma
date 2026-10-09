# Akma physical-device evidence

Status: **NOT RUN**. Copy this template for each tested handset. Fill only observed values. Keep raw serials, private messages, model weights, and credentials out of this document.

## Session and provenance

- Device alias: pova2 / zero5g / camon30
- Handset physically confirmed by:
- Expected hardware (team-reported, not measurements): Pova 2 Android 11 / API 30 / Helio G85 / 6 GB / 128 GB; Zero 5G Android 11; Camon 30 Android 14.
- Operator / date / time zone:
- Elijah's exclusive-slot agreement / start / release:
- No concurrent inference or Gradle build confirmed by:
- App package / version / build Git SHA:
- APK SHA-256 (if recorded):
- Collector Git SHA / host OS / Bash or PowerShell version:
- Model source / revision / license / filename / SHA-256 / size:
- Runtime version / backend / evidence of actual backend execution:
- Context / output cap / threads / sampling settings:
- Backend status: **UNVERIFIED**. GPU acceleration requires execution evidence, not GPU hardware or API support.
- Wi-Fi off / mobile data off / other network connections / verification method:
- Model provisioning method (may require internet before offline testing):

## Hardware observations

Use reviewed TSV evidence. Do not put raw ADB output here.

| Metric | Observed value and unit | Evidence / status |
|---|---|---|
| Android API / release / ABI / model | NOT MEASURED | |
| MemTotal / MemAvailable | NOT MEASURED | KiB; system values |
| Free /data storage | NOT MEASURED | KiB; distinct from total capacity |
| Battery temperature | NOT MEASURED | deciC; not CPU/GPU temperature |
| Thermal status | NOT MEASURED | Unsupported does not mean no throttling |
| App total PSS before / after | NOT MEASURED | KiB; snapshots do not establish peak |

## Timing and repeated runs

Use a stopwatch for visible timings or cite real runtime instrumentation. Include timing boundaries and resolution. Separate app startup, model initialization, analysis, and generation. Record process-cold versus unconfirmed process state; filesystem caches remain uncontrolled.

Synthetic fixture: “Are you free Friday at 10?” Select **Reschedule**, **Professional**. Expected behavior: request an alternative without inventing availability. A fixture is input, never a prerecorded AI response. Record quality observations without copying personal chats.

| Attempt | Process/cache state | App startup ms | Model load ms | Analysis ms | First token ms | Generation ms | Output tokens / tokens per second | Result / evidence |
|---|---|---|---|---|---|---|---|---|
| Cold | NOT CONFIRMED | NOT MEASURED | NOT MEASURED | NOT MEASURED | NOT MEASURABLE | NOT MEASURED | NOT MEASURABLE | NOT RUN |
| Warm 1 | | | | | | | | NOT RUN |
| Warm 2 | | | | | | | | NOT RUN |
| Warm 3 | | | | | | | | NOT RUN |

- Timing method / exact boundaries / resolution:
- First-token and token-count source, or reason unavailable:
- Generation-total definition (include/exclude analysis and prompt processing):
- Output intent fidelity / invented facts / responsiveness:
- Crash / ANR / cancellation / timeout / memory-pressure observations per attempt:
- Heat / battery warnings / thermal status before and after / stop reason:
- Three warm successes and internal ≤30-second target: **NOT EVALUATED**. This target is not a product guarantee.
- English / Filipino / Taglish coverage: **NOT RUN** unless tested individually.

## Checks and limits

- Exact commands executed (replace serial argument with `<REDACTED>`):
- Exit codes / missing metrics / reviewed evidence paths:
- Actual local on-device inference demonstrated: **NOT VERIFIED**
- Status of app engine: implemented real runtime / model unavailable / other (explain):
- Evidence boundary: physical / emulator / synthetic tooling test. Only physical results satisfy this gate.
- Remaining gaps (Windows host, secondary handsets, peak memory, token timing, crashes, backend):
- Human reviewer / review date:
- Next owner / follow-up:

Before committing, inspect every artifact for serials, personal data, credentials, and private text. An empty field or missing measurement is never a pass.
