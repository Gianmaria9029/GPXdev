package com.gpxnavigator.ui.map

internal const val DEFAULT_FOLLOW_ZOOM = 16.0

internal class MapZoomPolicy(initialZoom: Double? = null) {
    var selectedZoom: Double? = initialZoom?.takeIf { it.isFinite() && it in 0.0..24.0 }
        private set

    fun restore(zoom: Double?) {
        selectedZoom = zoom?.takeIf { it.isFinite() && it in 0.0..24.0 }
    }

    fun select(zoom: Double, minimum: Double, maximum: Double): Double {
        val selected = (zoom.takeIf { it.isFinite() } ?: selectedZoom ?: DEFAULT_FOLLOW_ZOOM)
            .coerceIn(minimum, maximum)
        selectedZoom = selected
        return selected
    }

    fun followZoom(minimum: Double, maximum: Double): Double =
        (selectedZoom ?: DEFAULT_FOLLOW_ZOOM).coerceIn(minimum, maximum)
}
