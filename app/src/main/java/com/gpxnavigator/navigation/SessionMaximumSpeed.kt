package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint

/** Session statistic: retain the maximum through signal loss, without using old or imprecise fixes. */
internal class SessionMaximumSpeed(private val startedAtMillis: Long) {
    private var maximum: Float? = null
    private var lastFixMillis: Long? = null

    fun reset() {
        maximum = null
        lastFixMillis = null
    }

    fun update(point: LocationPoint?, nowMillis: Long): Float? {
        val time = point?.elapsedRealtimeNanos?.div(1_000_000L) ?: return maximum
        if (time <= startedAtMillis || time > nowMillis || nowMillis - time > MAX_OFF_TRACK_FIX_AGE_MILLIS ||
            lastFixMillis?.let { time <= it } == true) return maximum
        lastFixMillis = time
        val accuracy = point.accuracyMeters ?: return maximum
        val speed = point.speedMetersPerSecond ?: return maximum
        // Use the same GPS accuracy and plausible road-speed bounds as the arrival estimator.
        if (accuracy.isFinite() && accuracy in 0f..50f && speed.isFinite() && speed in 0f..90f) {
            maximum = maxOf(maximum ?: speed, speed)
        }
        return maximum
    }
}
