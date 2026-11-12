package com.gpxnavigator.navigation

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val EARTH_RADIUS_METERS = 6_371_000.0

/** Signed shortest displacement on the longitude circle, including equivalent +180/-180. */
internal fun longitudeDeltaDegrees(from: Double, to: Double): Double {
    val delta = (to - from) % 360.0
    return when {
        delta > 180.0 -> delta - 360.0
        delta < -180.0 -> delta + 360.0
        else -> delta
    }
}

internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val latitudeDelta = Math.toRadians(lat2 - lat1)
    val longitudeDelta = Math.toRadians(lon2 - lon1)
    val a = sin(latitudeDelta / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(longitudeDelta / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}
