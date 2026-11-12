package com.gpxnavigator.navigation

internal const val MAX_TRACK_ACCURACY_METERS = 50f

// Matching and the off-track alert must agree on the usable corridor around the GPX.
internal fun trackToleranceMeters(accuracyMeters: Float): Double =
    maxOf(40.0, accuracyMeters.toDouble() * 2)
