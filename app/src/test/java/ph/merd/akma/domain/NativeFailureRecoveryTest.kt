package ph.merd.akma.domain

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeFailureRecoveryTest {
    // Synthetic thrown errors reproduce the coordinator path, not device memory pressure or real inference.
    private class Engine(private val failure: Throwable) : LocalReplyEngine {
        override suspend fun initialize(): Result<Unit> = throw failure
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = throw failure
        override suspend fun draft(request: DraftRequest): Result<String> = throw failure
    }

    private fun rejectsNativeFailure(failure: Throwable) = runTest {
        val escaped = mutableListOf<Throwable>()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, error -> escaped += error })
        try {
            val replies = ReplyCoordinator(Engine(failure), owner, StandardTestDispatcher(testScheduler))
            replies.initialize()
            runCurrent()
            assertEquals("Native failure must end loading", ReplyPhase.Error, replies.state.value.phase)
            assertFalse(replies.state.value.busy)
            assertFalse(replies.state.value.canCopy)
            assertTrue(replies.state.value.notice.orEmpty().isNotBlank())
            assertFalse(replies.state.value.notice.orEmpty().contains("PRIVATE"))
            assertTrue("Expected native failure must not escape owner scope", escaped.isEmpty())
            replies.recover()
            assertEquals("Native failure must invalidate runtime readiness", ReplyPhase.ModelUnavailable, replies.state.value.phase)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun linkageFailureCannotLeaveInitializationLoading() = rejectsNativeFailure(UnsatisfiedLinkError("PRIVATE synthetic library failure"))

    @Test
    fun missingNativeClassCannotLeaveInitializationLoading() = rejectsNativeFailure(NoClassDefFoundError("PRIVATE synthetic class failure"))

    @Test
    fun memoryFailureCannotLeaveInitializationLoading() = rejectsNativeFailure(OutOfMemoryError("PRIVATE synthetic memory failure"))
}
