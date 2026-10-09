package ph.merd.akma.overlay

internal data class OverlayPanelGeometry(val width: Int, val x: Int, val y: Int, val maxHeight: Int)

/** Insets are unions: keyboard and navigation-bar bottoms must not be added together. */
internal fun overlayPanelGeometry(
    width: Int,
    height: Int,
    insetLeft: Int,
    insetTop: Int,
    insetRight: Int,
    insetBottom: Int,
    desiredWidth: Int,
    margin: Int,
): OverlayPanelGeometry {
    val left = insetLeft.coerceAtLeast(0)
    val right = insetRight.coerceAtLeast(0)
    val gap = margin.coerceAtLeast(0)
    val availableWidth = (width - left - right - 2 * gap).coerceAtLeast(1)
    val panelWidth = minOf(desiredWidth.coerceAtLeast(1), availableWidth)
    val top = insetTop.coerceAtLeast(0) + gap
    val availableHeight = (height - top - insetBottom.coerceAtLeast(0) - gap).coerceAtLeast(1)
    return OverlayPanelGeometry(
        width = panelWidth,
        x = left + gap + (availableWidth - panelWidth) / 2,
        y = top,
        maxHeight = minOf((height.coerceAtLeast(1) * 0.65).toInt().coerceAtLeast(1), availableHeight),
    )
}
