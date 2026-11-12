package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.navigation.bearingDifference
import com.gpxnavigator.navigation.longitudeDeltaDegrees
import com.gpxnavigator.navigation.normalizeBearing

private const val DEFAULT_FIX_INTERVAL_MS = 1_000L
private const val MIN_ANIMATION_MS = 250L
private const val MAX_ANIMATION_MS = 1_500L
private const val MAX_CONTINUOUS_FIX_GAP_MS = 5_000L
private const val MAX_ANIMATED_DISTANCE_METERS = 1_000.0

/** Display-only interpolation. These intermediate points never enter navigation or recording. */
internal class LocationPresentation {
    private var from: LocationPoint? = null
    var target: LocationPoint? = null
        private set
    private var startedAt = 0L
    private var receivedAt: Long? = null
    private var duration = 0L

    fun update(point: LocationPoint?, now: Long, animate: Boolean = true): Boolean {
        val previous = target
        if (point == previous) return false
        val previousTime = previous?.elapsedRealtimeNanos
        val time = point?.elapsedRealtimeNanos
        if (time != null && previousTime != null && time <= previousTime) return false
        val current = position(now)
        val receiptInterval = receivedAt?.let { now - it }
        val fixInterval = if (time != null && previousTime != null) (time - previousTime) / 1_000_000L
            else receiptInterval ?: DEFAULT_FIX_INTERVAL_MS
        val continuous = animate && point != null && current != null && fixInterval in 1..MAX_CONTINUOUS_FIX_GAP_MS &&
            (receiptInterval == null || receiptInterval in 0..MAX_CONTINUOUS_FIX_GAP_MS) &&
            cameraDistanceMeters(current.latitude, current.longitude, point.latitude, point.longitude) <=
                MAX_ANIMATED_DISTANCE_METERS
        from = current
        target = point
        startedAt = now
        receivedAt = now
        duration = if (continuous) fixInterval.coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS) else 0L
        return true
    }

    fun remainingMillis(now: Long): Long = (duration - (now - startedAt)).coerceAtLeast(0L)

    fun finish() { from = target; duration = 0L }

    fun position(now: Long): LocationPoint? {
        val end = target ?: return null
        val start = from ?: return end
        if (duration == 0L || remainingMillis(now) == 0L) return end
        val fraction = ((now - startedAt).toDouble() / duration).coerceIn(0.0, 1.0)
        val startBearing = start.bearingDegrees?.takeIf { it.isFinite() }
        val endBearing = end.bearingDegrees?.takeIf { it.isFinite() }
        return end.copy(
            latitude = interpolate(start.latitude, end.latitude, fraction),
            longitude = interpolateLongitude(start.longitude, end.longitude, fraction),
            bearingDegrees = if (startBearing != null && endBearing != null)
                interpolateBearing(startBearing.toDouble(), endBearing.toDouble(), fraction).toFloat() else endBearing,
        )
    }
}

internal fun interpolate(from: Double, to: Double, fraction: Double): Double = from + (to - from) * fraction

internal fun interpolateLongitude(from: Double, to: Double, fraction: Double): Double =
    ((from + longitudeDeltaDegrees(from, to) * fraction + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

internal fun interpolateBearing(from: Double, to: Double, fraction: Double): Double =
    normalizeBearing(from + bearingDifference(from, to) * fraction)
