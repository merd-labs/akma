package ph.merd.akma.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayGeometryTest {
    @Test fun keyboardReducesScrollableBodyWithoutMovingBottomSheetTwice() {
        val full = overlayPanelMaxHeight(2400, 80, 60)
        val keyboard = overlayPanelMaxHeight(2400, 80, 1100)
        assertEquals(1560, full)
        assertEquals(1220, keyboard)
        assertTrue(keyboard < full)
        assertTrue(keyboard + 80 <= 2400 - 1100)
    }

    @Test fun tallAndLandscapeDisplaysKeepHeightWithinSafeViewport() {
        for (height in listOf(2460, 1080)) {
            val panel = overlayPanelMaxHeight(height, 80, 600)
            assertTrue(panel <= height - 80 - 600)
            assertTrue(panel <= (height * 0.65).toInt())
        }
    }

    @Test fun repeatedKeyboardTogglesRestoreHeightWithoutCompoundingInsets() {
        val initial = overlayPanelMaxHeight(2400, 80, 60)
        repeat(40) {
            assertEquals(1220, overlayPanelMaxHeight(2400, 80, 1100))
            assertEquals(initial, overlayPanelMaxHeight(2400, 80, 60))
        }
    }

    @Test fun degenerateBoundsNeverProduceInvalidMeasureDimensions() {
        assertEquals(1, overlayPanelMaxHeight(0, 0, 0))
        assertEquals(1, overlayPanelMaxHeight(100, 200, 300))
        assertEquals(65, overlayPanelMaxHeight(100, -10, -20))
    }
}
