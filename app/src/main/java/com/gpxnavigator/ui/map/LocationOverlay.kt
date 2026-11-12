package com.gpxnavigator.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.ui.theme.MapPalette
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private const val LOCATION_SOURCE = "current-location"
internal const val LOCATION_LAYER = "current-location-marker"
internal const val DIRECTION_IMAGE = "current-location-direction-image"
private const val HALO_LAYER = "current-location-halo"
private const val DIRECTION_LAYER = "current-location-direction"
private const val MIN_DIRECTION_SPEED_METERS_PER_SECOND = 0.8f

internal fun locationDirectionImage(density: Float, colors: MapPalette): Bitmap {
    val size = (36 * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.density = (160 * density).roundToInt()
    val canvas = Canvas(bitmap)
    canvas.scale(size / 36f, size / 36f)
    val arrow = Path().apply {
        moveTo(18f, 2f)
        lineTo(32f, 32f)
        lineTo(18f, 26f)
        lineTo(4f, 32f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colors.location) }
    canvas.drawPath(arrow, paint)
    paint.apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor(colors.locationOutline)
    }
    canvas.drawPath(arrow, paint)
    return bitmap
}

internal fun updateLocationColors(style: Style, colors: MapPalette) {
    style.getLayer(HALO_LAYER)?.setProperties(circleColor(colors.locationHalo))
    style.getLayer(LOCATION_LAYER)?.setProperties(
        circleColor(colors.location), circleStrokeColor(colors.locationOutline))
}

internal fun locationFeature(location: LocationPoint): Feature = Feature.fromGeometry(
    Point.fromLngLat(location.longitude, location.latitude),
).apply {
    val bearing = location.bearingDegrees?.takeIf { it.isFinite() }
    // Bearing is a direction of motion, and can be unstable when the device is still.
    if (bearing != null && (location.speedMetersPerSecond == null ||
            location.speedMetersPerSecond >= MIN_DIRECTION_SPEED_METERS_PER_SECOND)) {
        addNumberProperty("bearing", ((bearing % 360f) + 360f) % 360f)
    }
}

internal fun updateLocationOverlay(style: Style, location: LocationPoint?, colors: MapPalette) {
    val source = style.getSource(LOCATION_SOURCE) as? GeoJsonSource
    if (location == null) {
        if (source != null) {
            style.removeLayer(DIRECTION_LAYER)
            style.removeLayer(LOCATION_LAYER)
            style.removeLayer(HALO_LAYER)
            style.removeSource(LOCATION_SOURCE)
        }
        return
    }
    val point = locationFeature(location)
    if (source == null) {
        style.addSource(GeoJsonSource(LOCATION_SOURCE, point))
        style.addLayer(CircleLayer(HALO_LAYER, LOCATION_SOURCE).withProperties(
            circleColor(colors.locationHalo), circleRadius(19f),
        ))
        style.addLayer(CircleLayer(LOCATION_LAYER, LOCATION_SOURCE).withFilter(not(has("bearing"))).withProperties(
            circleColor(colors.location), circleRadius(10f), circleStrokeColor(colors.locationOutline),
            circleStrokeWidth(3f),
        ))
        style.addLayer(SymbolLayer(DIRECTION_LAYER, LOCATION_SOURCE).withFilter(has("bearing")).withProperties(
            iconImage(DIRECTION_IMAGE), iconRotate(get("bearing")), iconRotationAlignment("map"),
            iconAllowOverlap(true), iconIgnorePlacement(true),
        ))
    } else {
        source.setGeoJson(point)
    }
}
