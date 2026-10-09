package ph.merd.akma.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AkmaScreenTest {
    @Test
    fun firstLaunchFollowsFigmaOrder() {
        assertEquals(AkmaScreen.Landing, startScreen(onboarded = false, setupDone = false))
        assertEquals(AkmaScreen.Landing, startScreen(onboarded = false, setupDone = true))
        assertEquals(AkmaScreen.Setup, startScreen(onboarded = true, setupDone = false))
        assertEquals(AkmaScreen.Home, startScreen(onboarded = true, setupDone = true))
    }

    @Test
    fun backFromReplyReturnsToTheScreenThatOpenedIt() {
        assertEquals(AkmaScreen.Home, AkmaScreen.Reply.back(setupDone = true))
        assertEquals(AkmaScreen.Setup, AkmaScreen.Reply.back(setupDone = false))
        listOf(AkmaScreen.Landing, AkmaScreen.Setup, AkmaScreen.Home).forEach { assertNull(it.back(setupDone = true)) }
    }
}
