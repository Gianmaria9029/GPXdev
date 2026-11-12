package com.gpxnavigator.ui.map

data class ViewportPadding(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
)

internal data class MapViewport(
    val width: Int = 0,
    val height: Int = 0,
    val overlays: ViewportPadding = ViewportPadding(),
) {
    fun cameraPadding(courseUp: Boolean = false, margin: Double = 0.0): ViewportPadding {
        fun valid(value: Double) = value.takeIf { it.isFinite() && it > 0 } ?: 0.0
        fun boundPair(first: Double, second: Double, dimension: Int): Pair<Double, Double> {
            val available = (dimension - minOf(96, dimension.coerceAtLeast(0))).coerceAtLeast(0).toDouble()
            val total = first + second
            val scale = if (total > available) available / total else 1.0
            return first * scale to second * scale
        }
        val extra = valid(margin)
        val (left, right) = boundPair(valid(overlays.left) + extra, valid(overlays.right) + extra, width)
        val (top, bottom) = boundPair(valid(overlays.top) + extra, valid(overlays.bottom) + extra, height)
        val freeHeight = (height - top - bottom).coerceAtLeast(0.0)
        // Keep the position at 62% of the unobstructed area while leaving a usable viewport.
        val lookAhead = if (courseUp) minOf(freeHeight * 0.24,
            (freeHeight - minOf(96, height.coerceAtLeast(0))).coerceAtLeast(0.0)) else 0.0
        return ViewportPadding(left, top + lookAhead, right, bottom)
    }
}
