package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln

enum class ArrivalStatus { INACTIVE, CALCULATING, READY, PAUSED, WAITING_FOR_GPS, MATCHING_TRACK,
    OFF_TRACK, REVERSE_DIRECTION, DISCONTINUOUS_TRACK, SEGMENT_END, ARRIVED }

data class ArrivalEstimate(
    val status: ArrivalStatus = ArrivalStatus.INACTIVE,
    val remainingSeconds: Long? = null,
    val arrivalTimeMillis: Long? = null,
    val observedTimeMillis: Long? = null,
    val referenceSpeedMetersPerSecond: Double? = null,
)

private const val HISTORY_MILLIS = 180_000L
private const val WEIGHT_HALF_LIFE_MILLIS = 60_000.0
private const val SAMPLE_INTERVAL_MILLIS = 5_000L
private const val MIN_MEASURED_SECONDS = 30.0
private const val MIN_MEASURED_DISTANCE_METERS = 100.0
private const val STOP_MILLIS = 10_000L
private const val MIN_MOVING_SPEED = 0.8
private const val MAX_PLAUSIBLE_SPEED = 90.0

/** Learns pace from confirmed progress on the GPX; it does not predict traffic or future stops. */
class ArrivalTimeEstimator(track: Track) {
    private data class Fix(val time: Long, val distance: Double, val segment: Int, val generation: Long)
    private data class Interval(val start: Long, val end: Long, val distance: Double)
    private val intervals = ArrayDeque<Interval>()
    private val gapsBeforeSegments: Set<Int>
    private var previous: Fix? = null
    private var sampleAnchor: Fix? = null
    private var stationaryAnchor: Fix? = null
    private var paused = false
    private var lastFixMillis: Long? = null
    private var lastResult = ArrivalEstimate(ArrivalStatus.CALCULATING)

    init {
        val gaps = mutableSetOf<Int>()
        var previousEnd: TrackPoint? = null
        track.segments.forEachIndexed { index, segment ->
            val start = segment.points.firstOrNull()
            val end = previousEnd
            if (start != null && end != null &&
                distanceMeters(end.latitude, end.longitude, start.latitude, start.longitude) > 30.0) gaps += index
            segment.points.lastOrNull()?.let { previousEnd = it }
        }
        gapsBeforeSegments = gaps
    }

    fun reset() {
        intervals.clear()
        clearContinuity()
        lastFixMillis = null
        lastResult = ArrivalEstimate()
    }

    fun unavailable(status: ArrivalStatus, nowMillis: Long): ArrivalEstimate {
        trim(nowMillis)
        clearContinuity()
        return state(status)
    }

    fun update(progress: TrackProgress?, point: LocationPoint?, offTrack: OffTrackState,
        guidance: TrackGuidance, nowMillis: Long, wallTimeMillis: Long): ArrivalEstimate {
        val time = point?.elapsedRealtimeNanos?.div(1_000_000L)
        val accuracy = point?.accuracyMeters
        if (point == null || time == null || time !in 0..nowMillis || nowMillis - time > MAX_OFF_TRACK_FIX_AGE_MILLIS ||
            accuracy == null || !accuracy.isFinite() || accuracy !in 0f..MAX_TRACK_ACCURACY_METERS)
            return unavailable(ArrivalStatus.WAITING_FOR_GPS, nowMillis)
        if (offTrack.status == OffTrackStatus.OFF_TRACK || offTrack.status == OffTrackStatus.CHECKING ||
            progress?.matchStatus == TrackMatchStatus.OFF_TRACK)
            return unavailable(ArrivalStatus.OFF_TRACK, nowMillis)
        if (progress?.matchStatus == TrackMatchStatus.AMBIGUOUS || progress?.matchStatus == TrackMatchStatus.SEARCHING)
            return unavailable(ArrivalStatus.MATCHING_TRACK, nowMillis)
        val distance = progress?.travelledDistanceMeters
        val remaining = progress?.remainingDistanceMeters
        val segment = progress?.matchedSegmentIndex
        if (progress == null || progress.matchStatus != TrackMatchStatus.MATCHED || distance == null ||
            remaining == null || segment == null || !distance.isFinite() || !remaining.isFinite() || remaining < 0)
            return unavailable(ArrivalStatus.WAITING_FOR_GPS, nowMillis)
        if (progress.travelDirection == TrackTravelDirection.BACKWARD)
            return unavailable(ArrivalStatus.REVERSE_DIRECTION, nowMillis)
        if (guidance.status == GuidanceStatus.WAITING_FOR_GPS)
            return unavailable(ArrivalStatus.WAITING_FOR_GPS, nowMillis)
        if (gapsBeforeSegments.any { it > segment })
            return unavailable(ArrivalStatus.DISCONTINUOUS_TRACK, nowMillis)
        if (lastFixMillis?.let { time <= it } == true) return lastResult
        lastFixMillis = time
        if (guidance.status == GuidanceStatus.SEGMENT_END)
            return unavailable(ArrivalStatus.SEGMENT_END, nowMillis)
        if (guidance.status == GuidanceStatus.TRACK_END) {
            clearContinuity()
            lastResult = ArrivalEstimate(ArrivalStatus.ARRIVED, 0, wallTimeMillis, wallTimeMillis)
            return lastResult
        }
        // A projection at the end is not yet an arrival if the endpoint check has not passed.
        if (remaining <= END_DISTANCE_METERS) return unavailable(ArrivalStatus.CALCULATING, nowMillis)
        trim(nowMillis)
        val current = Fix(time, distance, segment, progress.matchGeneration)
        val before = previous
        if (before != null && (time - before.time > MAX_OFF_TRACK_FIX_AGE_MILLIS ||
                before.segment != segment || before.generation != current.generation)) clearContinuity()
        val continuous = previous
        if (continuous != null && abs(distance - continuous.distance) / ((time - continuous.time) / 1000.0) > MAX_PLAUSIBLE_SPEED)
            return unavailable(ArrivalStatus.CALCULATING, nowMillis)

        val stopReference = stationaryAnchor ?: current
        val speed = point.speedMetersPerSecond?.takeIf { it.isFinite() && it in 0f..90f }?.toDouble()
        val moving = if (speed != null) speed >= MIN_MOVING_SPEED else
            abs(distance - stopReference.distance) > maxOf(8.0, minOf(20.0, accuracy.toDouble()))
        if (moving) {
            if (paused) sampleAnchor = current
            stationaryAnchor = current
            paused = false
        } else {
            stationaryAnchor = stopReference
            if (time - stopReference.time >= STOP_MILLIS) {
                // Remove the tentative stopped intervals, including a bucket crossing the stop's start.
                while (intervals.lastOrNull()?.let { it.end > stopReference.time } == true) intervals.removeLast()
                previous = current
                sampleAnchor = null
                paused = true
                return state(ArrivalStatus.PAUSED)
            }
        }
        previous = current
        val anchor = sampleAnchor
        if (anchor == null) sampleAnchor = current
        else if (time - anchor.time >= SAMPLE_INTERVAL_MILLIS) {
            intervals.addLast(Interval(anchor.time, time, distance - anchor.distance))
            sampleAnchor = current
        }
        return estimate(remaining, nowMillis, wallTimeMillis)
    }

    private fun estimate(remaining: Double, now: Long, wall: Long): ArrivalEstimate {
        var seconds = 0.0
        var distance = 0.0
        var weightedSeconds = 0.0
        var weightedDistance = 0.0
        for (interval in intervals) {
            val start = maxOf(interval.start, now - HISTORY_MILLIS)
            val duration = (interval.end - start).coerceAtLeast(0).toDouble()
            val fraction = duration / (interval.end - interval.start)
            val meters = interval.distance * fraction
            val weight = exp(-ln(2.0) * (now - (start + interval.end) / 2.0) / WEIGHT_HALF_LIFE_MILLIS)
            seconds += duration / 1000
            distance += meters
            weightedSeconds += duration / 1000 * weight
            weightedDistance += meters * weight
        }
        if (seconds < MIN_MEASURED_SECONDS || distance < MIN_MEASURED_DISTANCE_METERS || weightedSeconds <= 0)
            return state(ArrivalStatus.CALCULATING)
        val speed = weightedDistance / weightedSeconds
        if (!speed.isFinite() || speed < MIN_MOVING_SPEED || speed > MAX_PLAUSIBLE_SPEED)
            return state(ArrivalStatus.CALCULATING)
        val duration = ceil(remaining / speed).toLong().coerceAtLeast(1)
        if (duration > (Long.MAX_VALUE - wall.coerceAtLeast(0)) / 1000) return state(ArrivalStatus.CALCULATING)
        lastResult = ArrivalEstimate(ArrivalStatus.READY, duration, wall + duration * 1000, wall, speed)
        return lastResult
    }

    private fun trim(now: Long) {
        while (intervals.firstOrNull()?.let { it.end <= now - HISTORY_MILLIS } == true) intervals.removeFirst()
    }

    private fun clearContinuity() {
        previous = null
        sampleAnchor = null
        stationaryAnchor = null
        paused = false
    }

    private fun state(status: ArrivalStatus): ArrivalEstimate = ArrivalEstimate(status).also { lastResult = it }
}
