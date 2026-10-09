# Start here — decisions and status

This is the **working project bootstrap**, not an implemented app. See `docs/reference/elijah/` for original unedited planning drafts. Those drafts use a 24-hour schedule and list hardware as TBD; THIS directory supersedes them for execution.

**Official product name:** Akma. **Repo slug:** `akma`. **Android ID:** `ph.merd.akma`. Original drafts in `docs/reference/elijah/` retain the earlier working name ContextAI intentionally. See `docs/BRAND_AND_PROVENANCE.md`.

**Locked for MVP:** Android native Kotlin; Jetpack Compose for Activity; Android Views hosted by `WindowManager` for overlay; explicit user paste; manual copy/send; no backend; no external AI APIs. One user-controlled core workflow.

**Confirmed from MERD:** Tecno Pova 2 LE7, Android 11 / API 30, 6 GB physical RAM, 128 GB storage capacity. **Provisional:** actual free storage/RAM, exact minSdk pending chosen dependency (must support API 30), target/compile SDK, Gradle/Kotlin/Compose/JDK versions, LiteRT-LM/Gemma versus llama.cpp/GGUF, generation throughput, memory and thermals. The current upstream llama.cpp Android Studio example requires API 33; do not copy its Gradle minSdk unchanged.

**Do not claim:** universal overlay in secure/system apps, auto-read from Viber/Messenger, zero taps, iOS support, working keyboard, 4GB compatibility, or functional offline inference until observed.

**Go/no-go:** Real quantized model executes on Pova 2 with Wi-Fi/mobile data off; generated reply preserves intended action; app remains responsive and recoverable. If unavailable, test smaller GGUF CPU option or simplify app transparently. No fake local inference.

**Repo:** Android build files are intentionally left for bootstrap lead agent because required SDK/runtime versions must first be audited and fixed. This avoids untested version combinations and counterfeit CI passes.

**Official rename/bootstrap agent prompt:** [`prompts/08_AKMA_REPO_MIGRATION.md`](../prompts/08_AKMA_REPO_MIGRATION.md). Use it before building only when migrating an existing repo; for a new repo, use [`prompts/00_BOOTSTRAP_LEAD.md`](../prompts/00_BOOTSTRAP_LEAD.md) after importing this package.
