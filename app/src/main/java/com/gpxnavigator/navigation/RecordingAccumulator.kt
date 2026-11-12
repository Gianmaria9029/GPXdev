package com.gpxnavigator.navigation

import com.gpxnavigator.data.database.RecordingPointEntity
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.ActivityMode
import java.time.Instant
import kotlin.math.min

data class RecordingChunk(val points: List<TrackPoint>)

data class RecordingState(
    val id: String? = null,
    val active: Boolean = false,
    val pending: Boolean = false,
    val chunks: List<RecordingChunk> = emptyList(),
    val pointCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val startedAtMillis: Long = 0,
    val error: String? = null,
    val statistics: RecordingStatistics = RecordingStatistics(),
    val mode: ActivityMode = ActivityMode.FREE_RECORDING,
    val title: String? = null,
    val endedAtMillis: Long? = null,
)

// A small immutable tail changes at each fix; completed chunks are reused by the map.
class RecordingAccumulator {
    private var statistics = RecordingStatisticsAccumulator()
    private var last: RecordingPointEntity? = null
    private var lastSample: RecordingPointEntity? = null
    private var sampleCount = 0
    private var lastObservedMillis: Long? = null
    private var sessionStartedElapsedMillis: Long? = null
    private var breakSegment = true
    var state = RecordingState()
        private set

    fun restore(id: String, startedAtMillis: Long, points: List<RecordingPointEntity>,
        mode: ActivityMode = ActivityMode.FREE_RECORDING, title: String? = null, endedAtMillis: Long? = null) {
        state = RecordingState(id = id, startedAtMillis = startedAtMillis, mode = mode, title = title, endedAtMillis = endedAtMillis)
        statistics = RecordingStatisticsAccumulator()
        last = null
        lastSample = null
        sampleCount = 0
        lastObservedMillis = null
        sessionStartedElapsedMillis = null
        for (point in points) commit(point)
        breakSegment = true // A resumed recording must not connect the interruption with a straight line.
    }

    fun breakSegment() { breakSegment = true }

    fun beginSession(elapsedMillis: Long) {
        // A new monotonic boundary also handles resuming after a device reboot.
        sessionStartedElapsedMillis = elapsedMillis
        lastObservedMillis = null
        breakSegment = true
    }

    fun candidate(id: String, point: LocationPoint?, precise: Boolean, now: Long, wall: Long): RecordingPointEntity? {
        val fix = point?.elapsedRealtimeNanos?.div(1_000_000L) ?: return null
        // Exclude the boundary millisecond too: it may contain a cached pre-start fix.
        if (sessionStartedElapsedMillis?.let { fix <= it } == true) return null
        val accuracy = point.accuracyMeters ?: return null
        if (!precise || !accuracy.isFinite() || accuracy !in 0f..50f || fix !in 0..now || now - fix > 10_000 ||
            !point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0) {
            breakSegment = true
            return null
        }
        val previous = lastSample
        if (previous != null && fix == previous.elapsedMillis && !breakSegment) return null
        val observed = lastObservedMillis
        if (observed != null && fix <= observed) return null
        val separated = breakSegment || previous == null || fix - (observed ?: previous.elapsedMillis) > MAX_RECORDING_SAMPLE_GAP_MILLIS ||
            fix < previous.elapsedMillis
        lastObservedMillis = fix
        if (previous != null && !separated) {
            if (fix <= previous.elapsedMillis) return null
            val distance = distanceMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
            if (distance / ((fix - previous.elapsedMillis) / 1_000.0) > 100.0) {
                breakSegment = true
                return null
            }
        }
        val routePrevious = last
        val isTrackPoint = if (separated || routePrevious == null) true else {
            val distance = distanceMeters(routePrevious.latitude, routePrevious.longitude, point.latitude, point.longitude)
            distance >= maxOf(3.0, min(routePrevious.accuracyMeters, accuracy) * 0.5) &&
                !(point.speedMetersPerSecond?.let { it.isFinite() && it in 0f..0.4f } == true &&
                    distance < maxOf(10.0, accuracy.toDouble()))
        }
        return RecordingPointEntity(id, sampleCount, if (previous == null) 0 else
            previous.segment + if (separated) 1 else 0, point.latitude, point.longitude,
            point.elevationMeters?.takeIf { it.isFinite() }, point.timestampMillis ?: (wall - (now - fix)), fix, accuracy,
            recordingSpeed(point.speedMetersPerSecond), isTrackPoint)
    }

    fun commit(point: RecordingPointEntity) {
        statistics.add(point)
        state = state.copy(statistics = statistics.snapshot())
        sampleCount = point.sequence + 1
        lastSample = point
        breakSegment = false
        // Keep stationary samples for the moving average without adding jitter to the route.
        if (!point.isTrackPoint) return
        val previous = last
        val connected = previous != null && previous.segment == point.segment
        val distance = if (connected) distanceMeters(previous!!.latitude, previous.longitude, point.latitude, point.longitude) else 0.0
        val trackPoint = TrackPoint(point.latitude, point.longitude, point.elevation, Instant.ofEpochMilli(point.timestampMillis))
        val chunks = state.chunks
        val tail = chunks.lastOrNull()
        val updated = when {
            !connected || tail == null -> chunks + RecordingChunk(listOf(trackPoint))
            tail.points.size >= 128 -> chunks + RecordingChunk(listOf(tail.points.last(), trackPoint))
            else -> chunks.dropLast(1) + RecordingChunk(tail.points + trackPoint)
        }
        state = state.copy(chunks = updated, pointCount = state.pointCount + 1,
            distanceMeters = state.distanceMeters + distance)
        last = point
        breakSegment = false
    }
}
