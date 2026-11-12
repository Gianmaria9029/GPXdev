package com.gpxnavigator.ui.importgpx

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.ui.map.trackExtent
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.tan

internal data class SketchPoint(val x: Float, val y: Float)
internal data class TrackSketch(val segments: List<List<SketchPoint>> = emptyList())

/** Bounded overview geometry, prepared off the UI thread with the same dateline seam as the map. */
internal fun prepareTrackSketch(track: Track, checkActive: () -> Unit = {}): TrackSketch {
    val extent = trackExtent(track) ?: return TrackSketch()
    fun mercator(latitude: Double): Double = ln(tan(Math.PI / 4 + Math.toRadians(latitude.coerceIn(-85.0, 85.0)) / 2))
    val north = mercator(extent.north)
    val south = mercator(extent.south)
    val horizontal = Math.toRadians(extent.east - extent.west)
    val span = maxOf(horizontal, north - south).coerceAtLeast(0.000001)
    val stride = ceil(track.segments.sumOf { it.points.size } / 3_000.0).toInt().coerceAtLeast(1)
    return TrackSketch(track.segments.filter { it.points.isNotEmpty() }.map { segment ->
        checkActive()
        segment.points.mapIndexedNotNull { index, point ->
            if (index != 0 && index != segment.points.lastIndex && index % stride != 0) null else {
                var longitude = if (point.longitude == 180.0) -180.0 else point.longitude
                if (longitude < extent.west) longitude += 360.0
                SketchPoint(((Math.toRadians(longitude - extent.west) - horizontal / 2) / span + 0.5).toFloat(),
                    (((north + south) / 2 - mercator(point.latitude)) / span + 0.5).toFloat())
            }
        }
    })
}
