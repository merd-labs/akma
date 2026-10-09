# Stable Kotlin contract — DO NOT MODIFY CONCURRENTLY

Owner: Miguel; reviewer: Elijah. Changes require a short PR reviewed before UI/backend branches rebase. Create these types in a shared `domain` or `ai` package after the Gradle skeleton is generated.

```kotlin
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
```

Validation: action ID allowlist; max 4 actions; no blank messages/drafts; cap text (start at 1,500 characters, tune after measurement); no false 'confirmed' responses; fail visibly on unsupported model or malformed JSON. Prompt input must be treated as untrusted content. `analyze` may return clearly labeled rule-based presets only when actual LLM classification is infeasible, not fabricated model results.

UI states: PermissionRequired → ModelUnavailable → ModelLoading → Ready → Analyzing → ChoosingAction → Drafting → Editing → Copied, with recoverable Error at each processing step. Cancellation and duplicate-click handling are required.
