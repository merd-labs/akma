package ph.merd.akma.demo

import kotlinx.coroutines.delay
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalysisResult
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.LocalReplyEngine

/**
 * TEMPORARY engine for debug builds while the local model is not ready. Returns DemoReplies through
 * the real ReplyCoordinator, so validation, the explicit Confirm step and copy rules all still apply.
 * Source is DETERMINISTIC, never LOCAL_MODEL, and the UI labels it as demo data.
 */
class DemoReplyEngine(
    private val analyzeDelayMillis: Long = 1_200,
    private val draftDelayMillis: Long = 1_500,
) : LocalReplyEngine {
    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> {
        delay(analyzeDelayMillis)
        return Result.success(
            AnalysisResult(
                category = DemoReplies.CATEGORY,
                summary = DemoReplies.SUMMARY,
                requiresUserDecision = true,
                actions = DemoReplies.actionIds.mapNotNull(ActionCatalog::action),
                source = AnalysisSource.DETERMINISTIC,
            ),
        )
    }

    override suspend fun draft(request: DraftRequest): Result<String> {
        delay(draftDelayMillis)
        return Result.success(DemoReplies.reply(request.selectedActionId, request.tone, request.userInstruction))
    }
}
