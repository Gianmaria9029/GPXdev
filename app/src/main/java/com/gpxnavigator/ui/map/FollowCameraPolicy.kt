package com.gpxnavigator.ui.map

import com.gpxnavigator.navigation.distanceMeters
import kotlin.math.max
import kotlin.math.min

internal enum class CameraMotion { STAY, ANIMATE, JUMP }

private const val MIN_JITTER_METERS = 8.0
private const val MAX_JITTER_METERS = 30.0
private const val JUMP_DISTANCE_METERS = 1_000.0
private const val MOVING_JITTER_METERS = 0.5
private const val MIN_MOVING_SPEED_METERS_PER_SECOND = 1.2f
private const val MAX_MOVING_ACCURACY_METERS = 20f

internal fun followCameraMotion(distanceMeters: Double, accuracyMeters: Float?, forced: Boolean,
    speedMetersPerSecond: Float? = null): CameraMotion {
    val moving = accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters in 0f..MAX_MOVING_ACCURACY_METERS &&
        speedMetersPerSecond != null && speedMetersPerSecond.isFinite() &&
        speedMetersPerSecond in MIN_MOVING_SPEED_METERS_PER_SECOND..90f
    val jitterThreshold = if (moving) MOVING_JITTER_METERS else max(MIN_JITTER_METERS,
        min(MAX_JITTER_METERS, (accuracyMeters?.takeIf { it.isFinite() && it >= 0f } ?: 0f).toDouble() / 2))
    if (!forced && distanceMeters < jitterThreshold) return CameraMotion.STAY
    return if (distanceMeters > JUMP_DISTANCE_METERS) CameraMotion.JUMP else CameraMotion.ANIMATE
}

internal fun cameraDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    return distanceMeters(lat1, lon1, lat2, lon2)
}
