package ph.merd.akma.domain

data class AnalyzeRequest(
    val message: String,
    val history: String = "",
    val relationship: String? = null,
)

data class SuggestedAction(val id: String, val label: String)

/** Classification provenance, supplied by the adapter, not extracted from model-generated JSON. */
enum class AnalysisSource { UNSPECIFIED, LOCAL_MODEL, HYBRID, DETERMINISTIC }

data class AnalysisResult(
    val category: String,
    val summary: String,
    val requiresUserDecision: Boolean,
    val actions: List<SuggestedAction>,
    val source: AnalysisSource = AnalysisSource.UNSPECIFIED,
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
    /** Return untrusted analysis with adapter-owned source metadata; callers normalize before display. */
    suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult>
    /** Interpret selectedActionId using ActionCatalog, never a model-supplied label. */
    suspend fun draft(request: DraftRequest): Result<String>
}
