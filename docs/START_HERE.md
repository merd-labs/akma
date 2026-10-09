# Start here — decisions and status

This is the **Android baseline**, with a Compose Activity, user-started Views overlay panel, shared validation and reply coordinator. Paste/copy and permission recovery are implemented but unverified on a phone. The production engine always reports model unavailable; local inference remains planned. See `docs/BOOTSTRAP_VERIFICATION.md` for actual checks and `docs/reference/elijah/` for original unedited planning drafts. Active documents supersede those drafts for execution.

**Official product name:** Akma. **Repo:** `merd-labs/akma` (verified public). **Android ID/namespace:** `ph.merd.akma`. Original drafts retain historical identity intentionally; see `docs/BRAND_AND_PROVENANCE.md`.

**Locked for MVP:** Android native Kotlin; Jetpack Compose for Activity; Android Views hosted by `WindowManager` for overlay; explicit user paste; manual copy/send; no backend; no external AI APIs. One user-controlled core workflow.

**Confirmed from MERD:** Tecno Pova 2 LE7, Android 11 / API 30, 6 GB physical RAM, 128 GB storage capacity. **Baseline pins:** minSdk 30, compile/target SDK 36, Build Tools 35.0.0, JDK 17, Gradle 8.14, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, Compose BOM 2025.10.00. **Unverified:** available RAM/storage, phone installation, runtime/model compatibility, generation throughput, memory and thermals. Runtime dependencies must preserve API 30 support. Do not copy the API 33 llama.cpp Android example unchanged.

**Do not claim:** universal overlay in secure/system apps, auto-read from Viber/Messenger, zero taps, iOS support, working keyboard, 4GB compatibility, or functional offline inference until observed.

**Go/no-go:** Real quantized model executes on Pova 2 with Wi-Fi/mobile data off; generated reply preserves intended action; app remains responsive and recoverable. If unavailable, test smaller GGUF CPU option or simplify app transparently. No fake local inference.

**Repo:** Baseline work is isolated on `chore/akma-bootstrap`. Miguel owns shared configuration; Elijah reviews integration. No remote writes occur without Miguel approving repository, branch and visibility. Spec Kit and Skills setup remains deferred until the baseline merges.

**Bootstrap prompts:** [`prompts/08_AKMA_REPO_MIGRATION.md`](../prompts/08_AKMA_REPO_MIGRATION.md) and [`prompts/00_BOOTSTRAP_LEAD.md`](../prompts/00_BOOTSTRAP_LEAD.md). Inspect existing code first; do not regenerate or overwrite this baseline.
