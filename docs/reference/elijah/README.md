# ContextAI — Hackathon Documentation Pack

**Kotlin / Android / Local AI · 24-hour hackathon · four developers**

## Read in order

1. [`01_PRODUCT_REQUIREMENTS.md`](01_PRODUCT_REQUIREMENTS.md) — problem, scope, users, measurable acceptance, demo promise.
2. [`02_TECHNICAL_REQUIREMENTS.md`](02_TECHNICAL_REQUIREMENTS.md) — Kotlin architecture, Android overlay boundaries, on-device model, contracts, QA.
3. [`03_24_HOUR_EXECUTION_PLAN.md`](03_24_HOUR_EXECUTION_PLAN.md) — owner assignments, hourly milestones, go/no-go gates, acceptance tests, demo script.

## Product in one sentence

A user-invoked Android overlay that uses on-device AI to understand pasted message context, propose response intentions, and draft a reply for manual review/copy.

## Critical honest scope

- User **copies and explicitly pastes** incoming context; no silent message reading.
- A bubble/overlay is feasible but application-specific behaviors **must be tested**.
- Android device runs the model; **not** a laptop server or hosted LLM for the planned MVP.
- **No auto-send** and no promise to insert into arbitrary chat fields.
- Choose exact inference SDK/model only after testing the team's physical phone.

## What to do now

At T+0 assign owners and capture device specs. At T+2 hours demand an airplane-mode local model output and visible overlay. If either fails, follow the pivot rules instead of adding features.

## Sources

- [Google LiteRT-LM for Android](https://developers.google.com/edge/litert-lm/android)
- [Google LiteRT-LM CPU/GPU Gemma sample](https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert/qualcomm/gemma3/cpu_gpu)
- [Android WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Android foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/changes)
