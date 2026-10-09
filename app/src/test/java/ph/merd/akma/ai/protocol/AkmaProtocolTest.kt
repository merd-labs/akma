package ph.merd.akma.ai.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.ReplyTone

class AkmaProtocolTest {

    @Test
    fun testCompileAnalysisPrompt() {
        val request = AnalyzeRequest(message = "Let's meet tomorrow.")
        val prompt = AkmaProtocol.compileAnalysisPrompt(request)
        assertTrue(prompt.contains("Let's meet tomorrow."))
        assertTrue(prompt.contains("Output strictly valid JSON"))
        assertTrue(prompt.contains("<|im_start|>user"))
        assertTrue(prompt.contains("<|im_end|>"))
    }

    @Test
    fun testDecodeAnalysis_ValidJSON() {
        val rawResponse = """
            Here is the analysis:
            {
                "category": "meeting",
                "summary": "User wants to meet tomorrow.",
                "language": "english"
            }
        """.trimIndent()
        val result = AkmaProtocol.decodeAnalysis(rawResponse)
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals("meeting", analysis.category)
        assertEquals("User wants to meet tomorrow.", analysis.summary)
        assertEquals(AnalysisSource.LOCAL_MODEL, analysis.source)
        assertTrue(analysis.actions.size <= 3)
    }

    @Test
    fun testDecodeAnalysis_MalformedJSON_ReturnsOther() {
        val rawResponse = "Just regular text without json."
        val result = AkmaProtocol.decodeAnalysis(rawResponse)
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals("other", analysis.category)
        assertEquals(AnalysisSource.DETERMINISTIC, analysis.source)
    }

    @Test
    fun testDecodeAnalysis_UnknownCategory_ReturnsOther() {
        val rawResponse = """{"category": "random_stuff", "summary": "Unknown."}"""
        val result = AkmaProtocol.decodeAnalysis(rawResponse)
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals("other", analysis.category)
    }

    @Test
    fun testDecodeAnalysis_LegacyActionOutput_ReturnsOther() {
        val rawResponse = """{"action_1": "reply", "action_2": "ignore"}"""
        val result = AkmaProtocol.decodeAnalysis(rawResponse)
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals("other", analysis.category)
    }

    @Test
    fun testCompileDraftPrompt() {
        val analyzeRequest = AnalyzeRequest(message = "Let's meet tomorrow.")
        val draftRequest = DraftRequest(
            original = analyzeRequest,
            selectedActionId = "confirm",
            tone = ReplyTone.PROFESSIONAL
        )
        val prompt = AkmaProtocol.compileDraftPrompt(draftRequest)
        assertTrue(prompt.contains("Confirm"))
        assertTrue(prompt.contains("professional"))
        assertTrue(prompt.contains("Let's meet tomorrow."))
    }

    @Test
    fun testValidateParsedReply() {
        val raw = "  \"Sure, I can meet tomorrow.\"<|im_end|>  "
        val result = AkmaProtocol.validateParsedReply(raw)
        assertTrue(result.isSuccess)
        assertEquals("Sure, I can meet tomorrow.", result.getOrNull())
    }

    @Test
    fun testValidateParsedReply_Empty() {
        val raw = " <|im_end|> "
        val result = AkmaProtocol.validateParsedReply(raw)
        assertTrue(result.isFailure)
    }
}
