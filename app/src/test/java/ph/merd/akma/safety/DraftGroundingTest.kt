package ph.merd.akma.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases include outputs actually produced by the pinned Qwen model on the Pova 2 and on a host CPU (synthetic messages). */
class DraftGroundingTest {
    private val hr = "Hello! We would like to invite you for an interview this Friday at 2 PM. Are you available?"

    private fun concerns(draft: String, source: String = hr, action: String? = null, instruction: String = "") =
        DraftGrounding.check(draft, source, instruction, action).concerns

    @Test fun observedPova2RescheduleDraftInventsUnavailability() {
        val c = concerns("Thank you for the invitation, but unfortunately, I am not available on Friday at 2 PM. Could we perhaps reschedule for a different day?", action = "reschedule")
        assertTrue(GroundingConcern.UNAVAILABILITY_CLAIM in c)
        assertTrue(GroundingConcern.UNGROUNDED_DETAIL !in c) // Friday and 2 PM come from the sender's message
    }

    @Test fun observedHostRescheduleDraftIsFlagged() {
        assertTrue(GroundingConcern.UNAVAILABILITY_CLAIM in concerns("I'm sorry, but I'm not available on Friday at 2 PM. Could we reschedule for another day?", action = "reschedule"))
    }

    @Test fun cleanQuestionDraftHasNoConcern() {
        assertEquals(emptySet<GroundingConcern>(), concerns("Could you please provide more details about the interview, such as the location and any specific requirements?", action = "clarify"))
    }

    @Test fun inventedTimeDateAndAmountAreUngrounded() {
        val c = DraftGrounding.check("I can do Monday at 10 AM and will pay 5,000 pesos by March 3.", hr, "", "reschedule")
        assertTrue(GroundingConcern.UNGROUNDED_DETAIL in c.concerns)
        assertTrue(c.examples.any { it.contains("monday", true) })
        assertTrue(c.examples.any { it.contains("10", true) })
        assertTrue(c.examples.any { it.contains("5,000") || it.contains("5000") })
    }

    @Test fun timeSpellingVariantsMatchTheSenderMessage() {
        assertEquals(emptySet<GroundingConcern>(), concerns("Could we move the 2pm interview on Friday?", action = "reschedule"))
        assertEquals(emptySet<GroundingConcern>(), concerns("Could we move the 2 P.M. interview on friday?", action = "reschedule"))
    }

    @Test fun detailsFromTheUsersOwnInstructionAreGrounded() {
        assertEquals(emptySet<GroundingConcern>(), concerns("How about Monday at 10 AM instead?", action = "suggest_time", instruction = "Offer Monday at 10 AM"))
    }

    @Test fun commitmentIsAConcernExceptForCommitActions() {
        val draft = "I will attend and I confirm."
        assertTrue(GroundingConcern.COMMITMENT_PHRASE in concerns(draft, action = "clarify"))
        assertTrue(GroundingConcern.COMMITMENT_PHRASE in concerns(draft, action = null))
        assertTrue(GroundingConcern.COMMITMENT_PHRASE !in concerns(draft, action = "accept"))
        assertTrue(GroundingConcern.COMMITMENT_PHRASE !in concerns(draft, action = "confirm"))
    }

    @Test fun declineMayStateUnavailabilityButStillCannotInventDetails() {
        val c = concerns("I'm afraid I can't make it this Friday at 4 PM.", action = "decline")
        assertTrue(GroundingConcern.UNAVAILABILITY_CLAIM !in c)
        assertTrue(GroundingConcern.UNGROUNDED_DETAIL in c) // 4 PM was never mentioned
    }

    @Test fun refundAndPaymentPromisesToAComplaintAreFlagged() {
        val complaint = "My order arrived 3 days late. I want a full refund now or I will report you."
        val c = DraftGrounding.check("We apologise and will give you a full refund of 1,500 pesos tomorrow.", complaint, "", "apologize").concerns
        assertTrue(GroundingConcern.COMMITMENT_PHRASE in c)
        assertTrue(GroundingConcern.UNGROUNDED_DETAIL in c)
    }

    @Test fun numbersThatAppearInTheSourceAreNotFlagged() {
        val msg = "Please send 2000 pesos to 0917-555-0100 today."
        assertEquals(emptySet<GroundingConcern>(), DraftGrounding.check("Could you explain why 2000 pesos is needed for 0917-555-0100?", msg, "", "ask_to_clarify").concerns)
    }

    @Test fun filipinoDraftIsHandledWithoutFalsePositives() {
        val msg = "Hi po, pwede po ba kayo sa interview bukas ng 10 AM? Salamat po."
        assertEquals(emptySet<GroundingConcern>(), DraftGrounding.check("Salamat po. Pwede po bang malaman ang lokasyon ng interview bukas ng 10 AM?", msg, "", "clarify").concerns)
    }

    @Test fun reportNeverEchoesMoreThanShortExamples() {
        val long = "Monday ".repeat(200)
        val report = DraftGrounding.check(long, hr, "", null)
        assertTrue(report.examples.all { it.length <= 60 } && report.examples.size <= 5)
    }

    @Test fun emptyDraftIsClear() {
        assertTrue(DraftGrounding.check("", hr).isClear)
    }

    // Host-CPU run of the pinned model with the app's prompts (synthetic complaint / deadline messages).
    private val complaint = "My order arrived 3 days late. I want a full refund now or I will report you."
    private val deadline = "Can you finish the report tonight? The client is waiting for the update."

    @Test fun observedModelPromisesRefundToAComplaint() {
        val apologize = DraftGrounding.check("I'm sorry to hear about your experience. I'll make sure to expedite your order and provide a full refund as soon as possible.", complaint, "", "apologize")
        val explain = DraftGrounding.check("I understand your frustration. We will look into the issue and provide a full refund as soon as possible.", complaint, "", "explain")
        assertTrue(GroundingConcern.COMMITMENT_PHRASE in apologize.concerns)
        assertTrue(GroundingConcern.COMMITMENT_PHRASE in explain.concerns)
    }

    @Test fun observedModelCommitsToTheDeadlineAndInventsAMeeting() {
        val update = DraftGrounding.check("Sure, I can finish the report tonight. The client will appreciate the quick turnaround.", deadline, "", "give_update")
        assertTrue(GroundingConcern.COMMITMENT_PHRASE in update.concerns)
        val moreTime = DraftGrounding.check("I understand, but I need to finish the report tonight to meet the client's deadline. Can we schedule a meeting tomorrow to discuss the report?", deadline, "", "request_more_time")
        assertTrue(GroundingConcern.UNGROUNDED_DETAIL in moreTime.concerns) // "tomorrow" was never mentioned
    }

    @Test fun observedGoodAcceptDraftIsNotOverFlagged() {
        val accept = DraftGrounding.check("Thank you for the invitation, I will check my schedule and get back to you.", hr, "", "accept")
        assertEquals(emptySet<GroundingConcern>(), accept.concerns)
    }
}
