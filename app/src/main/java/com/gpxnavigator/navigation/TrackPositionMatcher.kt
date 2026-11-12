package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import kotlin.math.abs
import kotlin.math.min

enum class TrackMatchStatus { UNAVAILABLE, SEARCHING, MATCHED, AMBIGUOUS, OFF_TRACK }
enum class TrackTravelDirection { UNKNOWN, FORWARD, BACKWARD }

internal data class TrackProjection(
    val distanceFromTrack: Double,
    val distanceAlongTrack: Double,
    val segmentIndex: Int,
    val bearing: Double?,
)

internal data class TrackMatch(
    val status: TrackMatchStatus,
    val projection: TrackProjection? = null,
    val direction: TrackTravelDirection = TrackTravelDirection.UNKNOWN,
    val generation: Long = 0,
)

private const val CONTINUITY_TIMEOUT_NANOS = 15_000_000_000L
private const val DISTINCT_BRANCH_METERS = 40.0
private const val CONFIRMATION_FIXES = 3
private const val CONFIRMATION_NANOS = 2_000_000_000L

/** Matches positions to the GPX itself; no roads, routing data or network are involved. */
internal class TrackPositionMatcher {
    private data class Accepted(val projection: TrackProjection, val location: LocationPoint)
    private data class Pending(val projection: TrackProjection, val location: LocationPoint,
        val firstNanos: Long, val count: Int)
    private var accepted: Accepted? = null
    private var pending: Pending? = null
    private var needsConfirmation = false
    private var lastNanos: Long? = null
    private var lastResult = TrackMatch(TrackMatchStatus.UNAVAILABLE)
    private var direction = TrackTravelDirection.UNKNOWN
    private var directionAnchor: Double? = null
    private var generation = 0L
    private val movement = MovementBearingEstimator()

    fun invalidate() {
        needsConfirmation = needsConfirmation || accepted != null
        pending = null
        movement.reset()
        lastResult = TrackMatch(TrackMatchStatus.UNAVAILABLE, generation = generation)
    }

    fun update(candidates: List<TrackProjection>, location: LocationPoint): TrackMatch {
        val time = location.elapsedRealtimeNanos
        if (time != null && lastNanos?.let { time <= it } == true) return lastResult
        lastNanos = time
        val accuracy = location.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy !in 0f..MAX_TRACK_ACCURACY_METERS) {
            invalidate()
            return lastResult
        }
        val heading = movement.update(location)
        if (candidates.isEmpty()) {
            invalidate()
            return result(TrackMatchStatus.OFF_TRACK)
        }
        val previous = accepted
        val previousTime = previous?.location?.elapsedRealtimeNanos
        val continuous = !needsConfirmation && previous != null && time != null && previousTime != null &&
            time - previousTime in 1..CONTINUITY_TIMEOUT_NANOS
        val bound = previous?.let { travelBound(it.location, location) } ?: 0.0
        val nearestDistance = candidates.minOf { it.distanceFromTrack }
        val local = if (continuous) candidates.filter {
            it.segmentIndex == previous!!.projection.segmentIndex &&
                abs(it.distanceAlongTrack - previous.projection.distanceAlongTrack) <= bound &&
                it.distanceFromTrack <= nearestDistance + maxOf(10.0, accuracy * 1.5)
        } else emptyList()
        val usingContinuity = local.isNotEmpty()
        if (previous != null && !usingContinuity) needsConfirmation = true
        val pool = if (usingContinuity) local else candidates
        fun score(candidate: TrackProjection): Double {
            val scale = maxOf(5.0, accuracy.toDouble())
            var value = candidate.distanceFromTrack / scale
            if (usingContinuity) {
                value += abs(candidate.distanceAlongTrack - previous!!.projection.distanceAlongTrack) /
                    maxOf(15.0, bound / 2)
            }
            if (heading != null && candidate.bearing != null) {
                var difference = abs(bearingDifference(candidate.bearing, heading))
                // Within an established trajectory the user may genuinely walk backwards.
                if (usingContinuity || direction == TrackTravelDirection.BACKWARD)
                    difference = min(difference, 180 - difference)
                value += difference / 45.0
            }
            return value
        }
        val best = pool.minBy { score(it) }
        val bestScore = score(best)
        val alternative = pool.filter {
            it.segmentIndex != best.segmentIndex ||
                abs(it.distanceAlongTrack - best.distanceAlongTrack) > DISTINCT_BRANCH_METERS
        }.minByOrNull { score(it) }
        if (alternative != null && score(alternative) - bestScore < 1.0) {
            pending = null
            needsConfirmation = true
            return result(TrackMatchStatus.AMBIGUOUS)
        }
        if (needsConfirmation) {
            val before = pending
            val consistent = before != null && time != null &&
                before.location.elapsedRealtimeNanos?.let { time - it in 1..CONTINUITY_TIMEOUT_NANOS } == true &&
                before.projection.segmentIndex == best.segmentIndex &&
                abs(before.projection.distanceAlongTrack - best.distanceAlongTrack) <= travelBound(before.location, location)
            val next = Pending(best, location, if (consistent) before!!.firstNanos else time ?: 0L,
                if (consistent) before!!.count + 1 else 1)
            pending = next
            if (time == null || next.count < CONFIRMATION_FIXES || time - next.firstNanos < CONFIRMATION_NANOS)
                return result(TrackMatchStatus.SEARCHING)
            generation++
            direction = TrackTravelDirection.UNKNOWN
            directionAnchor = null
        }
        needsConfirmation = false
        pending = null
        // Untimed positions can be projected, but cannot establish temporal evidence.
        if (time != null) {
            val anchor = directionAnchor
            if (anchor == null) {
                directionAnchor = best.distanceAlongTrack
                if (heading != null && best.bearing != null) {
                    val difference = abs(bearingDifference(best.bearing, heading))
                    direction = when {
                        difference < 45 -> TrackTravelDirection.FORWARD
                        difference > 135 -> TrackTravelDirection.BACKWARD
                        else -> TrackTravelDirection.UNKNOWN
                    }
                }
            } else {
                val displacement = best.distanceAlongTrack - anchor
                if (abs(displacement) >= maxOf(20.0, accuracy * 2.0)) {
                    direction = if (displacement > 0) TrackTravelDirection.FORWARD else TrackTravelDirection.BACKWARD
                    directionAnchor = best.distanceAlongTrack
                }
            }
            accepted = Accepted(best, location)
        }
        lastResult = TrackMatch(TrackMatchStatus.MATCHED, best, direction, generation)
        return lastResult
    }

    private fun result(status: TrackMatchStatus): TrackMatch =
        TrackMatch(status, generation = generation).also { lastResult = it }

    private fun travelBound(previous: LocationPoint, current: LocationPoint): Double {
        val seconds = ((current.elapsedRealtimeNanos ?: 0) - (previous.elapsedRealtimeNanos ?: 0)) /
            1_000_000_000.0
        val uncertainty = ((current.accuracyMeters ?: 0f) + (previous.accuracyMeters ?: 0f))
            .toDouble().coerceIn(10.0, 60.0)
        val displacement = distanceMeters(previous.latitude, previous.longitude, current.latitude, current.longitude)
        val speed = current.speedMetersPerSecond?.takeIf { it.isFinite() && it in 0f..90f }?.toDouble() ?: 0.0
        return min(90.0 * seconds.coerceAtLeast(0.0) + uncertainty,
            maxOf(25.0, displacement * 2 + uncertainty, speed * seconds * 1.5 + uncertainty))
    }
}
