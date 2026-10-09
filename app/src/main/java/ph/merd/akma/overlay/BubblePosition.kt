package ph.merd.akma.overlay

import kotlin.math.hypot

/** Window position of the floating bubble, in pixels from the top-left of the screen. */
internal data class BubblePoint(val x: Int, val y: Int)

/**
 * Pure geometry for the movable bubble. [size] is the bubble window size, [margin] the gap kept to
 * the screen edge, and the insets keep it clear of the status and navigation bars.
 */
internal class BubbleBounds(
    private val screenW: Int,
    private val screenH: Int,
    private val size: Int,
    private val margin: Int,
    private val topInset: Int = 0,
    private val bottomInset: Int = 0,
) {
    private val minY get() = topInset
    private val maxY get() = (screenH - bottomInset - size).coerceAtLeast(minY)
    private val leftX get() = margin
    private val rightX get() = (screenW - size - margin).coerceAtLeast(leftX)

    /** Keeps the whole bubble on screen while it is dragged. */
    fun clamp(point: BubblePoint) = BubblePoint(
        point.x.coerceIn(0, (screenW - size).coerceAtLeast(0)),
        point.y.coerceIn(minY, maxY),
    )

    /** Where the bubble rests after a drag: the nearer side edge, at the dropped height. */
    fun snap(point: BubblePoint): BubblePoint {
        val centre = point.x + size / 2
        return BubblePoint(if (centre < screenW / 2) leftX else rightX, point.y.coerceIn(minY, maxY))
    }

    /** First position: right edge, a little below centre (Figma y 520 of 800). */
    fun initial(offsetBelowCentre: Int) = snap(BubblePoint(rightX, screenH / 2 - size / 2 + offsetBelowCentre))
}

/** Movement within the system touch slop is a tap, so a slightly shaky tap still opens the panel. */
internal fun isBubbleDrag(dx: Float, dy: Float, touchSlop: Int): Boolean = hypot(dx, dy) > touchSlop
