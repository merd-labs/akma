package ph.merd.akma.overlay

/** Bound the accepted bottom sheet without changing WindowManager's IME positioning.
 * Bottom is the union of IME/navigation/cutout insets, not their sum. Height is unresized.
 */
internal fun overlayPanelMaxHeight(height: Int, insetTop: Int, insetBottom: Int): Int {
    val fullHeight = height.coerceAtLeast(1)
    val available = (fullHeight - insetTop.coerceAtLeast(0) - insetBottom.coerceAtLeast(0)).coerceAtLeast(1)
    return minOf((fullHeight * 0.92).toInt().coerceAtLeast(1), available)
}
