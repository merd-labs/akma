package ph.merd.akma.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftConfirmation
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone

class PanelStateMapperTest {
    // Deterministic catalog fallback only; no fixture here represents model output.
    private val analysis = ActionCatalog.otherAnalysis()
    private val action = analysis.actions.first()
    private val pending = DraftConfirmation(
        id = 7L,
        action = action,
        request = DraftRequest(AnalyzeRequest("message"), action.id, ReplyTone.CONCISE),
    )

    private fun ReplyState.ui(tone: ReplyTone = ReplyTone.PROFESSIONAL, selected: String? = null) = toPanelUi(tone, selected)

    @Test
    fun pendingConfirmationShowsExactStagedRequestAndBlocksCopy() {
        val ui = ReplyState(phase = ReplyPhase.ChoosingAction, message = "message", analysis = analysis, pendingConfirmation = pending).ui()
        val confirmation = ui.confirmation!!
        assertEquals(7L, confirmation.confirmationId)
        assertEquals(action.label, confirmation.actionLabel)
        assertEquals(ReplyTone.CONCISE, confirmation.tone)
        assertEquals("message", confirmation.message)
        assertEquals(ReplyTone.CONCISE, ui.choice!!.tone)
        assertEquals(action.id, ui.choice!!.selectedActionId)
        assertEquals(CopyUi.Disabled, ui.copy)
        assertNull(ui.reply)
        assertEquals(JourneyStep.Confirm, ui.step)
        assertTrue(confirmation.valid)
    }

    @Test
    fun pendingConfirmationKeepsChoicesEnabledAndMirrorsTheStagedRequest() {
        val ui = ReplyState(phase = ReplyPhase.ChoosingAction, message = "message", analysis = analysis, pendingConfirmation = pending)
            .ui(tone = ReplyTone.FRIENDLY, selected = "something_else")
        assertTrue(ui.choice!!.enabled)
        assertEquals(action.id, ui.choice!!.selectedActionId)
        assertEquals(ReplyTone.CONCISE, ui.choice!!.tone)
        assertFalse(ui.canStartOver)
    }

    @Test
    fun staleConfirmationCannotBeConfirmed() {
        // The message changed after staging, so the staged request no longer matches live state.
        val ui = ReplyState(phase = ReplyPhase.ChoosingAction, message = "edited", analysis = analysis, pendingConfirmation = pending).ui()
        assertEquals(7L, ui.confirmation!!.confirmationId)
        assertFalse(ui.confirmation!!.valid)
    }

    @Test
    fun startOverAndRetryFollowCoordinatorRules() {
        assertTrue(ReplyState(phase = ReplyPhase.Editing, message = "m", analysis = analysis, draft = "d").ui().canStartOver)
        assertFalse(ReplyState(phase = ReplyPhase.Drafting, message = "m", analysis = analysis).ui().canStartOver)
        assertFalse(ReplyState(phase = ReplyPhase.Ready).ui().canStartOver)
        assertTrue(ReplyState(phase = ReplyPhase.ModelUnavailable).ui().canRetry)
        assertTrue(ReplyState(phase = ReplyPhase.Error).ui().canRetry)
        assertFalse(ReplyState(phase = ReplyPhase.Ready).ui().canRetry)
    }

    @Test
    fun refineNeedsADraftAndTheActionThatMadeIt() {
        val editing = ReplyState(phase = ReplyPhase.Editing, message = "m", analysis = analysis, draft = "d")
        assertTrue(editing.ui(selected = action.id).canRefine)
        assertFalse(editing.ui(selected = null).canRefine)
        assertFalse(ReplyState(phase = ReplyPhase.Drafting, message = "m", analysis = analysis).ui(selected = action.id).canRefine)
        assertFalse(ReplyState(phase = ReplyPhase.ChoosingAction, message = "m", analysis = analysis).ui(selected = action.id).canRefine)
    }

    @Test
    fun refineConfirmationShowsWhatWillChange() {
        val refine = pending.copy(request = pending.request.copy(userInstruction = RefineKind.Shorter.instruction))
        val ui = ReplyState(phase = ReplyPhase.ChoosingAction, message = "message", analysis = analysis, pendingConfirmation = refine).ui()
        assertEquals(RefineKind.Shorter, ui.confirmation!!.refine)
        assertNull(ReplyState(phase = ReplyPhase.ChoosingAction, message = "message", analysis = analysis, pendingConfirmation = pending).ui().confirmation!!.refine)
    }

    @Test
    fun languageAndDemoPassThroughUnchanged() {
        val ui = ReplyState(phase = ReplyPhase.ChoosingAction, message = "m", analysis = analysis).toPanelUi(ReplyTone.PROFESSIONAL, null, language = "English", demo = true)
        assertEquals("English", ui.intent!!.language)
        assertTrue(ui.demo)
        assertFalse(ReplyState(phase = ReplyPhase.Ready).ui().demo)
    }

    @Test
    fun coordinatorNoticeIsShownVerbatimExceptAfterCopy() {
        assertEquals("Cancelled.", ReplyState(phase = ReplyPhase.Ready, notice = "Cancelled.").ui().notice)
        val copied = ReplyState(phase = ReplyPhase.Copied, message = "m", analysis = analysis, draft = "d", notice = "Copied. Paste and send manually.")
        assertNull(copied.ui().notice)
    }

    @Test
    fun choosingWithoutPendingHasNoConfirmation() {
        val ui = ReplyState(phase = ReplyPhase.ChoosingAction, message = "m", analysis = analysis).ui()
        assertNull(ui.confirmation)
        assertTrue(ui.choice!!.enabled)
        assertNull(ui.choice!!.selectedActionId)
        assertEquals(analysis.actions.map { it.label }, ui.choice!!.actions.map { it.label })
    }

    @Test
    fun draftingShowsWritingSkeletonAndLocksChoices() {
        val ui = ReplyState(phase = ReplyPhase.Drafting, message = "m", analysis = analysis).ui(selected = action.id)
        assertEquals(ReplyUi.Writing, ui.reply)
        assertFalse(ui.choice!!.enabled)
        assertEquals(action.id, ui.choice!!.selectedActionId)
        assertEquals(CopyUi.Disabled, ui.copy)
        assertTrue(ui.canCancelProcessing)
    }

    @Test
    fun editingEnablesCopyOnlyWhenCoordinatorAllows() {
        val editing = ReplyState(phase = ReplyPhase.Editing, message = "m", analysis = analysis, draft = "d")
        assertEquals(CopyUi.Ready, editing.ui().copy)
        assertEquals(ReplyUi.Draft("d"), editing.ui().reply)
        assertEquals(CopyUi.Disabled, editing.copy(draft = " ").ui().copy)
        assertEquals(CopyUi.Copied, editing.copy(phase = ReplyPhase.Copied).ui().copy)
    }

    @Test
    fun unknownSelectedActionIsNotShownAsSelected() {
        val ui = ReplyState(phase = ReplyPhase.Editing, message = "m", analysis = analysis, draft = "d").ui(selected = "accept")
        assertNull(ui.choice!!.selectedActionId)
    }

    @Test
    fun statusStatesMapToEmptyStates() {
        val unavailable = ReplyState(phase = ReplyPhase.ModelUnavailable, message = "m").ui()
        assertEquals(PanelStatus.ModelUnavailable, unavailable.status)
        assertFalse(unavailable.input!!.canAnalyze)
        assertEquals(PanelStatus.LoadingModel, ReplyState(phase = ReplyPhase.ModelLoading).ui().status)
        val error = ReplyState(phase = ReplyPhase.Error, message = "m", analysis = analysis).ui()
        assertEquals(PanelStatus.Error, error.status)
        assertNull(error.choice)
        assertNull(error.reply)
        assertEquals(CopyUi.Hidden, error.copy)
    }

    @Test
    fun readyNeedsMessageBeforeAnalyze() {
        assertFalse(ReplyState(phase = ReplyPhase.Ready).ui().input!!.canAnalyze)
        assertTrue(ReplyState(phase = ReplyPhase.Ready, message = "hi").ui().input!!.canAnalyze)
        assertTrue(ReplyState(phase = ReplyPhase.Analyzing, message = "hi").ui().reading)
    }

    @Test
    fun noPhaseShowsAReplyWithoutACoordinatorDraft() {
        ReplyPhase.entries.forEach { phase ->
            val reply = ReplyState(phase = phase, analysis = analysis).ui().reply
            assertTrue("$phase", reply == null || reply == ReplyUi.Writing || reply == ReplyUi.Draft(""))
        }
    }
}
