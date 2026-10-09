package ph.merd.akma.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayGeometryTest {
    @Test fun keyboardReducesScrollablePanelWithoutHidingCloseAboveIt() {
        val full = overlayPanelGeometry(1080, 2400, 0, 80, 0, 60, 1020, 48)
        val keyboard = overlayPanelGeometry(1080, 2400, 0, 80, 0, 1100, 1020, 48)
        assertEquals(984, full.width)
        assertEquals(128, keyboard.y)
        assertTrue(keyboard.maxHeight < full.maxHeight)
        assertTrue(keyboard.y + keyboard.maxHeight <= 2400 - 1100 - 48)
        assertEquals(full.width, keyboard.width)
    }

    @Test fun tallAndLandscapeDisplaysKeepPanelInsideSafeBounds() {
        for ((width, height) in listOf(1080 to 2460, 2400 to 1080)) {
            val panel = overlayPanelGeometry(width, height, 100, 80, 30, 60, 1020, 48)
            assertTrue(panel.x >= 100 + 48)
            assertTrue(panel.x + panel.width <= width - 30 - 48)
            assertTrue(panel.y + panel.maxHeight <= height - 60 - 48)
            assertTrue(panel.maxHeight <= (height * 0.65).toInt())
        }
    }

    @Test fun repeatedKeyboardTogglesRestoreIdenticalGeometryWithoutCompoundingInsets() {
        val initial = overlayPanelGeometry(1080, 2400, 0, 80, 0, 60, 1020, 48)
        repeat(40) {
            val keyboard = overlayPanelGeometry(1080, 2400, 0, 80, 0, 1100, 1020, 48)
            assertEquals(1124, keyboard.maxHeight)
            assertEquals(initial, overlayPanelGeometry(1080, 2400, 0, 80, 0, 60, 1020, 48))
        }
    }

    @Test fun degenerateBoundsNeverProduceInvalidWindowDimensions() {
        val panel = overlayPanelGeometry(0, 0, 0, 0, 0, 0, 1020, 48)
        assertEquals(1, panel.width)
        assertEquals(1, panel.maxHeight)
    }
}
