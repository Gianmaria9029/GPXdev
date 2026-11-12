package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import java.time.Duration

data class TrackSummary(
    val distanceMeters: Double,
    val pointCount: Int,
    val segmentCount: Int,
    val start: TrackPoint?,
    val finish: TrackPoint?,
    val minimumElevation: Double?,
    val maximumElevation: Double?,
    val ascentMeters: Double?,
    val descentMeters: Double?,
    val elevationIncomplete: Boolean,
    val recordedDuration: Duration?,
)

/** Summarises only connected legs; missing elevations never create artificial climbs. */
fun summarizeTrack(track: Track, checkActive: () -> Unit = {}): TrackSummary {
    var distance = 0.0
    var count = 0
    var segments = 0
    var start: TrackPoint? = null
    var finish: TrackPoint? = null
    var minimum: Double? = null
    var maximum: Double? = null
    var ascent = 0.0
    var descent = 0.0
    var elevationLegs = 0
    var missingElevation = false
    var duration = Duration.ZERO
    var completeTime = true
    var timedLegs = 0
    for (segment in track.segments) {
        checkActive()
        if (segment.points.isNotEmpty()) segments++
        var previous: TrackPoint? = null
        for (point in segment.points) {
            checkActive()
            count++
            if (start == null) start = point
            finish = point
            val elevation = point.elevation?.takeIf { it.isFinite() }
            if (elevation == null) missingElevation = true else {
                minimum = minimum?.let { minOf(it, elevation) } ?: elevation
                maximum = maximum?.let { maxOf(it, elevation) } ?: elevation
            }
            if (point.timestamp == null) completeTime = false
            previous?.let { before ->
                distance += distanceMeters(before.latitude, before.longitude, point.latitude, point.longitude)
                val beforeElevation = before.elevation?.takeIf { it.isFinite() }
                if (beforeElevation != null && elevation != null) {
                    val change = elevation - beforeElevation
                    ascent += change.coerceAtLeast(0.0)
                    descent += (-change).coerceAtLeast(0.0)
                    elevationLegs++
                }
                val from = before.timestamp
                val to = point.timestamp
                if (from != null && to != null && !to.isBefore(from)) {
                    duration = duration.plus(Duration.between(from, to))
                    timedLegs++
                } else completeTime = false
            }
            previous = point
        }
    }
    return TrackSummary(distance, count, segments, start, finish, minimum, maximum,
        ascent.takeIf { elevationLegs > 0 }, descent.takeIf { elevationLegs > 0 }, missingElevation,
        duration.takeIf { completeTime && timedLegs > 0 && !it.isZero })
}
