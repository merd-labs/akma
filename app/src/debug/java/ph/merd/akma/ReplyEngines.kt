package ph.merd.akma

import android.content.Context
import ph.merd.akma.demo.DemoReplies
import ph.merd.akma.demo.DemoReplyEngine

/**
 * TEMPORARY: debug builds run on hardcoded demo data until the local model is ready.
 * To switch debug builds to the real model, replace this body with the one in src/release/.../ReplyEngines.kt.
 */
@Suppress("UNUSED_PARAMETER")
internal fun replyEngine(context: Context): ReplyEngineChoice =
    ReplyEngineChoice(DemoReplyEngine(), demo = true, languageLabel = DemoReplies::language)
