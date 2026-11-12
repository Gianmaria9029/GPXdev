package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal fun normalizeBearing(degrees: Double): Double = (degrees % 360 + 360) % 360
internal fun bearingDifference(from: Double, to: Double): Double = (to - from + 540) % 360 - 180

internal fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val first = Math.toRadians(lat1)
    val second = Math.toRadians(lat2)
    val longitude = Math.toRadians(lon2 - lon1)
    return normalizeBearing(Math.toDegrees(atan2(sin(longitude) * cos(second),
        cos(first) * sin(second) - sin(first) * cos(second) * cos(longitude))))
}

/** Returns fresh evidence of movement. Holding/smoothing an old heading belongs to the camera. */
internal class MovementBearingEstimator {
    private var anchor: LocationPoint? = null
    private var lastNanos: Long? = null

    fun reset() { anchor = null; lastNanos = null }

    fun update(point: LocationPoint): Double? {
        val time = point.elapsedRealtimeNanos
        if (time != null && lastNanos?.let { time <= it } == true) return null
        lastNanos = time
        val accuracy = point.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy !in 0f..35f) {
            anchor = null
            return null
        }
        val speed = point.speedMetersPerSecond?.takeIf { it.isFinite() && it in 0f..90f }
        if (speed != null && speed < 0.5f) {
            anchor = point
            return null
        }
        val bearing = point.bearingDegrees?.takeIf { it.isFinite() }
        val bearingAccuracy = point.bearingAccuracyDegrees
        if (bearing != null && speed != null && speed >= 1.2f &&
            (bearingAccuracy == null || (bearingAccuracy.isFinite() && bearingAccuracy in 0f..35f))) {
            anchor = point
            return normalizeBearing(bearing.toDouble())
        }
        val previous = anchor
        val previousTime = previous?.elapsedRealtimeNanos
        if (previous == null || time == null || previousTime == null || time - previousTime > 30_000_000_000L) {
            anchor = point
            return null
        }
        val seconds = (time - previousTime) / 1_000_000_000.0
        val distance = distanceMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
        val uncertainty = maxOf(8.0, (accuracy + (previous.accuracyMeters ?: accuracy)).toDouble())
        if (seconds <= 0 || distance < uncertainty) return null
        anchor = point
        if (distance / seconds !in 0.5..90.0) return null
        return bearingDegrees(previous.latitude, previous.longitude, point.latitude, point.longitude)
    }
}
