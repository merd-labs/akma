package ph.merd.akma.domain

/** Honest default until a real API-30-compatible adapter passes the phone gate. */
class UnavailableReplyEngine : LocalReplyEngine {
    override suspend fun initialize(): Result<Unit> = unavailable()
    override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = unavailable()
    override suspend fun draft(request: DraftRequest): Result<String> = unavailable()

    private fun <T> unavailable(): Result<T> = Result.failure(ModelUnavailableException())
}

class ModelUnavailableException : IllegalStateException("No local model is configured.")
