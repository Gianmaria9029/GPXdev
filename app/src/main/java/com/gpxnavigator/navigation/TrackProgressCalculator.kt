package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import kotlin.math.cos

data class TrackProgress(
    val totalDistanceMeters: Double,
    val travelledDistanceMeters: Double?,
    val remainingDistanceMeters: Double?,
    val distanceFromTrackMeters: Double? = null,
    val matchedSegmentIndex: Int? = null,
    val matchStatus: TrackMatchStatus = if (travelledDistanceMeters == null) TrackMatchStatus.UNAVAILABLE else TrackMatchStatus.MATCHED,
    val travelDirection: TrackTravelDirection = TrackTravelDirection.UNKNOWN,
    val matchGeneration: Long = 0,
)

class TrackProgressCalculator(track: Track) {
    private data class Leg(
        val start: TrackPoint,
        val end: TrackPoint,
        val startDistanceMeters: Double,
        val lengthMeters: Double,
        val segmentIndex: Int,
        val bearing: Double?,
    )

    private data class IsolatedPoint(val point: TrackPoint, val distance: Double, val segmentIndex: Int)

    private val legs: List<Leg>
    private val isolatedPoints: List<IsolatedPoint>
    val totalDistanceMeters: Double
    private val matcher = TrackPositionMatcher()

    init {
        val builtLegs = mutableListOf<Leg>()
        val builtPoints = mutableListOf<IsolatedPoint>()
        var distance = 0.0
        for ((segmentIndex, segment) in track.segments.withIndex()) {
            if (segment.points.size == 1) builtPoints += IsolatedPoint(segment.points.first(), distance, segmentIndex)
            for (index in 0 until segment.points.lastIndex) {
                val start = segment.points[index]
                val end = segment.points[index + 1]
                val length = distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)
                builtLegs += Leg(start, end, distance, length, segmentIndex,
                    if (length > 0.1) bearingDegrees(start.latitude, start.longitude, end.latitude, end.longitude) else null)
                distance += length
            }
        }
        legs = builtLegs
        isolatedPoints = builtPoints
        totalDistanceMeters = distance
    }

    fun progress(location: LocationPoint?): TrackProgress {
        val accuracy = location?.accuracyMeters
        if ((legs.isEmpty() && isolatedPoints.isEmpty()) ||
            location == null || !location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0 ||
            accuracy == null || !accuracy.isFinite() || accuracy !in 0f..MAX_TRACK_ACCURACY_METERS) {
            matcher.invalidate()
            return TrackProgress(totalDistanceMeters, null, null)
        }
        var closestDistance = Double.POSITIVE_INFINITY
        val tolerance = trackToleranceMeters(accuracy)
        val candidates = mutableListOf<TrackProjection>()
        for (leg in legs) {
            val (distance, fraction) = projection(location, leg)
            closestDistance = minOf(closestDistance, distance)
            if (distance <= tolerance) candidates += TrackProjection(distance,
                leg.startDistanceMeters + leg.lengthMeters * fraction, leg.segmentIndex, leg.bearing)
        }
        for ((point, distanceAlongTrack, index) in isolatedPoints) {
            val distance = distanceMeters(location.latitude, location.longitude,
                point.latitude, point.longitude)
            closestDistance = minOf(closestDistance, distance)
            if (distance <= tolerance) candidates += TrackProjection(distance, distanceAlongTrack, index, null)
        }
        val match = matcher.update(candidates, location)
        val completed = match.projection?.distanceAlongTrack?.coerceIn(0.0, totalDistanceMeters)
        return TrackProgress(totalDistanceMeters, completed, completed?.let { totalDistanceMeters - it },
            closestDistance.takeIf { it.isFinite() }, match.projection?.segmentIndex, match.status,
            match.direction, match.generation)
    }

    fun invalidate() = matcher.invalidate()

    private fun projection(location: LocationPoint, leg: Leg): Pair<Double, Double> {
        val cosine = cos(Math.toRadians((leg.start.latitude + leg.end.latitude + location.latitude) / 3))
        val longitudeDelta = longitudeDeltaDegrees(leg.start.longitude, leg.end.longitude)
        val endX = Math.toRadians(longitudeDelta) * cosine * EARTH_RADIUS_METERS
        val endY = Math.toRadians(leg.end.latitude - leg.start.latitude) * EARTH_RADIUS_METERS
        val pointX = Math.toRadians(longitudeDeltaDegrees(leg.start.longitude, location.longitude)) * cosine * EARTH_RADIUS_METERS
        val pointY = Math.toRadians(location.latitude - leg.start.latitude) * EARTH_RADIUS_METERS
        val squaredLength = endX * endX + endY * endY
        val fraction = if (squaredLength == 0.0) 0.0 else
            ((pointX * endX + pointY * endY) / squaredLength).coerceIn(0.0, 1.0)
        // The plane chooses a local fraction; geographic distance stays valid for far-away fixes.
        val projectedLatitude = leg.start.latitude + (leg.end.latitude - leg.start.latitude) * fraction
        val projectedLongitude = leg.start.longitude + longitudeDelta * fraction
        return distanceMeters(location.latitude, location.longitude, projectedLatitude, projectedLongitude) to fraction
    }
}
