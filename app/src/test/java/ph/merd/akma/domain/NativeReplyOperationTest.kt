package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Tests the production callback bridge with synthetic callbacks, never JNI termination or generated prose quality. */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeReplyOperationTest {
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
                if (operation.completed) slot.invalidate().getOrThrow() else slot.quarantine()
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
