package com.gpxnavigator.domain.model

enum class ScreenOrientation(val preferenceValue: String) {
    AUTOMATIC("automatic"), PORTRAIT("portrait"), LANDSCAPE("landscape"),
}

data class DisplaySettings(
    val orientation: ScreenOrientation = ScreenOrientation.AUTOMATIC,
)
