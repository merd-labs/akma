package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Tests the production callback bridge with synthetic callbacks, never JNI termination or generated prose quality. */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeReplyOperationTest {
    @Test fun missingTerminalCallbackLeavesCoordinatorNonBusyAndBlocksFurtherInference() = runTest {
        val runtime = NativeHandleSlot<AutoCloseable>()
        lateinit var callbacks: NativeReplyCallbacks
        var starts = 0
        var initializations = 0
        val operation = NativeReplyOperation(20, onCancellationFailure = { runtime.quarantine() })
        val engine = object : LocalReplyEngine, RuntimeRecovery {
            override val requiresRestart get() = runtime.quarantined
            override suspend fun invalidateRuntime() = runtime.invalidate()
            override suspend fun initialize(): Result<Unit> {
                initializations++
                runtime.install(AutoCloseable {})
                return Result.success(Unit)
            }
            override suspend fun analyze(request: AnalyzeRequest) = Result.success(AnalysisResult(
                "interview_invitation", "Synthetic analysis", true,
                listOf(requireNotNull(ActionCatalog.action("reschedule"))), AnalysisSource.DETERMINISTIC,
            ))
            override suspend fun draft(request: DraftRequest): Result<String> = Result.success(
                operation.await({ starts++; callbacks = it }, {}),
            )
        }
        val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
        try {
            replies.initialize(); runCurrent()
            replies.setMessage("Synthetic invitation"); replies.analyze(); runCurrent()
            replies.draft("reschedule", ReplyTone.PROFESSIONAL)
            replies.confirmDraft(requireNotNull(replies.state.value.pendingConfirmation).id); runCurrent()
            assertEquals(ReplyPhase.Drafting, replies.state.value.phase)
            replies.cancel(); runCurrent()
            assertFalse(replies.state.value.busy)
            advanceTimeBy(5_001); runCurrent()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertEquals("Local AI cleanup failed. Restart Akma before retrying.", replies.state.value.notice)
            assertFalse(replies.state.value.canCopy)
            assertNull(replies.state.value.pendingConfirmation)
            callbacks.onText("Late output"); callbacks.onComplete(); runCurrent()
            assertEquals("", replies.state.value.draft)
            replies.setMessage("New synthetic message"); replies.analyze(); replies.initialize(); runCurrent()
            assertEquals(1, starts)
            assertEquals(1, initializations)
            assertTrue(runtime.quarantined)
            assertFalse(replies.state.value.busy)
        } finally {
            callbacks.onComplete(CancellationException("Synthetic cleanup")); runCurrent()
        }
    }

    @Test fun missingTerminalCallbackEndsCleanupWithoutClosingOrReusingNativeHandles() = runTest {
        val runtime = NativeHandleSlot<AutoCloseable>()
        val conversation = NativeHandleSlot<AutoCloseable>()
        var closes = 0
        runtime.install(AutoCloseable { closes++ })
        conversation.install(AutoCloseable { closes++ })
        val operation = NativeReplyOperation(20, onCancellationFailure = { runtime.quarantine() })
        lateinit var callbacks: NativeReplyCallbacks
        var cancellations = 0
        var output: String? = null
        val job = launch {
            try {
                output = operation.await({ callbacks = it }, { cancellations++ })
            } finally {
                if (operation.started && !operation.completed) conversation.quarantine()
                else conversation.invalidate().getOrThrow()
            }
        }
        try {
            runCurrent()
            callbacks.onText("Partial output")
            job.cancel(); runCurrent()
            advanceTimeBy(5_001); runCurrent()
            assertTrue("Missing native terminal callback must not hold coroutine cleanup forever", job.isCompleted)
            assertTrue(job.isCancelled)
            assertEquals(1, cancellations)
            assertEquals(0, closes)
            assertTrue(runtime.quarantined)
            assertTrue(conversation.quarantined)
            assertNull(runtime.current)
            assertNull(conversation.current)
            callbacks.onText("Late output"); callbacks.onComplete(); runCurrent()
            assertFalse("Late callback must not make quarantined work reusable", operation.completed)
            assertNull(output)
            assertTrue(runtime.invalidate().exceptionOrNull() is RuntimeRestartRequiredException)
        } finally {
            // Complete a broken implementation too, so the reproducer fails an assertion without hanging its test scope.
            callbacks.onComplete(CancellationException("Synthetic cleanup")); runCurrent()
        }
    }

    @Test fun oversizedOutputRequestsCancellationBeforeTerminalCallback() = runTest {
        val operation = NativeReplyOperation(5)
        lateinit var callbacks: NativeReplyCallbacks
        var cancellations = 0
        var failure: Throwable? = null
        val job = launch {
            try { operation.await({ callbacks = it }, { cancellations++ }) }
            catch (error: Exception) { failure = error }
        }
        try {
            runCurrent(); callbacks.onText("123456"); runCurrent()
            assertEquals(1, cancellations)
            assertFalse(operation.completed)
            assertFalse(job.isCompleted)
            callbacks.onComplete(); runCurrent()
            assertTrue(failure is IllegalStateException)
            assertEquals("Native reply exceeds the raw text limit.", failure?.message)
        } finally { callbacks.onComplete(); runCurrent() }
    }

    @Test fun oversizedOutputWithoutTerminalCallbackQuarantinesWithinCleanupDeadline() = runTest {
        var quarantines = 0
        val operation = NativeReplyOperation(5, onCancellationFailure = { quarantines++ })
        lateinit var callbacks: NativeReplyCallbacks
        var cancellations = 0
        var failure: Throwable? = null
        val job = launch {
            try { operation.await({ callbacks = it }, { cancellations++ }) }
            catch (error: Exception) { failure = error }
        }
        try {
            runCurrent(); callbacks.onText("123456"); runCurrent()
            advanceTimeBy(5_001); runCurrent()
            assertTrue(job.isCompleted)
            assertEquals(1, cancellations)
            assertEquals(1, quarantines)
            assertFalse(operation.completed)
            assertTrue(failure is IllegalStateException)
            callbacks.onText("late"); callbacks.onComplete(); runCurrent()
            assertFalse(operation.completed)
        } finally { callbacks.onComplete(); runCurrent() }
    }

    @Test fun callbackConversionFailureCancelsButCannotCloseBeforeTerminal() = runTest {
        val operation = NativeReplyOperation(20)
        lateinit var callbacks: NativeReplyCallbacks
        var cancellations = 0
        var failure: Throwable? = null
        val conversion = IllegalStateException("Synthetic callback conversion")
        val job = launch {
            try { operation.await({ callbacks = it }, { cancellations++ }) }
            catch (error: Exception) { failure = error }
        }
        runCurrent(); callbacks.onText("Partial"); callbacks.onFailure(conversion); runCurrent()
        assertEquals(1, cancellations)
        assertFalse(operation.completed)
        assertFalse(job.isCompleted)
        callbacks.onText("Late text"); callbacks.onComplete(CancellationException("Synthetic native stop")); runCurrent()
        assertTrue(job.isCompleted)
        assertTrue(failure === conversion || failure?.cause === conversion)
    }

    @Test fun callbackConversionFatalFailureReachesCoroutineOwnerAfterTerminal() = runTest {
        listOf<Throwable>(UnsatisfiedLinkError("Synthetic binding"), OutOfMemoryError("Synthetic OOM"), InternalError("Synthetic fatal")).forEach { failure ->
            val escaped = mutableListOf<Throwable>()
            val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> escaped += error })
            try {
                val operation = NativeReplyOperation(20)
                lateinit var callbacks: NativeReplyCallbacks
                var cancellations = 0
                owner.launch { operation.await({ callbacks = it }, { cancellations++ }) }
                runCurrent(); callbacks.onFailure(failure); runCurrent()
                assertEquals(1, cancellations)
                assertFalse(operation.completed)
                callbacks.onComplete(); runCurrent()
                assertEquals(1, escaped.size)
                assertEquals(failure.javaClass, escaped.single().javaClass)
            } finally { owner.cancel(); runCurrent() }
        }
    }

    @Test fun nativeFatalTerminalIsNotHiddenByPriorOutputOverflow() = runTest {
        val escaped = mutableListOf<Throwable>()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, error -> escaped += error })
        try {
            val operation = NativeReplyOperation(5)
            lateinit var callbacks: NativeReplyCallbacks
            val fatal = InternalError("Synthetic fatal after overflow")
            owner.launch { operation.await({ callbacks = it }, {}) }
            runCurrent(); callbacks.onText("123456"); runCurrent()
            callbacks.onComplete(fatal); runCurrent()
            assertEquals(1, escaped.size)
            assertTrue(escaped.single() is InternalError)
        } finally { owner.cancel(); runCurrent() }
    }

    @Test fun cancellationBeforeStartupNeverInvokesNativeGeneration() = runTest {
        val operation = NativeReplyOperation(20)
        var starts = 0
        var cancellations = 0
        val job = launch {
            currentCoroutineContext().cancel()
            operation.await({ callbacks -> starts++; callbacks.onComplete() }, { cancellations++ })
        }
        runCurrent()
        assertEquals(0, starts)
        assertEquals(0, cancellations)
        assertFalse(operation.started)
        assertTrue(job.isCancelled)
        assertTrue(job.isCompleted)
    }

    @Test fun chunksPublishOnlyAfterTerminalCompletionAndIgnoreLateCallbacks() = runTest {
        val operation = NativeReplyOperation(20)
        lateinit var callbacks: NativeReplyCallbacks
        var output: String? = null
        val job = launch { output = operation.await({ callbacks = it }, { error("Unexpected cancellation") }) }
        runCurrent()
        callbacks.onText("Synthetic "); callbacks.onText("reply")
        assertFalse(operation.completed)
        assertNull(output)
        callbacks.onComplete(); callbacks.onText("MALICIOUS LATE TEXT"); callbacks.onComplete(AssertionError())
        runCurrent()
        assertEquals("Synthetic reply", output)
        assertTrue(job.isCompleted)
    }

    @Test fun cancellationRequestsNativeCancelAndWaitsForTerminalCallbackBeforeClose() = runTest {
        val operation = NativeReplyOperation(20)
        val slot = NativeHandleSlot<AutoCloseable>()
        var cancellations = 0
        var closes = 0
        slot.install(AutoCloseable { closes++ })
        lateinit var callbacks: NativeReplyCallbacks
        var output: String? = null
        val job = launch {
            try {
                output = operation.await({ callbacks = it }, { cancellations++ })
            } finally {
                if (operation.started && !operation.completed) slot.quarantine() else slot.invalidate().getOrThrow()
            }
        }
        runCurrent(); callbacks.onText("Partial reply")
        job.cancel(); runCurrent()
        assertEquals(1, cancellations)
        assertEquals(0, closes)
        assertNotNull(slot.current)
        assertFalse(job.isCompleted)
        callbacks.onComplete(CancellationException("PRIVATE native cancellation")); runCurrent()
        assertEquals(1, closes)
        assertNull(slot.current)
        assertTrue(job.isCancelled)
        assertTrue(job.isCompleted)
        assertNull(output)
    }

    @Test fun nonCooperativeLateSuccessStillCannotPublishAfterCancellation() = runTest {
        val operation = NativeReplyOperation(20)
        lateinit var callbacks: NativeReplyCallbacks
        var output: String? = null
        var cancellations = 0
        val job = launch { output = operation.await({ callbacks = it }, { cancellations++ }) }
        runCurrent(); job.cancel(); runCurrent()
        callbacks.onText("Late native success"); callbacks.onComplete(); runCurrent()
        assertEquals(1, cancellations)
        assertTrue(job.isCancelled)
        assertNull(output)
    }

    @Test fun outputOverflowWaitsForCompletionAndRejectsRatherThanTruncates() = runTest {
        val operation = NativeReplyOperation(5)
        lateinit var callbacks: NativeReplyCallbacks
        var failure: Throwable? = null
        val job = launch {
            try { operation.await({ callbacks = it }, {}) } catch (error: Exception) { failure = error }
        }
        runCurrent(); callbacks.onText("12345"); callbacks.onText("6")
        assertFalse(operation.completed)
        assertFalse(job.isCompleted)
        callbacks.onComplete(); runCurrent()
        assertTrue(failure is IllegalStateException)
        assertEquals("Native reply exceeds the raw text limit.", failure?.message)
    }

    @Test fun nativeCallbackFailureKeepsItsTypeAndFatalErrorsReachTheOwner() = runTest {
        listOf<Throwable>(UnsatisfiedLinkError("PRIVATE native"), OutOfMemoryError("PRIVATE OOM"), InternalError("PRIVATE fatal")).forEach { failure ->
            val escaped = mutableListOf<Throwable>()
            val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> escaped += error })
            try {
                val operation = NativeReplyOperation(20)
                lateinit var callbacks: NativeReplyCallbacks
                owner.launch { operation.await({ callbacks = it }, {}) }
                runCurrent(); callbacks.onComplete(failure); runCurrent()
                assertEquals(listOf(failure), escaped)
            } finally { owner.cancel(); runCurrent() }
        }
    }

    @Test fun cancellationHookFailureDoesNotPermitPrematureClose() = runTest {
        val escaped = mutableListOf<Throwable>()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, error -> escaped += error })
        try {
            var quarantines = 0
            val operation = NativeReplyOperation(20, onCancellationFailure = { quarantines++ })
            val failure = UnsatisfiedLinkError("PRIVATE cancel")
            lateinit var callbacks: NativeReplyCallbacks
            val job = owner.launch { operation.await({ callbacks = it }, { throw failure }) }
            runCurrent(); job.cancel(); runCurrent()
            assertFalse(job.isCompleted)
            assertFalse(operation.completed)
            assertEquals(1, quarantines)
            callbacks.onComplete(); runCurrent()
            // Coroutine stack recovery may copy an exception while keeping its type and original cause.
            assertEquals(1, escaped.size)
            assertEquals(failure.javaClass, escaped.single().javaClass)
            assertTrue(escaped.single() === failure || escaped.single().cause === failure)
        } finally { owner.cancel(); runCurrent() }
    }

    @Test fun fatalCallbackAfterCancellationIsNotHiddenAsOrdinaryCancellation() = runTest {
        val escaped = mutableListOf<Throwable>()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, error -> escaped += error })
        try {
            val operation = NativeReplyOperation(20)
            lateinit var callbacks: NativeReplyCallbacks
            val fatal = InternalError("PRIVATE fatal")
            val job = owner.launch { operation.await({ callbacks = it }, {}) }
            runCurrent(); job.cancel(); runCurrent()
            callbacks.onComplete(fatal); runCurrent()
            assertEquals(1, escaped.size)
            assertEquals(fatal.javaClass, escaped.single().javaClass)
            assertTrue(escaped.single() === fatal || escaped.single().cause === fatal)
        } finally { owner.cancel(); runCurrent() }
    }
}
