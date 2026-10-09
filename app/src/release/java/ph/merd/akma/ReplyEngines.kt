package ph.merd.akma

import android.content.Context
import ph.merd.akma.domain.LiteRtReplyEngine

/** Release builds always use the bundled on-device model. */
internal fun replyEngine(context: Context): ReplyEngineChoice = ReplyEngineChoice(LiteRtReplyEngine(context), demo = false)
