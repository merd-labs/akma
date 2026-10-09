package ph.merd.akma.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleViewportTest {
    @Test fun landscapeCutoutKeepsDragAndSnapInsideSafeHorizontalEdges() {
        val bounds = BubbleBounds(2400, 1080, 216, 12, 80, 60, leftInset = 120, rightInset = 40)
        assertEquals(BubblePoint(120, 80), bounds.clamp(BubblePoint(-500, -500)))
        assertEquals(BubblePoint(2144, 804), bounds.clamp(BubblePoint(5000, 5000)))
        assertEquals(BubblePoint(132, 100), bounds.snap(BubblePoint(0, 100)))
        assertEquals(BubblePoint(2132, 100), bounds.snap(BubblePoint(5000, 100)))
    }

    @Test fun undersizedViewportKeepsOriginAtZeroInsteadOfOutsideDisplay() {
        val bounds = BubbleBounds(40, 60, 72, 12, 100, 24)
        assertEquals(BubblePoint(0, 0), bounds.snap(BubblePoint(500, 500)))
        assertEquals(BubblePoint(0, 0), bounds.clamp(BubblePoint(-500, -500)))
    }

    @Test fun oversizedMarginDoesNotPushEitherEdgeBeyondAvailableTravel() {
        val bounds = BubbleBounds(300, 1000, 216, 200)
        assertEquals(BubblePoint(42, 100), bounds.snap(BubblePoint(0, 100)))
        assertEquals(BubblePoint(42, 100), bounds.snap(BubblePoint(500, 100)))
    }

    @Test fun rotationReclampsRememberedPositionWithoutAccumulatingOffsets() {
        val portrait = BubbleBounds(1080, 2400, 216, 12, 100, 60)
        val landscape = BubbleBounds(2400, 1080, 216, 12, 80, 60)
        var point = portrait.snap(BubblePoint(5000, 5000))
        repeat(20) {
            point = landscape.snap(landscape.clamp(point))
            assertTrue(point.x in 0..2184)
            assertTrue(point.y in 80..804)
            point = portrait.snap(portrait.clamp(point))
            assertTrue(point.x in 0..864)
            assertTrue(point.y in 100..2124)
        }
    }
}
