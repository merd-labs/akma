package ph.merd.akma.domain

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import ph.merd.akma.safety.ModelOutputSafety
import ph.merd.akma.safety.SafetyResult
import ph.merd.akma.safety.UnsafeModelOutputException

/** The only model-output parser. Model strings never select domain categories or action IDs. */
internal object LocalModelOutput {
    fun purpose(raw: String): String = displayText(stringField(raw.trim(), "message_purpose"))

    fun draft(raw: String): String {
        val clean = accepted(ModelOutputSafety.sanitizeDraft(raw, ModelOutputSafety.MAX_RAW_CHARS))
        val text = if (clean.startsWith("{")) stringField(clean, "reply") else clean
        val draft = accepted(ModelOutputSafety.sanitizeDraft(text, ReplyValidation.MAX_TEXT_LENGTH))
        require(!draft.startsWith("{") && !draft.endsWith("}")) { "Malformed draft." }
        return draft.also { ReplyValidation.validateDraft(it).getOrThrow() }
    }

    private fun displayText(text: String): String =
        accepted(ModelOutputSafety.sanitizeDisplayText(text, ReplyValidation.MAX_SUMMARY_CODE_POINTS))

    private fun accepted(result: SafetyResult): String = when (result) {
        is SafetyResult.Accepted -> result.text
        is SafetyResult.Rejected -> throw UnsafeModelOutputException(result.reason)
    }

    private fun stringField(raw: String, field: String): String {
        require(raw.length <= ModelOutputSafety.MAX_RAW_CHARS) { "Model output exceeds the raw text limit." }
        return JsonReader(StringReader(raw)).use { reader ->
            reader.strictness = Strictness.STRICT
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Malformed model output." }
            reader.beginObject()
            var value: String? = null
            while (reader.hasNext()) {
                if (reader.nextName() == field) {
                    require(value == null && reader.peek() == JsonToken.STRING) { "Malformed model output." }
                    value = reader.nextString()
                } else reader.skipValue()
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Malformed model output." }
            requireNotNull(value) { "Malformed model output." }
        }
    }
}
