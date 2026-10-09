package ph.merd.akma.domain

data class AnalyzeRequest(
    val message: String,
    val history: String = "",
    val relationship: String? = null,
)

data class SuggestedAction(val id: String, val label: String)

data class AnalysisResult(
    val category: String,
    val summary: String,
    val requiresUserDecision: Boolean,
    val actions: List<SuggestedAction>,
)

enum class ReplyTone { PROFESSIONAL, FRIENDLY, CONCISE }

data class DraftRequest(
    val original: AnalyzeRequest,
    val selectedActionId: String,
    val tone: ReplyTone,
    val userInstruction: String = "",
)

interface LocalReplyEngine {
    suspend fun initialize(): Result<Unit>
    suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult>
    suspend fun draft(request: DraftRequest): Result<String>
}
