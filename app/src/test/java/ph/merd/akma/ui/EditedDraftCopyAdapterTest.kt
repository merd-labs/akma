package ph.merd.akma.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ph.merd.akma.domain.*

/** Actual event bindings with synthetic inference and clipboard callbacks; no device claims. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditedDraftCopyAdapterTest {
    private class Engine : LocalReplyEngine {
        var drafts = 0
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun analyze(request: AnalyzeRequest) = Result.success(ActionCatalog.otherAnalysis())
        override suspend fun draft(request: DraftRequest): Result<String> {
            drafts++
            return Result.success("Synthetic draft")
        }
    }

    private fun TestScope.editable(engine: Engine): ReplyCoordinator =
        ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler)).also {
            it.initialize(); runCurrent()
            it.setMessage("Synthetic source"); it.analyze(); runCurrent()
            it.selectDraft("acknowledge", ReplyTone.PROFESSIONAL)
            it.confirmDisplayedDraft(requireNotNull(it.state.value.pendingConfirmation).id); runCurrent()
            assertTrue(it.state.value.canCopy)
        }

    @Test fun unsafeEditBlocksBothStaleAndFreshCopyCallbacksUntilCorrected() = runTest {
        val engine = Engine()
        val replies = editable(engine)
        val copies = mutableListOf<String>()
        var failures = 0
        val adapter = JourneyCoordinatorAdapter(replies, {}, { copies += it.draft; true },
            copyFailed = { failures++ })
        val original = adapter.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        val unsafe = "Synthetic\u202E edit"
        original.onDraftChange(unsafe)
        assertEquals(unsafe, replies.state.value.draft)
        assertEquals("Review draft formatting before copying.", replies.state.value.notice)
        original.onCopy()
        val rejected = adapter.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        repeat(10) { rejected.onCopy() }
        assertTrue(copies.isEmpty())
        assertEquals(0, failures)
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        rejected.onDraftChange("Synthetic corrected edit")
        assertNull(replies.state.value.notice)
        rejected.onCopy()
        assertTrue(copies.isEmpty())
        val accepted = adapter.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        repeat(10) { accepted.onCopy() }
        assertEquals(listOf("Synthetic corrected edit"), copies)
        assertEquals(ReplyPhase.Copied, replies.state.value.phase)
        assertEquals(1, engine.drafts)
    }

    @Test fun closeClearsUnsafeEditAndSourceBeforeNavigationAndInvalidatesCopy() = runTest {
        val replies = editable(Engine())
        var copies = 0
        var closes = 0
        val adapter = JourneyCoordinatorAdapter(replies, {}, { copies++; true }, close = {
            closes++
            assertEquals("", replies.state.value.message)
            assertEquals("", replies.state.value.draft)
            assertNull(replies.state.value.analysis)
            assertNull(replies.state.value.pendingConfirmation)
            assertNull(replies.state.value.notice)
        })
        val original = adapter.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        original.onDraftChange("Synthetic\u202E edit")
        val unsafe = adapter.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        requireNotNull(unsafe.onClose).invoke()
        original.onCopy(); unsafe.onCopy()
        assertEquals(1, closes)
        assertEquals(0, copies)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
    }
}
