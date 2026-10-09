package ph.merd.akma

import ph.merd.akma.domain.LocalReplyEngine

/**
 * The engine the app runs on. Each build type supplies `replyEngine(context)` (src/debug, src/release),
 * so swapping the temporary demo engine for the real model is a one-file change.
 * [demo] is true only for the debug-only DemoReplyEngine; the UI then labels everything as demo data.
 * [languageLabel] feeds the intent card's language tag; null hides the tag.
 */
data class ReplyEngineChoice(
    val engine: LocalReplyEngine,
    val demo: Boolean,
    val languageLabel: (String) -> String? = { null },
)
