package ph.merd.akma.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlaySessionTest {
    @Test fun onlyExplicitActivityIntentCanShow() {
        assertTrue(isOverlayShowRequest(true, null))
        assertFalse(isOverlayShowRequest(false, null))
        assertFalse(isOverlayShowRequest(true, "ph.merd.akma.CLOSE_OVERLAY"))
        assertFalse(isOverlayShowRequest(true, "unexpected"))
    }

    @Test fun denialNeverAttachesAndReleasesOnce() {
        var attaches = 0
        var releases = 0
        val session = OverlaySession({ attaches++ }, { releases++ })
        assertFalse(session.show(false))
        session.close()
        assertFalse(session.show(true))
        assertEquals(0, attaches)
        assertEquals(1, releases)
    }

    @Test fun repeatedShowOwnsOneAttachment() {
        var attaches = 0
        var releases = 0
        val session = OverlaySession({ attaches++ }, { releases++ })
        repeat(20) { assertTrue(session.show(true)) }
        assertEquals(1, attaches)
        assertEquals(0, releases)
    }

    @Test fun panelCloseNotificationCloseAndDestroyReleaseOnce() {
        var releases = 0
        val session = OverlaySession({}, { releases++ })
        session.show(true)
        repeat(3) { session.close() }
        assertEquals(1, releases)
        assertFalse(session.show(true))
    }

    @Test fun closeBeforeShowDoesNotReopenStoppedService() {
        var attaches = 0
        var releases = 0
        val session = OverlaySession({ attaches++ }, { releases++ })
        session.close()
        assertFalse(session.show(true))
        assertEquals(0, attaches)
        assertEquals(1, releases)
    }

    @Test fun partialAttachmentFailureReleasesAndCannotRetrySameInstance() {
        var releases = 0
        val failure = IllegalStateException("synthetic attachment failure")
        val session = OverlaySession({ throw failure }, { releases++ })
        try {
            session.show(true)
            fail("Expected attachment failure")
        } catch (observed: IllegalStateException) {
            assertSame(failure, observed)
        }
        session.close()
        assertFalse(session.show(true))
        assertEquals(1, releases)
    }

    @Test fun revocationClosesExistingAttachment() {
        var attaches = 0
        var releases = 0
        val session = OverlaySession({ attaches++ }, { releases++ })
        assertTrue(session.show(true))
        assertFalse(session.show(false))
        assertEquals(1, attaches)
        assertEquals(1, releases)
        assertTrue(session.closed)
    }

    @Test fun repeatedUserSessionsHaveNoRetainedAttachment() {
        var liveAttachments = 0
        var peakAttachments = 0
        repeat(20) {
            val session = OverlaySession(
                { liveAttachments++; peakAttachments = maxOf(peakAttachments, liveAttachments) },
                { liveAttachments-- },
            )
            session.show(true)
            session.show(true)
            session.close()
            session.close()
            assertEquals(0, liveAttachments)
        }
        assertEquals(1, peakAttachments)
    }
    @Test fun repeatedPanelDismissalsClearButKeepOneBubbleWindow() {
        var attaches = 0
        var releases = 0
        var displays = 0
        var clears = 0
        val session = OverlaySession({ attaches++ }, { releases++ }, { displays++ }, {}, { clears++ })
        session.show(true)
        repeat(20) {
            session.expand()
            session.expand()
            assertTrue(session.expanded)
            session.collapse()
            session.collapse()
            assertFalse(session.expanded)
            assertFalse(session.closed)
        }
        assertEquals(1, attaches)
        assertEquals(0, releases)
        assertEquals(20, displays)
        assertEquals(20, clears)
        session.close()
        session.expand()
        assertEquals(1, releases)
        assertEquals(20, displays)
    }

    @Test fun failureSwitchingPanelReleasesWindowOnce() {
        var releases = 0
        val session = OverlaySession({}, { releases++ }, { error("synthetic layout failure") })
        session.show(true)
        try { session.expand(); fail("Expected failure") } catch (_: IllegalStateException) { }
        session.close()
        assertTrue(session.closed)
        assertFalse(session.expanded)
        assertEquals(1, releases)
    }

    @Test fun collapseClearsBeforeShowingBubble() {
        val events = mutableListOf<String>()
        val session = OverlaySession({}, {}, {}, { events += "bubble" }, { events += "clear" })
        session.show(true)
        session.expand()
        session.collapse()
        assertEquals(listOf("clear", "bubble"), events)
    }

    @Test fun permissionRevocationWhileExpandedCannotReopen() {
        var releases = 0
        val session = OverlaySession({}, { releases++ })
        session.show(true)
        session.expand()
        assertFalse(session.show(false))
        session.expand()
        session.collapse()
        assertEquals(1, releases)
        assertFalse(session.expanded)
    }
}
