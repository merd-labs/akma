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
    private val leftInset: Int = 0,
    private val rightInset: Int = 0,
) {
    private val horizontalTravel = (screenW - size).coerceAtLeast(0)
    private val verticalTravel = (screenH - size).coerceAtLeast(0)
    private val minX = leftInset.coerceIn(0, horizontalTravel)
    private val maxX = (screenW - rightInset - size).coerceIn(minX, horizontalTravel)
    private val minY = topInset.coerceIn(0, verticalTravel)
    private val maxY = (screenH - bottomInset - size).coerceIn(minY, verticalTravel)
    private val edgeMargin = margin.coerceIn(0, (maxX - minX) / 2)
    private val leftX = minX + edgeMargin
    private val rightX = maxX - edgeMargin

    /** Keeps the whole bubble on screen while it is dragged. */
    fun clamp(point: BubblePoint) = BubblePoint(
        point.x.coerceIn(minX, maxX),
        point.y.coerceIn(minY, maxY),
    )

    /** Where the bubble rests after a drag: the nearer side edge, at the dropped height. */
    fun snap(point: BubblePoint): BubblePoint {
        val safe = clamp(point)
        val midpoint = minX + (maxX - minX) / 2
        return BubblePoint(if (safe.x < midpoint) leftX else rightX, safe.y)
    }

    /** First position: right edge, a little below centre (Figma y 520 of 800). */
    fun initial(offsetBelowCentre: Int) = snap(BubblePoint(rightX, screenH / 2 - size / 2 + offsetBelowCentre))
}

/** Movement within the system touch slop is a tap, so a slightly shaky tap still opens the panel. */
internal fun isBubbleDrag(dx: Float, dy: Float, touchSlop: Int): Boolean = hypot(dx, dy) > touchSlop
