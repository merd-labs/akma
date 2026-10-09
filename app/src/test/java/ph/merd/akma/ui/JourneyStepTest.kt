package ph.merd.akma.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftConfirmation
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone

class JourneyStepTest {
    // Deterministic catalog fallback only; no fixture represents model output.
    private val analysis = ActionCatalog.otherAnalysis()
    private val action = analysis.actions.first()
    private val confirmation = DraftConfirmation(
        id = 1L,
        action = action,
        request = DraftRequest(AnalyzeRequest("message"), action.id, ReplyTone.PROFESSIONAL),
    )

    @Test
    fun idlePhasesShowPaste() {
        listOf(ReplyPhase.ModelUnavailable, ReplyPhase.ModelLoading, ReplyPhase.Ready).forEach {
            assertEquals(JourneyStep.Paste, ReplyState(phase = it).journeyStep())
        }
    }

    @Test
    fun activePhasesMapInJourneyOrder() {
        assertEquals(JourneyStep.Analyze, ReplyState(phase = ReplyPhase.Analyzing).journeyStep())
        assertEquals(JourneyStep.Choose, ReplyState(phase = ReplyPhase.ChoosingAction, analysis = analysis).journeyStep())
        assertEquals(JourneyStep.Generate, ReplyState(phase = ReplyPhase.Drafting).journeyStep())
        assertEquals(JourneyStep.Edit, ReplyState(phase = ReplyPhase.Editing, draft = "d").journeyStep())
        assertEquals(JourneyStep.Copy, ReplyState(phase = ReplyPhase.Copied, draft = "d").journeyStep())
    }

    @Test
    fun pendingConfirmationAlwaysShowsConfirm() {
        val state = ReplyState(phase = ReplyPhase.ChoosingAction, analysis = analysis, pendingConfirmation = confirmation)
        assertEquals(JourneyStep.Confirm, state.journeyStep())
    }

    @Test
    fun errorKeepsStepFromRetainedState() {
        assertEquals(JourneyStep.Paste, ReplyState(phase = ReplyPhase.Error, message = "m").journeyStep())
        assertEquals(JourneyStep.Choose, ReplyState(phase = ReplyPhase.Error, analysis = analysis).journeyStep())
        assertEquals(JourneyStep.Edit, ReplyState(phase = ReplyPhase.Error, analysis = analysis, draft = "d").journeyStep())
    }

    @Test
    fun everyPhaseIsMapped() {
        ReplyPhase.entries.forEach { ReplyState(phase = it).journeyStep() }
    }
}
