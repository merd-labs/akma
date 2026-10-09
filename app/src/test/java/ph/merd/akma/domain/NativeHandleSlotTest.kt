package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class NativeHandleSlotTest {
    @Test fun detachPrecedesCloseAndSuccessfulCleanupIsIdempotent() {
        val slot = NativeHandleSlot<AutoCloseable>()
        var closes = 0
        slot.install(AutoCloseable { assertNull(slot.current); closes++ })
        assertTrue(slot.invalidate().isSuccess)
        assertTrue(slot.invalidate().isSuccess)
        assertEquals(1, closes)
        assertFalse(slot.quarantined)
        slot.install(AutoCloseable { closes++ })
        assertTrue(slot.invalidate().isSuccess)
        assertEquals(2, closes)
    }

    @Test fun expectedCloseFailuresKeepTheirTypeAndQuarantineTheHandle() {
        listOf<Throwable>(IllegalStateException("PRIVATE close"), UnsatisfiedLinkError("PRIVATE close"), OutOfMemoryError("PRIVATE close")).forEach { failure ->
            val slot = NativeHandleSlot<AutoCloseable>()
            var closes = 0
            slot.install(AutoCloseable { closes++; throw failure })
            assertSame(failure, slot.invalidate().exceptionOrNull())
            assertNull(slot.current)
            assertTrue(slot.quarantined)
            assertTrue(slot.invalidate().exceptionOrNull() is RuntimeRestartRequiredException)
            assertThrows(RuntimeRestartRequiredException::class.java) { slot.install(AutoCloseable {}) }
            assertEquals(1, closes)
        }
    }

    @Test fun cancellationDuringClosePropagatesAndPreventsReuse() {
        val slot = NativeHandleSlot<AutoCloseable>()
        val cancelled = CancellationException("PRIVATE close cancellation")
        slot.install(AutoCloseable { throw cancelled })
        assertSame(cancelled, assertThrows(CancellationException::class.java) { slot.invalidate() })
        assertNull(slot.current)
        assertTrue(slot.quarantined)
    }

    @Test fun fatalCloseFailuresPropagateAndPreventReuse() {
        listOf<Error>(AssertionError("PRIVATE assertion"), InternalError("PRIVATE VM"), StackOverflowError("PRIVATE stack")).forEach { fatal ->
            val slot = NativeHandleSlot<AutoCloseable>()
            slot.install(AutoCloseable { throw fatal })
            assertSame(fatal, assertThrows(Error::class.java) { slot.invalidate() })
            assertNull(slot.current)
            assertTrue(slot.quarantined)
        }
    }

    @Test fun uncertainInFlightOwnershipQuarantinesWithoutClosingAnActiveHandle() {
        val slot = NativeHandleSlot<AutoCloseable>()
        var closes = 0
        slot.install(AutoCloseable { closes++ })
        slot.quarantine()
        assertNull(slot.current)
        assertTrue(slot.quarantined)
        assertTrue(slot.invalidate().isFailure)
        assertEquals(0, closes)
    }
}
