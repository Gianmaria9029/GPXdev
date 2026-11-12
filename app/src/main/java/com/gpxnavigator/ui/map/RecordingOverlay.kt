package com.gpxnavigator.ui.map

import com.gpxnavigator.navigation.RecordingChunk
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal class RecordingOverlay {
    private var id: String? = null
    private var rendered: List<RecordingChunk> = emptyList()

    fun update(style: Style, recordingId: String?, chunks: List<RecordingChunk>) {
        if (id != recordingId || chunks.size < rendered.size) {
            rendered.indices.forEach { index ->
                style.removeLayer("recorded-line-$index")
                style.removeLayer("recorded-outline-$index")
                style.removeSource("recorded-source-$index")
            }
            rendered = emptyList()
            id = recordingId
        }
        if (rendered === chunks) return
        for (index in chunks.indices) {
            if (rendered.getOrNull(index) === chunks[index]) continue
            val points = chunks[index].points.map { Point.fromLngLat(it.longitude, it.latitude) }
            val geometry = LineString.fromLngLats(if (points.size == 1) points + points else points)
            val source = "recorded-source-$index"
            val existing = style.getSource(source) as? GeoJsonSource
            if (existing != null) existing.setGeoJson(geometry) else {
                style.addSource(GeoJsonSource(source, geometry))
                val outline = LineLayer("recorded-outline-$index", source).withProperties(
                    lineColor("#FFFFFF"), lineWidth(6f), lineCap("round"), lineJoin("round"))
                val line = LineLayer("recorded-line-$index", source).withProperties(
                    lineColor("#0057A8"), lineWidth(3f), lineCap("round"), lineJoin("round"))
                if (style.getLayer(LOCATION_LAYER) == null) {
                    style.addLayer(outline); style.addLayer(line)
                } else {
                    style.addLayerBelow(outline, LOCATION_LAYER); style.addLayerBelow(line, LOCATION_LAYER)
                }
            }
        }
        rendered = chunks
    }
}
