package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Distances are measured along the original GPX, even after simplifying its geometry.
private const val SIMPLIFICATION_METERS = 5.0
private const val MIN_POINT_SPACING_METERS = 3.0
private const val MIN_CORNER_DEGREES = 8.0
private const val STRAIGHT_LIMIT_DEGREES = 25.0
private const val TURN_LIMIT_DEGREES = 60.0
private const val HAIRPIN_LIMIT_DEGREES = 150.0
private const val MERGE_GAP_METERS = 45.0
private const val MAX_CURVE_SPAN_METERS = 120.0
private const val SECOND_MANEUVER_MAX_GAP_METERS = 100.0
private const val PASS_MARGIN_METERS = 10.0
internal const val END_DISTANCE_METERS = 10.0
private const val MAX_ARRIVAL_ACCURACY_METERS = 10f

enum class Maneuver { STRAIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, LEFT, RIGHT, HAIRPIN_LEFT, HAIRPIN_RIGHT }
enum class GuidanceStatus { WAITING_FOR_GPS, ACTIVE, CHECKING_TRACK, MATCHING_TRACK, REVERSE_DIRECTION,
    OFF_TRACK, SEGMENT_END, TRACK_END }

data class SecondaryManeuver(val maneuver: Maneuver, val distanceAfterMeters: Double)

data class TrackGuidance(
    val status: GuidanceStatus = GuidanceStatus.WAITING_FOR_GPS,
    val maneuver: Maneuver? = null,
    val distanceMeters: Double? = null,
    val secondManeuver: SecondaryManeuver? = null,
    val announcementDistanceMeters: Double? = null,
)

internal data class TrackManeuver(
    val distanceMeters: Double,
    val endDistanceMeters: Double,
    val angleDegrees: Double,
    val maneuver: Maneuver,
)

class TrackGuidanceCalculator(track: Track) {
    private data class Vertex(val point: TrackPoint, val distance: Double)
    private data class Corner(val distance: Double, val angle: Double)
    private data class Segment(val endDistance: Double, val maneuvers: List<TrackManeuver>,
        val navigable: Boolean, val endPoint: TrackPoint?)
    private val segments: List<Segment>
    private val lastPointSegmentIndex = track.segments.indexOfLast { it.points.isNotEmpty() }
    private var currentSegment: Int? = null
    private var nextIndex = 0
    private var announcedIndex: Int? = null
    private var announcementDistance: Double? = null
    private val anticipation = GuidanceAnticipation()
    private var matchGeneration: Long? = null

    init {
        var distance = 0.0
        segments = track.segments.map { segment ->
            val vertices = segment.points.mapIndexed { index, point ->
                if (index > 0) {
                    val previous = segment.points[index - 1]
                    distance += distanceMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
                }
                Vertex(point, distance)
            }
            Segment(distance, findManeuvers(simplify(vertices)),
                vertices.size > 1 && distance - vertices.first().distance > END_DISTANCE_METERS,
                segment.points.lastOrNull())
        }
    }

    fun update(progress: TrackProgress?, location: LocationPoint?, offTrack: OffTrackState): TrackGuidance {
        val accuracy = location?.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy !in 0f..MAX_TRACK_ACCURACY_METERS)
            return suspendGuidance()
        if (offTrack.status == OffTrackStatus.CHECKING) return suspendGuidance(GuidanceStatus.CHECKING_TRACK)
        if (offTrack.status == OffTrackStatus.OFF_TRACK || progress?.matchStatus == TrackMatchStatus.OFF_TRACK)
            return suspendGuidance(GuidanceStatus.OFF_TRACK)
        val travelled = progress?.travelledDistanceMeters
        val index = progress?.matchedSegmentIndex
        if (progress?.matchStatus == TrackMatchStatus.AMBIGUOUS || progress?.matchStatus == TrackMatchStatus.SEARCHING)
            return suspendGuidance(GuidanceStatus.MATCHING_TRACK)
        if (travelled == null || index == null) return suspendGuidance()
        val segment = segments.getOrNull(index) ?: return suspendGuidance()
        if (!segment.navigable) return suspendGuidance()
        if (progress.travelDirection == TrackTravelDirection.BACKWARD) {
            currentSegment = null
            return suspendGuidance(GuidanceStatus.REVERSE_DIRECTION)
        }
        if (currentSegment != index || matchGeneration != progress.matchGeneration) {
            currentSegment = index
            matchGeneration = progress.matchGeneration
            nextIndex = 0
            announcedIndex = null
            announcementDistance = null
            anticipation.reset()
        }
        val announceDistance = anticipation.update(location)
        // A passed cue never reappears because of small backwards GPS oscillations.
        while (nextIndex < segment.maneuvers.size &&
            travelled > segment.maneuvers[nextIndex].endDistanceMeters + PASS_MARGIN_METERS) {
            nextIndex++
            announcedIndex = null
            announcementDistance = null
        }
        val remaining = (segment.endDistance - travelled).coerceAtLeast(0.0)
        if (remaining <= END_DISTANCE_METERS) {
            if (index != lastPointSegmentIndex) return TrackGuidance(GuidanceStatus.SEGMENT_END)
            if (accuracy > MAX_ARRIVAL_ACCURACY_METERS) return TrackGuidance()
            val end = segment.endPoint!!
            val endpointDistance = distanceMeters(location.latitude, location.longitude, end.latitude, end.longitude)
            if (endpointDistance <= END_DISTANCE_METERS)
                return TrackGuidance(GuidanceStatus.TRACK_END)
            return TrackGuidance(GuidanceStatus.ACTIVE, Maneuver.STRAIGHT, endpointDistance)
        }
        val next = segment.maneuvers.getOrNull(nextIndex)
        val toTurn = next?.let { (it.distanceMeters - travelled).coerceAtLeast(0.0) }
        if (toTurn != null && announcedIndex != nextIndex && toTurn <= announceDistance) {
            announcedIndex = nextIndex
            // Freeze the bar's reference distance so slowing down cannot shrink or remove the cue.
            announcementDistance = announceDistance
        }
        val announcingTurn = next != null && announcedIndex == nextIndex
        val second = if (announcingTurn) segment.maneuvers.getOrNull(nextIndex + 1)?.let {
            val gap = it.distanceMeters - next!!.distanceMeters
            if (gap > 0 && gap <= SECOND_MANEUVER_MAX_GAP_METERS + 0.001)
                SecondaryManeuver(it.maneuver, gap) else null
        } else null
        return TrackGuidance(GuidanceStatus.ACTIVE,
            if (announcingTurn) next!!.maneuver else Maneuver.STRAIGHT,
            toTurn ?: remaining, second, announcementDistance.takeIf { announcingTurn })
    }

    private fun suspendGuidance(status: GuidanceStatus = GuidanceStatus.WAITING_FOR_GPS): TrackGuidance {
        anticipation.reset()
        return TrackGuidance(status)
    }

    internal fun maneuvers(segmentIndex: Int): List<TrackManeuver> = segments[segmentIndex].maneuvers

    private fun findManeuvers(vertices: List<Vertex>): List<TrackManeuver> {
        val corners = (1 until vertices.lastIndex).mapNotNull { index ->
            val incoming = bearing(vertices[index - 1].point, vertices[index].point)
            val outgoing = bearing(vertices[index].point, vertices[index + 1].point)
            val angle = (outgoing - incoming + 540.0) % 360.0 - 180.0
            if (abs(angle) < MIN_CORNER_DEGREES) null else Corner(vertices[index].distance, angle)
        }
        val result = mutableListOf<TrackManeuver>()
        var group = mutableListOf<Corner>()
        fun flush() {
            val angle = group.sumOf { it.angle }.coerceIn(-180.0, 180.0)
            if (abs(angle) > STRAIGHT_LIMIT_DEGREES) {
                // Use the middle of the change in direction as the curve's reference point.
                var accumulated = 0.0
                val midpoint = group.first { accumulated += abs(it.angle); accumulated >= abs(angle) / 2 }
                result += TrackManeuver(midpoint.distance, group.last().distance, angle, classify(angle))
            }
            group = mutableListOf()
        }
        for (corner in corners) {
            val previous = group.lastOrNull()
            if (previous != null) {
                val gap = corner.distance - previous.distance
                val separateSharpTurns = abs(group.sumOf { it.angle }) >= TURN_LIMIT_DEGREES &&
                    abs(corner.angle) >= TURN_LIMIT_DEGREES && gap > 20.0
                if (previous.angle * corner.angle <= 0 || gap > MERGE_GAP_METERS ||
                    corner.distance - group.first().distance > MAX_CURVE_SPAN_METERS || separateSharpTurns) flush()
            }
            group += corner
        }
        if (group.isNotEmpty()) flush()
        return result
    }

    private fun simplify(vertices: List<Vertex>): List<Vertex> {
        if (vertices.size < 3) return vertices
        val spaced = mutableListOf(vertices.first())
        for (vertex in vertices.drop(1).dropLast(1)) {
            if (vertex.distance - spaced.last().distance >= MIN_POINT_SPACING_METERS) spaced += vertex
        }
        if (vertices.last().distance > spaced.last().distance) spaced += vertices.last()
        if (spaced.size < 3) return spaced
        val keep = BooleanArray(spaced.size)
        keep[0] = true
        keep[spaced.lastIndex] = true
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(0 to spaced.lastIndex)
        // Iterative Douglas-Peucker avoids overflowing the stack with large GPX files.
        while (pending.isNotEmpty()) {
            val (start, end) = pending.removeLast()
            var furthest = -1
            var maxDistance = SIMPLIFICATION_METERS
            for (index in start + 1 until end) {
                val offset = offsetMeters(spaced[index].point, spaced[start].point, spaced[end].point)
                if (offset > maxDistance) { maxDistance = offset; furthest = index }
            }
            if (furthest >= 0) {
                keep[furthest] = true
                pending.addLast(start to furthest)
                pending.addLast(furthest to end)
            }
        }
        return spaced.filterIndexed { index, _ -> keep[index] }
    }

    private fun offsetMeters(point: TrackPoint, start: TrackPoint, end: TrackPoint): Double {
        val cosine = cos(Math.toRadians(start.latitude))
        val x = Math.toRadians(longitudeDeltaDegrees(start.longitude, point.longitude)) * cosine * EARTH_RADIUS_METERS
        val y = Math.toRadians(point.latitude - start.latitude) * EARTH_RADIUS_METERS
        val endX = Math.toRadians(longitudeDeltaDegrees(start.longitude, end.longitude)) * cosine * EARTH_RADIUS_METERS
        val endY = Math.toRadians(end.latitude - start.latitude) * EARTH_RADIUS_METERS
        val lengthSquared = endX * endX + endY * endY
        val fraction = if (lengthSquared == 0.0) 0.0 else ((x * endX + y * endY) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(x - fraction * endX, y - fraction * endY)
    }

    private fun bearing(start: TrackPoint, end: TrackPoint): Double {
        val latitude1 = Math.toRadians(start.latitude)
        val latitude2 = Math.toRadians(end.latitude)
        val longitude = Math.toRadians(end.longitude - start.longitude)
        return Math.toDegrees(atan2(sin(longitude) * cos(latitude2),
            cos(latitude1) * sin(latitude2) - sin(latitude1) * cos(latitude2) * cos(longitude)))
    }

    private fun classify(angle: Double): Maneuver = when {
        abs(angle) >= HAIRPIN_LIMIT_DEGREES -> if (angle < 0) Maneuver.HAIRPIN_LEFT else Maneuver.HAIRPIN_RIGHT
        abs(angle) >= TURN_LIMIT_DEGREES -> if (angle < 0) Maneuver.LEFT else Maneuver.RIGHT
        else -> if (angle < 0) Maneuver.SLIGHT_LEFT else Maneuver.SLIGHT_RIGHT
    }
}
