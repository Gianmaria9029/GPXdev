package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint

internal const val DEFAULT_GUIDANCE_ANNOUNCE_DISTANCE_METERS = 200.0
private const val ANTICIPATION_SECONDS = 12.0
private const val MIN_ANNOUNCE_DISTANCE_METERS = 80.0
private const val MAX_ANNOUNCE_DISTANCE_METERS = 350.0
private const val MAX_SPEED_ACCURACY_METERS = 20f
private const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 90f
private const val SPEED_WINDOW_NANOS = 5_000_000_000L
private const val MAX_SPEED_SAMPLES = 3

/** The controller checks fix freshness; only distinct, timed GPS samples enter this filter. */
internal class GuidanceAnticipation {
    private data class Sample(val nanos: Long, val speed: Double)
    private val samples = ArrayDeque<Sample>()
    private var lastNanos: Long? = null
    private var distance = DEFAULT_GUIDANCE_ANNOUNCE_DISTANCE_METERS

    fun reset() {
        samples.clear()
        lastNanos = null
        distance = DEFAULT_GUIDANCE_ANNOUNCE_DISTANCE_METERS
    }

    fun update(point: LocationPoint): Double {
        val nanos = point.elapsedRealtimeNanos
        if (nanos == null || nanos < 0) {
            reset()
            return distance
        }
        if (lastNanos?.let { nanos <= it } == true) return distance
        lastNanos = nanos
        val speed = point.speedMetersPerSecond
        val accuracy = point.accuracyMeters
        if (speed == null || !speed.isFinite() || speed !in 0f..MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND ||
            accuracy == null || !accuracy.isFinite() || accuracy !in 0f..MAX_SPEED_ACCURACY_METERS) {
            samples.clear()
            distance = DEFAULT_GUIDANCE_ANNOUNCE_DISTANCE_METERS
            return distance
        }
        while (samples.firstOrNull()?.let { nanos - it.nanos > SPEED_WINDOW_NANOS } == true)
            samples.removeFirst()
        samples.addLast(Sample(nanos, speed.toDouble()))
        while (samples.size > MAX_SPEED_SAMPLES) samples.removeFirst()
        // A median rejects an isolated spike; with two samples use the lower one until confirmed.
        val speeds = samples.map { it.speed }.sorted()
        val stableSpeed = speeds[(speeds.size - 1) / 2]
        distance = (stableSpeed * ANTICIPATION_SECONDS)
            .coerceIn(MIN_ANNOUNCE_DISTANCE_METERS, MAX_ANNOUNCE_DISTANCE_METERS)
        return distance
    }
}
