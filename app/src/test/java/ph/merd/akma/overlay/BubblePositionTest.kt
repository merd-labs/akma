package ph.merd.akma.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePositionTest {
    // 1080 x 2400 screen, 216px bubble, 12px margin, 100px status bar, 60px navigation bar.
    private val bounds = BubbleBounds(screenW = 1080, screenH = 2400, size = 216, margin = 12, topInset = 100, bottomInset = 60)

    @Test
    fun releaseSnapsToTheNearerSideEdge() {
        assertEquals(BubblePoint(12, 900), bounds.snap(BubblePoint(300, 900)))
        assertEquals(BubblePoint(1080 - 216 - 12, 900), bounds.snap(BubblePoint(500, 900)))
    }

    @Test
    fun bubbleStaysClearOfTheSystemBars() {
        assertEquals(100, bounds.snap(BubblePoint(0, -50)).y)
        assertEquals(2400 - 60 - 216, bounds.snap(BubblePoint(0, 5000)).y)
    }

    @Test
    fun draggingOffScreenIsClamped() {
        assertEquals(BubblePoint(0, 100), bounds.clamp(BubblePoint(-400, -400)))
        assertEquals(BubblePoint(1080 - 216, 2400 - 60 - 216), bounds.clamp(BubblePoint(5000, 5000)))
    }

    @Test
    fun firstPositionIsTheRightEdgeBelowCentre() {
        assertEquals(BubblePoint(1080 - 216 - 12, 1200 - 108 + 400), bounds.initial(400))
    }

    @Test
    fun smallMovementIsATapNotADrag() {
        assertFalse(isBubbleDrag(3f, 4f, 24))
        assertTrue(isBubbleDrag(20f, 20f, 24))
    }
}
