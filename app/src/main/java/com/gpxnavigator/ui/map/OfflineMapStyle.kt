package com.gpxnavigator.ui.map

import android.net.Uri
import com.gpxnavigator.data.maps.OfflineMap
import com.gpxnavigator.ui.theme.MapPalette
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PaintPropertyValue

internal const val MAP_FONT = "Noto Sans Regular"
internal const val OFFLINE_GLYPHS = "asset://fonts/{fontstack}/{range}.pbf"

// Tiles and glyphs are local; labels never need a remote font server.
internal fun offlineMapStyle(map: OfflineMap, colors: MapPalette): String {
    val source = JSONObject().put("type", if (map.header.isVector) "vector" else "raster")
        .put("url", "pmtiles://${Uri.fromFile(map.file)}").put("attribution", map.attribution)
    if (!map.header.isVector) source.put("tileSize", 256)
    val style = JSONObject().put("version", 8).put("sources", JSONObject().put("offline", source))
        .put("layers", offlineMapLayers(map, colors))
    if (map.header.isVector) style.put("glyphs", OFFLINE_GLYPHS)
    return style.toString()
}

internal fun updateOfflineMapPaint(style: Style, map: OfflineMap, colors: MapPalette) {
    val layers = offlineMapLayers(map, colors)
    for (index in 0 until layers.length()) {
        val layer = layers.getJSONObject(index)
        val paint = layer.optJSONObject("paint") ?: continue
        val rendered = style.getLayer(layer.getString("id")) ?: continue
        for (name in paint.keys()) {
            if (name.endsWith("-color")) rendered.setProperties(PaintPropertyValue(name, paint.getString(name)))
            if (name == "line-dasharray") {
                val dashes = paint.getJSONArray(name)
                rendered.setProperties(PaintPropertyValue(name, Array(dashes.length()) { dashes.getDouble(it).toFloat() }))
            }
        }
    }
}

private fun offlineMapLayers(map: OfflineMap, colors: MapPalette): JSONArray {
    val layers = JSONArray().put(JSONObject().put("id", "background").put("type", "background")
        .put("paint", JSONObject().put("background-color", colors.background)))
    fun add(id: String, type: String, geometry: String, color: String) {
        if (id !in map.vectorLayers) return
        val paint = JSONObject().put("$type-color", color)
        if (type == "line") paint.put("line-width", JSONArray("[\"interpolate\",[\"linear\"],[\"zoom\"],5,0.5,15,2.5]"))
        layers.put(JSONObject().put("id", "$id-$type").put("type", type)
            .put("source", "offline").put("source-layer", id)
            .put("filter", JSONArray(listOf("==", "\$type", geometry))).put("paint", paint))
    }
    if (map.header.isVector) {
        add("earth", "fill", "Polygon", colors.background)
        add("landcover", "fill", "Polygon", colors.landcover)
        add("landuse", "fill", "Polygon", colors.landuse)
        add("water", "fill", "Polygon", colors.water)
        add("water", "line", "LineString", colors.water)
        add("waterway", "line", "LineString", colors.water)
        add("buildings", "fill", "Polygon", colors.buildings)
        add("building", "fill", "Polygon", colors.buildings)
        add("transit", "line", "LineString", colors.transit)
        for (roadLayer in listOf("roads", "transportation")) {
            if (roadLayer !in map.vectorLayers) continue
            addRoadLayers(layers, roadLayer, colors)
        }
        add("boundaries", "line", "LineString", colors.boundaries)
        add("boundary", "line", "LineString", colors.boundaries)
        val labelName = JSONArray("[\"coalesce\",[\"get\",\"name:it\"],[\"get\",\"name:latin\"],[\"get\",\"name\"],\"\"]")
        fun labels(id: String, layer: String, road: Boolean) {
            if (layer !in map.vectorLayers) return
            val layout = JSONObject().put("text-field", labelName)
                .put("text-font", JSONArray(listOf(MAP_FONT)))
                .put("text-size", if (road) JSONArray("[\"interpolate\",[\"linear\"],[\"zoom\"],12,11,17,14]")
                    else JSONArray("[\"interpolate\",[\"linear\"],[\"zoom\"],3,12,10,15,16,18]"))
                .put("text-padding", if (road) 3 else 6)
                .put("text-max-width", 10).put("text-allow-overlap", false)
                .put("symbol-placement", if (road) "line" else "point")
            if (road) {
                layout.put("symbol-spacing", 350).put("text-max-angle", 35)
            } else {
                layout.put("symbol-sort-key", JSONArray("[\"coalesce\",[\"get\",\"min_zoom\"],0]"))
            }
            layers.put(JSONObject().put("id", id).put("type", "symbol")
                .put("source", "offline").put("source-layer", layer)
                .put("minzoom", if (road) 12 else 2)
                .put("filter", JSONArray(listOf("==", "\$type", if (road) "LineString" else "Point")))
                .put("layout", layout).put("paint", JSONObject()
                    .put("text-color", colors.labels)
                    .put("text-halo-color", colors.labelHalo).put("text-halo-width", 1.5)))
        }
        labels("road-labels", if ("roads" in map.vectorLayers) "roads"
            else if ("transportation_name" in map.vectorLayers) "transportation_name" else "transportation", true)
        labels("place-labels", if ("places" in map.vectorLayers) "places" else "place", false)
    } else {
        layers.put(JSONObject().put("id", "raster").put("type", "raster").put("source", "offline"))
    }
    return layers
}

private fun addRoadLayers(layers: JSONArray, sourceLayer: String, colors: MapPalette) {
    // Protomaps v4, older Protomaps archives and OpenMapTiles use different classification keys.
    val kind = JSONArray("""["coalesce",["get","kind"],["get","pmap:kind"],["get","class"],["get","kind_detail"],["get","highway"],""]""")
    fun isKind(vararg names: String) = JSONArray().put("match").put(kind)
        .put(JSONArray(names.toList())).put(true).put(false)
    val main = isKind("highway", "major_road", "motorway", "motorway_link", "trunk", "trunk_link",
        "primary", "primary_link", "secondary", "secondary_link")
    val trail = JSONArray().put("any")
        .put(isKind("path", "track", "footway", "cycleway", "bridleway", "steps", "pedestrian"))
        .put(JSONArray("""["match",["coalesce",["get","kind_detail"],["get","highway"],""],["path","track","footway","cycleway","bridleway","steps","pedestrian"],true,false]"""))
    val transport = isKind("rail", "railway", "transit", "aerialway", "ferry", "pier", "aeroway")
    // Unknown or missing classes retain the ordinary road rendering.
    val secondary = JSONArray().put("all").put(JSONArray().put("!").put(main))
        .put(JSONArray().put("!").put(trail)).put(JSONArray().put("!").put(transport))
    fun road(id: String, category: JSONArray, color: String, widths: List<Double>,
        outline: Double = 0.0, dashLength: Float? = null, minZoom: Int = 0) {
        val width = JSONArray().put("interpolate").put(JSONArray().put("linear")).put(JSONArray().put("zoom"))
        for ((zoom, value) in listOf(6, 12, 16, 18).zip(widths)) width.put(zoom).put(value + outline)
        val paint = JSONObject().put("line-color", color).put("line-width", width)
        if (dashLength != null) paint.put("line-dasharray", JSONArray(listOf(dashLength, 2)))
        layers.put(JSONObject().put("id", "$sourceLayer-$id").put("type", "line")
            .put("source", "offline").put("source-layer", sourceLayer).put("minzoom", minZoom)
            .put("filter", JSONArray().put("all")
                .put(JSONArray("""["==",["geometry-type"],"LineString"]""")).put(category))
            .put("layout", JSONObject().put("line-join", "round")
                .put("line-cap", if (dashLength != null) "butt" else "round"))
            .put("paint", paint))
    }
    val secondaryWidths = listOf(0.5, 1.5, 5.0, 11.0)
    val mainWidths = listOf(0.6, 2.0, 7.0, 15.0)
    road("transport", transport, colors.transit, listOf(0.4, 0.8, 1.8, 2.5), dashLength = colors.transportDashLength)
    road("casing", secondary, colors.roadBorder, secondaryWidths, outline = 1.5)
    road("surface", secondary, colors.roads, secondaryWidths)
    road("main-casing", main, colors.roadBorder, mainWidths, outline = 1.5)
    road("main-surface", main, colors.majorRoads, mainWidths)
    road("trail", trail, colors.trails, listOf(0.4, 1.2, 2.4, 3.2), dashLength = 2f, minZoom = 11)
}
