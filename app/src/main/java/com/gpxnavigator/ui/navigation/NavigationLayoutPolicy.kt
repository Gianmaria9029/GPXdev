package com.gpxnavigator.ui.navigation

internal data class NavigationLayout(val landscape: Boolean, val sidebarWidthDp: Float = 0f)

internal fun navigationLayout(widthDp: Float, heightDp: Float): NavigationLayout {
    // Keep a readable sidebar and room for the map with its single landscape Follow control.
    val landscape = widthDp > heightDp && widthDp >= 400f
    return NavigationLayout(landscape,
        if (landscape) (widthDp * 0.26f).coerceIn(160f, 180f) else 0f)
}

internal fun landscapeManeuverSize(mapWidthDp: Float, mapHeightDp: Float): Float {
    // Leave a lane beside the cue and room above it for the controls plus a secondary turn.
    return minOf(192f, (mapWidthDp - 112f).coerceAtLeast(96f),
        (mapHeightDp - 120f).coerceAtLeast(96f))
}
