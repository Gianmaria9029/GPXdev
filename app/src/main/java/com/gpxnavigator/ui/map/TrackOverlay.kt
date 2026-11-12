package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.navigation.distanceMeters
import com.gpxnavigator.navigation.longitudeDeltaDegrees
import com.gpxnavigator.ui.theme.MapPalette
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.gte
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconKeepUpright
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconPadding
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.symbolSpacing
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val LINE_SOURCE = "gpx-lines"
private const val LINE_LAYER = "gpx-line"
private const val LINE_CASING_LAYER = "gpx-line-casing"
private const val POINT_SOURCE = "gpx-single-points"
private const val POINT_LAYER = "gpx-single-point"
private const val PROGRESS_SOURCE = "gpx-progress-chunks"
private const val FUTURE_LAYER = "gpx-future-line"
private const val PARTIAL_SOURCE = "gpx-future-partial"
private const val PARTIAL_LAYER = "gpx-future-partial-line"
private const val ARROWS_LAYER = "gpx-direction-arrows"
private const val MARKER_SOURCE = "gpx-endpoints"
internal const val TRACK_ARROW_IMAGE = "gpx-direction-arrow-image"
internal const val TRACK_START_IMAGE = "gpx-start-image"
internal const val TRACK_FINISH_IMAGE = "gpx-finish-image"
internal const val TRACK_LOOP_IMAGE = "gpx-loop-image"

internal data class PreparedTrackOverlay(
    val lines: FeatureCollection,
    val points: FeatureCollection,
    val extent: TrackExtent?,
    val progress: PreparedTrackProgress,
    val markers: FeatureCollection,
)

internal fun prepareTrackOverlay(track: Track) = PreparedTrackOverlay(
    lineFeatures(track), singlePointFeatures(track), trackExtent(track), prepareTrackProgress(track), trackMarkers(track),
)

internal fun trackMarkers(track: Track): FeatureCollection {
    val start = track.segments.firstNotNullOfOrNull { it.points.firstOrNull() }
        ?: return FeatureCollection.fromFeatures(emptyList())
    val finish = track.segments.asReversed().firstNotNullOfOrNull { it.points.lastOrNull() }!!
    fun marker(point: TrackPoint, kind: String) =
        Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply { addStringProperty("marker", kind) }
    val combined = distanceMeters(start.latitude, start.longitude, finish.latitude, finish.longitude) <= 15.0
    return FeatureCollection.fromFeatures(if (combined) listOf(marker(start, "start_finish")) else
        listOf(marker(start, "start"), marker(finish, "finish")))
}

internal data class TrackExtent(val north: Double, val east: Double, val south: Double, val west: Double) {
    val isSingleLocation: Boolean get() = north == south && east == west
}

internal fun trackExtent(track: Track): TrackExtent? {
    var north = Double.NEGATIVE_INFINITY
    var east = Double.NEGATIVE_INFINITY
    var south = Double.POSITIVE_INFINITY
    var west = Double.POSITIVE_INFINITY
    for (segment in track.segments) for (point in segment.points) {
        val longitude = if (point.longitude == 180.0) -180.0 else point.longitude
        north = maxOf(north, point.latitude)
        east = maxOf(east, longitude)
        south = minOf(south, point.latitude)
        west = minOf(west, longitude)
    }
    if (north == Double.NEGATIVE_INFINITY) return null
    if (east - west <= 180.0) return TrackExtent(north, east, south, west)

    // Cover each leg's shortest longitude arc, not just its endpoints, before choosing a seam.
    val spans = mutableListOf<Pair<Double, Double>>()
    for (segment in track.segments) {
        var previous: Double? = null
        for (point in segment.points) {
            val longitude = if (point.longitude == 180.0) -180.0 else point.longitude
            val start = previous ?: longitude
            val end = start + longitudeDeltaDegrees(start, longitude)
            val left = minOf(start, end)
            val right = maxOf(start, end)
            when {
                left < -180.0 -> { spans += -180.0 to right; spans += (left + 360.0) to 180.0 }
                right > 180.0 -> { spans += left to 180.0; spans += -180.0 to (right - 360.0) }
                else -> spans += left to right
            }
            previous = longitude
        }
    }
    spans.sortBy { it.first }
    var coveredEast = spans.first().second
    var largestGap = 0.0
    for (span in spans.drop(1)) {
        if (span.first - coveredEast > largestGap) {
            largestGap = span.first - coveredEast
            west = span.first
            east = coveredEast + 360.0
        }
        coveredEast = maxOf(coveredEast, span.second)
    }
    if (spans.first().first + 360.0 - coveredEast > largestGap) {
        west = spans.first().first
        east = coveredEast
    } else if (largestGap == 0.0) {
        west = -180.0
        east = 180.0
    }
    // MapLibre accepts unwrapped bounds (e.g. 179.999..180.001) with east >= west.
    return TrackExtent(north, east, south, west)
}

internal fun lineFeatures(track: Track): FeatureCollection = FeatureCollection.fromFeatures(
    track.segments.filter { it.hasLine() }.map { segment ->
        Feature.fromGeometry(LineString.fromLngLats(segment.points.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }))
    },
)

internal fun singlePointFeatures(track: Track): FeatureCollection = FeatureCollection.fromFeatures(
    track.segments.filter { it.points.isNotEmpty() && !it.hasLine() }.map { segment ->
        val point = segment.points.first()
        Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
    },
)

private fun TrackSegment.hasLine(): Boolean {
    val first = points.firstOrNull() ?: return false
    return points.any { it.latitude != first.latitude || it.longitude != first.longitude }
}

internal fun replaceTrackOverlay(style: Style, track: PreparedTrackOverlay?, colors: MapPalette) {
    style.removeLayer(LINE_LAYER)
    style.removeLayer(LINE_CASING_LAYER)
    style.removeLayer(POINT_LAYER)
    style.removeLayer(FUTURE_LAYER)
    style.removeLayer(PARTIAL_LAYER)
    style.removeLayer(ARROWS_LAYER)
    listOf("start", "finish", "start_finish").forEach { style.removeLayer("gpx-marker-$it") }
    style.removeSource(LINE_SOURCE)
    style.removeSource(POINT_SOURCE)
    style.removeSource(PROGRESS_SOURCE)
    style.removeSource(PARTIAL_SOURCE)
    style.removeSource(MARKER_SOURCE)
    if (track == null) return

    fun addBelowLocation(layer: Layer) {
        if (style.getLayer(LOCATION_LAYER) == null) style.addLayer(layer)
        else style.addLayerBelow(layer, LOCATION_LAYER)
    }

    if (!track.lines.features().isNullOrEmpty()) {
        style.addSource(GeoJsonSource(LINE_SOURCE, track.lines))
        addBelowLocation(LineLayer(LINE_CASING_LAYER, LINE_SOURCE).withProperties(
            lineColor(colors.trackOutline), lineWidth(9f), lineCap("round"), lineJoin("round"),
        ))
        addBelowLocation(LineLayer(LINE_LAYER, LINE_SOURCE).withProperties(
            lineColor(colors.trackPassed), lineWidth(5f), lineCap("round"), lineJoin("round"),
        ))
        style.addSource(GeoJsonSource(PROGRESS_SOURCE, track.progress.features))
        style.addSource(GeoJsonSource(PARTIAL_SOURCE, FeatureCollection.fromFeatures(emptyList())))
        // Remaining sections sit above the muted base, also at intersections/overlaps.
        addBelowLocation(LineLayer(FUTURE_LAYER, PROGRESS_SOURCE)
            .withFilter(gte(get(TRACK_CHUNK_START), literal(-1.0))).withProperties(
                lineColor(colors.track), lineWidth(5f), lineCap("round"), lineJoin("round")))
        addBelowLocation(LineLayer(PARTIAL_LAYER, PARTIAL_SOURCE).withProperties(
            lineColor(colors.track), lineWidth(5f), lineCap("round"), lineJoin("round")))
        val arrows = SymbolLayer(ARROWS_LAYER, LINE_SOURCE).withProperties(
            symbolPlacement("line"), symbolSpacing(120f), iconImage(TRACK_ARROW_IMAGE), iconSize(0.85f),
            iconRotationAlignment("map"), iconPitchAlignment("map"), iconKeepUpright(false),
            iconAllowOverlap(false), iconIgnorePlacement(false), iconPadding(12f))
        arrows.minZoom = 10f
        addBelowLocation(arrows)
    }
    if (!track.points.features().isNullOrEmpty()) {
        style.addSource(GeoJsonSource(POINT_SOURCE, track.points))
        addBelowLocation(CircleLayer(POINT_LAYER, POINT_SOURCE).withProperties(
            circleColor(colors.track), circleRadius(6f), circleStrokeColor(colors.trackOutline),
            circleStrokeWidth(2f),
        ))
    }
    style.addSource(GeoJsonSource(MARKER_SOURCE, track.markers))
    for ((kind, image) in listOf("start" to TRACK_START_IMAGE, "finish" to TRACK_FINISH_IMAGE, "start_finish" to TRACK_LOOP_IMAGE)) {
        addBelowLocation(SymbolLayer("gpx-marker-$kind", MARKER_SOURCE).withFilter(eq(get("marker"), literal(kind)))
            .withProperties(iconImage(image), iconSize(1f), iconAnchor("bottom"),
                iconAllowOverlap(true), iconIgnorePlacement(true), iconRotationAlignment("viewport")))
    }
}

internal fun updateTrackOverlayColors(style: Style, colors: MapPalette) {
    style.getLayer(LINE_CASING_LAYER)?.setProperties(lineColor(colors.trackOutline))
    style.getLayer(LINE_LAYER)?.setProperties(lineColor(colors.trackPassed))
    style.getLayer(FUTURE_LAYER)?.setProperties(lineColor(colors.track))
    style.getLayer(PARTIAL_LAYER)?.setProperties(lineColor(colors.track))
    style.getLayer(POINT_LAYER)?.setProperties(circleColor(colors.track), circleStrokeColor(colors.trackOutline))
}

internal fun updateTrackProgressOverlay(style: Style, track: PreparedTrackOverlay, position: TrackOverlayPosition?) {
    (style.getLayer(FUTURE_LAYER) as? LineLayer)?.setFilter(
        gte(get(TRACK_CHUNK_START), literal(position?.distanceMeters ?: -1.0)))
    (style.getSource(PARTIAL_SOURCE) as? GeoJsonSource)?.setGeoJson(track.progress.partialFutureFeatures(position))
}
