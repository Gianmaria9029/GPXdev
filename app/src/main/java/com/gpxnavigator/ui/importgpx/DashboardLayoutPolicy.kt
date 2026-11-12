package com.gpxnavigator.ui.importgpx

internal data class DashboardLayout(val twoPanes: Boolean)

internal fun dashboardLayout(widthDp: Float, heightDp: Float, fontScale: Float): DashboardLayout {
    val scale = fontScale.coerceAtLeast(1f)
    // Reserve readable introduction/action columns, a 32 dp gutter and 20 dp outer margins.
    return DashboardLayout(widthDp > heightDp && widthDp >= 72f + 640f * scale && heightDp >= 300f * scale)
}
