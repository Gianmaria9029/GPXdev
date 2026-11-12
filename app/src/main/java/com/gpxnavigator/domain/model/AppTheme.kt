package com.gpxnavigator.domain.model

enum class AppTheme(val preferenceValue: String) {
    // Keep the stored identifier so existing installations retain their selected theme.
    SOFT("standard"),
    CYBER("cyber"),
}
