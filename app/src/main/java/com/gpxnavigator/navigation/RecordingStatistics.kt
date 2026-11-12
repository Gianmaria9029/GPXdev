package com.gpxnavigator.navigation

import com.gpxnavigator.data.database.RecordingPointEntity

internal const val MAX_RECORDING_SAMPLE_GAP_MILLIS = 15_000L
internal const val MOVING_SPEED_METERS_PER_SECOND = 1f // 3.6 km/h; excludes stationary GPS drift.

internal fun recordingSpeed(speed: Float?): Float? = speed?.takeIf { it.isFinite() && it in 0f..90f }

data class RecordingStatistics(
    val totalDistanceMeters: Double = 0.0,
    val maximumSpeedPoint: RecordingPointEntity? = null,
    val minimumElevationMeters: Double? = null,
    val maximumElevationMeters: Double? = null,
    val elevationDifferenceMeters: Double? = null,
    val movingAverageSpeedMetersPerSecond: Double? = null,
    val startElevationMeters: Double? = null,
    val endElevationMeters: Double? = null,
) {
    companion object {
        fun calculate(samples: List<RecordingPointEntity>): RecordingStatistics {
            val accumulator = RecordingStatisticsAccumulator()
            samples.forEach(accumulator::add)
            return accumulator.snapshot()
        }
    }
}

/** The same incremental calculation feeds the live cockpit and the saved GPX. */
internal class RecordingStatisticsAccumulator {
    private var distance = 0.0
    private var lastTrackPoint: RecordingPointEntity? = null
    private var maximum: RecordingPointEntity? = null
    private var minimumElevation: Double? = null
    private var maximumElevation: Double? = null
    private var previous: RecordingPointEntity? = null
    private var firstElevation: Double? = null
    private var movingMillis = 0L
    private var movingDistance = 0.0

    fun add(sample: RecordingPointEntity) {
        if (previous == null) firstElevation = sample.elevation?.takeIf { it.isFinite() }
        if (sample.isTrackPoint) {
            lastTrackPoint?.takeIf { it.segment == sample.segment }?.let {
                distance += distanceMeters(it.latitude, it.longitude, sample.latitude, sample.longitude)
            }
            lastTrackPoint = sample
        }
        val speed = recordingSpeed(sample.speedMetersPerSecond)
        val peak = maximum
        if (speed != null && (peak == null || speed > peak.speedMetersPerSecond!!)) maximum = sample
        sample.elevation?.takeIf { it.isFinite() }?.let { elevation ->
            minimumElevation = minOf(minimumElevation ?: elevation, elevation)
            maximumElevation = maxOf(maximumElevation ?: elevation, elevation)
        }
        val before = previous
        if (before != null && before.segment == sample.segment) {
            val duration = sample.elapsedMillis - before.elapsedMillis
            val beforeSpeed = recordingSpeed(before.speedMetersPerSecond)
            // Integrate adjacent speeds in time. Never bridge a stop, missing fix or interrupted segment.
            if (duration in 1..MAX_RECORDING_SAMPLE_GAP_MILLIS &&
                beforeSpeed != null && beforeSpeed >= MOVING_SPEED_METERS_PER_SECOND &&
                speed != null && speed >= MOVING_SPEED_METERS_PER_SECOND) {
                movingMillis += duration
                movingDistance += (beforeSpeed.toDouble() + speed) * 0.5 * duration / 1_000.0
            }
        }
        previous = sample
    }

    fun snapshot(): RecordingStatistics {
        val lastElevation = previous?.elevation?.takeIf { it.isFinite() }
        val start = firstElevation
        return RecordingStatistics(distance, maximum, minimumElevation, maximumElevation,
            if (start != null && lastElevation != null) lastElevation - start else null,
            if (movingMillis > 0) movingDistance / (movingMillis / 1_000.0) else null, start, lastElevation)
    }
}
