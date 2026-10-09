package ph.merd.akma.domain

import org.junit.Assert.*
import org.junit.Test
import ph.merd.akma.safety.UnsafeModelOutputException

/** Synthetic parser fixtures exercise production parsing, not model generation or semantic faithfulness. */
class LocalModelOutputTest {
    @Test fun strictAnalysisRejectsMissingNonStringDuplicateAndMalformedFields() {
        listOf(
            "{}", "[]", "null", "true", "{\"message_purpose\":null}",
            "{\"message_purpose\":42}", "{\"message_purpose\":true}",
            "{\"message_purpose\":[]}", "{\"message_purpose\":{}}",
            "{\"message_purpose\":\"first\",\"message_purpose\":\"second\"}",
            "{message_purpose:'test'}", "{\"message_purpose\":\"test\",}",
            "{\"message_purpose\":\"test\"} {}", "{\"message_purpose\":\"test\"} trailing",
        ).forEach { raw -> assertTrue("Malformed synthetic output must fail", runCatching { LocalModelOutput.purpose(raw) }.isFailure) }
    }

    @Test fun decodedPurposeIsSanitizedAndBoundedWithoutChoosingCategory() {
        assertEquals("Interview invitation", LocalModelOutput.purpose("{\"message_purpose\":\"Interview\\u202e invitation\\u0000\"}"))
        val purpose = LocalModelOutput.purpose("{\"message_purpose\":\"${"a".repeat(300)}\"}")
        assertEquals(ReplyValidation.MAX_SUMMARY_CODE_POINTS, purpose.length)
    }

    @Test fun acceptsValidatedPlainTextAndStrictJsonDrafts() {
        assertEquals("Could we choose another time?", LocalModelOutput.draft("Could we choose another time?"))
        assertEquals("Could we choose another time?", LocalModelOutput.draft("{\"reply\":\"Could we choose another time?\"}"))
        assertEquals("Thanks", LocalModelOutput.draft("```json\n{\"reply\":\"Thanks\"}\n```"))
    }

    @Test fun draftJsonRejectsNonStringDuplicateFieldsAndIncompleteObjects() {
        listOf(
            "{\"reply\":42}", "{\"reply\":false}", "{\"reply\":null}",
            "{\"reply\":[]}", "{\"reply\":{}}", "{\"reply\":\"first\",\"reply\":\"second\"}",
            "{reply:'test'}", "{\"reply\":\"test\",}", "{\"reply\":\"test\"",
            "{\"reply\":\"test\"} {}", "{\"reply\":\"test\"} trailing", "{}",
        ).forEach { raw -> assertTrue("Malformed synthetic output must fail", runCatching { LocalModelOutput.draft(raw) }.isFailure) }
    }

    @Test fun decodedDraftSafetyCannotBeBypassedWithJsonEscapes() {
        assertEquals("Thank you", LocalModelOutput.draft("{\"reply\":\"Thank\\u202e you\\u0000\"}"))
        assertTrue(runCatching { LocalModelOutput.draft("{\"reply\":\"\\ud800\"}") }.exceptionOrNull() is UnsafeModelOutputException)
        assertTrue(runCatching { LocalModelOutput.draft("{\"reply\":\"\\u200b\"}") }.isFailure)
    }

    @Test fun oversizedRawAndDecodedDraftsFailWithoutTruncation() {
        assertTrue(runCatching { LocalModelOutput.purpose("{\"message_purpose\":\"${"a".repeat(20_001)}\"}") }.isFailure)
        assertTrue(runCatching { LocalModelOutput.draft("a".repeat(20_001)) }.exceptionOrNull() is UnsafeModelOutputException)
        assertTrue(runCatching { LocalModelOutput.draft("{\"reply\":\"${"a".repeat(1_501)}\"}") }.exceptionOrNull() is UnsafeModelOutputException)
    }
}
