package ph.merd.akma.safety

import java.text.Normalizer
import java.util.concurrent.CancellationException

/**
 * Advisory properties of an accepted output. Flags never block a draft; the UI decides how to warn.
 * They are heuristics, not proof: absence of a flag does not make text safe or truthful.
 */
enum class SafetyFlag {
    CONTAINS_URL,
    CONTAINS_PHONE,
    SUSPICIOUS_URL,
    MIXED_SCRIPT_WORD,
    UI_IMPERSONATION,
    TEMPLATE_ARTIFACT_REMOVED,
    TRUNCATED_AT_TURN_END,
    INVISIBLE_CHARS_REMOVED,
    CONTROL_CHARS_REMOVED,
    COMBINING_MARKS_LIMITED,
    TRUNCATED_FOR_DISPLAY,
}

enum class SafetyRejection {
    /** Nothing displayable is left after cleaning (blank, zero-width only, reasoning-only, control-only). */
    EMPTY,

    /** Raw engine output exceeded [ModelOutputSafety.MAX_RAW_CHARS]; it is dropped without processing. */
    RAW_TOO_LARGE,

    /** Unpaired UTF-16 surrogate: the engine produced malformed text (often a split multi-byte token). */
    MALFORMED_UNICODE,

    /** Cleaned draft is longer than the caller's limit. Drafts are never silently truncated. */
    TOO_LONG,
}

sealed interface SafetyResult {
    data class Accepted(val text: String, val flags: Set<SafetyFlag>) : SafetyResult
    data class Rejected(val reason: SafetyRejection) : SafetyResult
}

/**
 * Fixed, user-presentable failure classes. [userMessage] is a constant: it never contains exception text,
 * which can embed prompts, file paths or native diagnostics.
 */
enum class SafeFailure(val userMessage: String) {
    TIMEOUT("Local processing timed out. Retry or cancel."),
    CANCELLED("Cancelled."),
    OUT_OF_MEMORY("The phone ran out of memory while running the local model. Close other apps and retry."),
    RUNTIME_UNAVAILABLE("The local AI runtime is not available on this device."),
    MODEL_UNAVAILABLE("No local model is configured."),
    GENERIC("Local processing failed. Check the model and retry."),
}

/**
 * Deterministic cleanup of untrusted local-model output before it is shown, edited or copied.
 *
 * What it does: removes invisible/bidirectional/control characters, rejects malformed Unicode, strips chat-template
 * tokens and hallucinated follow-on turns, bounds size, and attaches advisory flags (links, numbers, UI imitation).
 *
 * What it does NOT do: it cannot tell whether a draft is true, faithful to the user's intent, or free of invented
 * dates, availability, payments or commitments. The user must still read and edit every draft. URLs and phone
 * numbers are preserved on purpose (they can be the intended content); they are only flagged.
 *
 * Pure JVM code: no Android classes, no logging, no I/O. Safe to call from any thread.
 */
object ModelOutputSafety {
    const val MAX_RAW_CHARS = 20_000
    const val DEFAULT_MAX_DRAFT_CHARS = 1_500
    private const val MAX_COMBINING_RUN = 4

    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D

    fun sanitizeDraft(raw: String, maxChars: Int = DEFAULT_MAX_DRAFT_CHARS): SafetyResult {
        require(maxChars > 0) { "maxChars must be positive." }
        val cleaned = clean(raw, allowNewlines = true)
        if (cleaned is Cleaned.Failed) return SafetyResult.Rejected(cleaned.reason)
        cleaned as Cleaned.Ok
        val flags = cleaned.flags
        val templated = stripTemplate(cleaned.text, flags)
        val text = unwrapFence(templated).trim().collapseBlankLines()
        if (text.isBlank()) return SafetyResult.Rejected(SafetyRejection.EMPTY)
        if (text.length > maxChars) return SafetyResult.Rejected(SafetyRejection.TOO_LONG)
        flags += advisoryFlags(text)
        return SafetyResult.Accepted(text, flags)
    }

    /**
     * Single-line plain text for labels/summaries. Newlines become spaces and the result is cut at a code-point
     * boundary (never splitting a surrogate pair) with [SafetyFlag.TRUNCATED_FOR_DISPLAY] set when shortened.
     */
    fun sanitizeDisplayText(raw: String, maxCodePoints: Int): SafetyResult {
        require(maxCodePoints > 0) { "maxCodePoints must be positive." }
        val cleaned = clean(raw, allowNewlines = false)
        if (cleaned is Cleaned.Failed) return SafetyResult.Rejected(cleaned.reason)
        cleaned as Cleaned.Ok
        val flags = cleaned.flags
        var text = stripTemplate(cleaned.text, flags).replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return SafetyResult.Rejected(SafetyRejection.EMPTY)
        if (text.codePointCount(0, text.length) > maxCodePoints) {
            text = text.substring(0, text.offsetByCodePoints(0, maxCodePoints)).trimEnd()
            flags += SafetyFlag.TRUNCATED_FOR_DISPLAY
        }
        flags += advisoryFlags(text)
        return SafetyResult.Accepted(text, flags)
    }

    /**
     * Maps any throwable (including native-binding errors) to a constant class. Never reads `message` or `cause`,
     * so private prompts, file paths and native diagnostics cannot reach the UI or logs through this path.
     */
    fun safeFailure(error: Throwable): SafeFailure = when {
        error is kotlinx.coroutines.TimeoutCancellationException -> SafeFailure.TIMEOUT
        error is CancellationException -> SafeFailure.CANCELLED
        error is OutOfMemoryError -> SafeFailure.OUT_OF_MEMORY
        error is LinkageError -> SafeFailure.RUNTIME_UNAVAILABLE // UnsatisfiedLinkError, NoClassDefFoundError, ...
        error.javaClass.simpleName == "ModelUnavailableException" -> SafeFailure.MODEL_UNAVAILABLE
        else -> SafeFailure.GENERIC
    }

    /** Content-free description for diagnostics: length only. Use instead of logging text. */
    fun describeForLog(text: String): String = "[redacted chars=${text.length}]"

    // ---- cleaning ---------------------------------------------------------------------------------------------

    private sealed interface Cleaned {
        class Ok(val text: String, val flags: MutableSet<SafetyFlag>) : Cleaned
        class Failed(val reason: SafetyRejection) : Cleaned
    }

    private fun clean(raw: String, allowNewlines: Boolean): Cleaned {
        if (raw.length > MAX_RAW_CHARS) return Cleaned.Failed(SafetyRejection.RAW_TOO_LARGE)
        val points = IntArray(raw.codePointCount(0, raw.length))
        var count = 0
        var index = 0
        while (index < raw.length) {
            val unit = raw[index]
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= raw.length || !Character.isLowSurrogate(raw[index + 1])) {
                    return Cleaned.Failed(SafetyRejection.MALFORMED_UNICODE)
                }
            } else if (Character.isLowSurrogate(unit)) {
                return Cleaned.Failed(SafetyRejection.MALFORMED_UNICODE)
            }
            val point = raw.codePointAt(index)
            points[count++] = point
            index += Character.charCount(point)
        }

        val flags = linkedSetOf<SafetyFlag>()
        val out = StringBuilder(raw.length)
        var lastEmitted = -1
        var i = 0
        while (i < count) {
            val point = points[i]
            val type = Character.getType(point)
            when {
                point == '\n'.code || point == '\r'.code || point == 0x85 || point == 0x2028 || point == 0x2029 -> {
                    if (point == '\r'.code && i + 1 < count && points[i + 1] == '\n'.code) {
                        // CRLF: let the LF produce the single newline.
                    } else {
                        emitNewline(out, allowNewlines)
                        lastEmitted = ' '.code
                    }
                }
                point == '\t'.code -> { out.append(' '); lastEmitted = ' '.code }
                point == ZWJ || point == ZWNJ -> {
                    val before = lastEmitted
                    val after = if (i + 1 < count) points[i + 1] else -1
                    if (joinerAllowed(before, after)) {
                        out.appendCodePoint(point)
                        lastEmitted = point
                    } else {
                        flags += SafetyFlag.INVISIBLE_CHARS_REMOVED
                    }
                }
                type == Character.CONTROL.toInt() -> flags += SafetyFlag.CONTROL_CHARS_REMOVED
                type == Character.FORMAT.toInt() -> flags += SafetyFlag.INVISIBLE_CHARS_REMOVED
                type == Character.PRIVATE_USE.toInt() -> flags += SafetyFlag.CONTROL_CHARS_REMOVED
                type == Character.SPACE_SEPARATOR.toInt() -> { out.append(' '); lastEmitted = ' '.code }
                point in 0xFE00..0xFE0D -> flags += SafetyFlag.INVISIBLE_CHARS_REMOVED // keep FE0E/FE0F (emoji/text style)
                point in 0xE0100..0xE01EF -> {
                    // Ideographic variation selectors are legitimate only directly after a Han character.
                    if (lastEmitted >= 0 && Character.UnicodeScript.of(lastEmitted) == Character.UnicodeScript.HAN) {
                        out.appendCodePoint(point)
                        lastEmitted = point
                    } else {
                        flags += SafetyFlag.INVISIBLE_CHARS_REMOVED
                    }
                }
                else -> { out.appendCodePoint(point); lastEmitted = point }
            }
            i++
        }

        val normalized = Normalizer.normalize(out, Normalizer.Form.NFC)
        val limited = limitCombiningRuns(normalized, flags)
        return Cleaned.Ok(limited, flags)
    }

    private fun emitNewline(out: StringBuilder, allowNewlines: Boolean) {
        out.append(if (allowNewlines) '\n' else ' ')
    }

    private fun isJoinerContext(point: Int): Boolean =
        point >= 0 && point != ZWJ && point != ZWNJ && !Character.isWhitespace(point) && !Character.isSpaceChar(point) &&
            Character.getType(point) != Character.CONTROL.toInt()

    private fun isEmojiLike(point: Int): Boolean {
        val type = Character.getType(point)
        return point in 0x1F000..0x1FFFF || point == 0xFE0F ||
            type == Character.OTHER_SYMBOL.toInt() || type == Character.MODIFIER_SYMBOL.toInt()
    }

    private fun isLatin(point: Int): Boolean = Character.UnicodeScript.of(point) == Character.UnicodeScript.LATIN

    /**
     * ZWJ/ZWNJ carry meaning inside emoji sequences and in scripts such as Persian or Indic, but between plain
     * Latin letters (or at an edge, or doubled) they are only an invisible matching/spoofing channel.
     */
    private fun joinerAllowed(before: Int, after: Int): Boolean {
        if (!isJoinerContext(before) || !isJoinerContext(after)) return false
        if (isEmojiLike(before) || isEmojiLike(after)) return true
        val letterish = { p: Int -> Character.isLetter(p) || isMark(p) }
        return letterish(before) && letterish(after) && !isLatin(before) && !isLatin(after)
    }

    private fun isMark(point: Int): Boolean {
        val type = Character.getType(point)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun limitCombiningRuns(text: String, flags: MutableSet<SafetyFlag>): String {
        val out = StringBuilder(text.length)
        var run = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            index += Character.charCount(point)
            if (isMark(point)) {
                run++
                if (run > MAX_COMBINING_RUN) {
                    flags += SafetyFlag.COMBINING_MARKS_LIMITED
                    continue
                }
            } else {
                run = 0
            }
            out.appendCodePoint(point)
        }
        return out.toString()
    }

    private fun String.collapseBlankLines(): String =
        lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n")

    // ---- template / control-token handling ----------------------------------------------------------------------

    private val thinkBlock = Regex("(?is)<think>.*?</think>")
    private val thinkOpenTail = Regex("(?is)<think>.*$")
    private val leadingArtifacts = listOf(
        Regex("^\\s*(?:<bos>|<s>|<\\|begin_of_text\\|>)"),
        Regex("^\\s*<\\|start_header_id\\|>\\s*(?:assistant|model)\\s*<\\|end_header_id\\|>"),
        Regex("^\\s*<\\|im_start\\|>\\s*(?:assistant|model)\\s*(?:\\n|:)?"),
        Regex("^\\s*<start_of_turn>\\s*model\\s*(?:\\n|:)?"),
        Regex("^\\s*\\[/INST]"),
        Regex("(?i)^\\s*(?:assistant|model)\\s*(?::|\\n)"),
    )
    private val turnTerminators = Regex(
        "(?i)<\\|im_end\\|>|<\\|im_start\\|>|<\\|endoftext\\|>|<\\|eot_id\\|>|<\\|end\\|>|<end_of_turn>|<start_of_turn>|" +
            "<eos>|</s>|<\\|(?:user|assistant|system)\\|>|\\[INST]",
    )
    private val strayTokens = Regex(
        "(?i)<\\|[a-z_]{2,32}\\|>|<(?:bos|eos|pad|unk|s|/s|start_of_turn|end_of_turn)>|<</?SYS>>|\\[/?INST]",
    )

    private fun stripTemplate(input: String, flags: MutableSet<SafetyFlag>): String {
        var text = input
        if (thinkBlock.containsMatchIn(text)) {
            text = thinkBlock.replace(text, "")
            flags += SafetyFlag.TEMPLATE_ARTIFACT_REMOVED
        }
        if (thinkOpenTail.containsMatchIn(text)) {
            text = thinkOpenTail.replace(text, "")
            flags += SafetyFlag.TEMPLATE_ARTIFACT_REMOVED
        }
        var changed = true
        while (changed) {
            changed = false
            for (pattern in leadingArtifacts) {
                val match = pattern.find(text) ?: continue
                text = text.substring(match.range.last + 1)
                flags += SafetyFlag.TEMPLATE_ARTIFACT_REMOVED
                changed = true
            }
        }
        val terminator = turnTerminators.find(text)
        if (terminator != null) {
            text = text.substring(0, terminator.range.first)
            flags += SafetyFlag.TEMPLATE_ARTIFACT_REMOVED
            flags += SafetyFlag.TRUNCATED_AT_TURN_END
        }
        if (strayTokens.containsMatchIn(text)) {
            text = strayTokens.replace(text, "")
            flags += SafetyFlag.TEMPLATE_ARTIFACT_REMOVED
        }
        return text
    }

    private val fencedBlock = Regex("(?s)^\\s*```[A-Za-z0-9_+-]{0,20}[ \\t]*\\n?(.*?)\\n?```\\s*$")

    private fun unwrapFence(text: String): String {
        val match = fencedBlock.find(text) ?: return text
        val inner = match.groupValues[1]
        return if (inner.contains("```")) text else inner
    }

    // ---- advisory flags ------------------------------------------------------------------------------------------

    private val urlPattern = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\"']+")
    private val phonePattern = Regex("(?<![\\w])\\+?\\d[\\d\\s().-]{7,}\\d")
    private val impersonation = listOf(
        Regex("(?im)^\\s*(?:system|assistant|akma|app|android|notice|warning)\\s*[:：]"),
        Regex("(?i)paste and send manually"),
        Regex("(?i)\\bcopied\\.\\s"),
        Regex("(?i)\\btap\\s+(?:confirm|allow|ok|accept|continue)\\b"),
        Regex("(?i)ignore\\s+(?:all\\s+|any\\s+)?(?:previous|prior|above)\\s+(?:instructions|messages)"),
        Regex("(?i)\\bconfirm\\s+(?:now|to continue)\\b"),
    )

    private fun advisoryFlags(text: String): Set<SafetyFlag> {
        val flags = linkedSetOf<SafetyFlag>()
        val urls = urlPattern.findAll(text).map { it.value }.toList()
        if (urls.isNotEmpty()) flags += SafetyFlag.CONTAINS_URL
        if (urls.any(::isSuspiciousUrl)) flags += SafetyFlag.SUSPICIOUS_URL
        if (phonePattern.findAll(text).any { match -> match.value.count(Char::isDigit) in 9..15 }) {
            flags += SafetyFlag.CONTAINS_PHONE
        }
        if (impersonation.any { it.containsMatchIn(text) }) flags += SafetyFlag.UI_IMPERSONATION
        if (text.split(Regex("\\s+")).any(::isMixedScriptWord)) flags += SafetyFlag.MIXED_SCRIPT_WORD
        return flags
    }

    private fun isSuspiciousUrl(url: String): Boolean {
        val withoutScheme = url.substringAfter("://", url)
        val authority = withoutScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':')
        return authority.contains('@') ||
            host.split('.').any { it.startsWith("xn--", ignoreCase = true) } ||
            host.any { it.code > 0x7F }
    }

    private fun isMixedScriptWord(word: String): Boolean {
        var latin = false
        var other = false
        var index = 0
        while (index < word.length) {
            val point = word.codePointAt(index)
            index += Character.charCount(point)
            if (!Character.isLetter(point)) continue
            when (Character.UnicodeScript.of(point)) {
                Character.UnicodeScript.LATIN -> latin = true
                Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.GREEK,
                Character.UnicodeScript.ARMENIAN, Character.UnicodeScript.CHEROKEE -> other = true
                else -> Unit
            }
        }
        return latin && other
    }
}
