package ph.merd.akma.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reviewer regression: native/runtime bindings fail with java.lang.Error subclasses (UnsatisfiedLinkError for a missing
 * ABI library, NoClassDefFoundError, OutOfMemoryError for a large model), which `catch (Exception)` does not catch.
 * Synthetic exceptions only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeErrorResilienceTest {
    private class Engine(val failure: Throwable) : LocalReplyEngine {
        override suspend fun initialize(): Result<Unit> = throw failure
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = throw failure
        override suspend fun draft(request: DraftRequest): Result<String> = throw failure
    }

    private fun check(failure: Throwable) = runTest {
        val replies = ReplyCoordinator(Engine(failure), backgroundScope, StandardTestDispatcher(testScheduler))
        replies.initialize(); runCurrent()
        val state = replies.state.value
        assertEquals("phase after ${failure.javaClass.simpleName}", ReplyPhase.Error, state.phase)
        assertFalse(state.busy)
        assertFalse("private text must not reach the UI", state.notice.orEmpty().contains("PRIVATE"))
        assertTrue(state.notice.orEmpty().isNotBlank())
    }

    @Test fun unsatisfiedLinkErrorFromNativeLibraryIsAVisibleRecoverableError() = check(UnsatisfiedLinkError("PRIVATE dlopen failed: /data/app/lib/arm64/libx.so"))
    @Test fun noClassDefFoundErrorIsAVisibleRecoverableError() = check(NoClassDefFoundError("PRIVATE ai/runtime/Engine"))
    @Test fun outOfMemoryErrorIsAVisibleRecoverableError() = check(OutOfMemoryError("PRIVATE"))
}
