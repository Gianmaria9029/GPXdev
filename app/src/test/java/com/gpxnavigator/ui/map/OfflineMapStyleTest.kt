package com.gpxnavigator.ui.map

import android.app.Application
import com.gpxnavigator.data.maps.OfflineMap
import com.gpxnavigator.data.maps.PmTilesHeader
import com.gpxnavigator.domain.model.MapAppearance
import com.gpxnavigator.ui.theme.CyberMapPalette
import com.gpxnavigator.ui.theme.SoftMapPalette
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class OfflineMapStyleTest {
    private fun map(tileType: Int) = OfflineMap(File("offline-test.pmtiles"), "Test",
        PmTilesHeader(0, 0, 1, tileType, 0, 15, 10.0, 40.0, 12.0, 44.0),
        setOf("earth", "landcover", "water", "roads", "places"), "Offline")

    @Test fun vectorStylesUseTheSelectedPaletteAndLocalSources() {
        val palettes = listOf(SoftMapPalette, CyberMapPalette) + MapAppearance.entries.map(::navigationMapPalette)
        for (palette in palettes) {
            val style = JSONObject(offlineMapStyle(map(1), palette))
            val layers = style.getJSONArray("layers")
            val byId = (0 until layers.length()).map { layers.getJSONObject(it) }.associateBy { it.getString("id") }
            assertEquals(palette.background, byId.getValue("background").getJSONObject("paint").getString("background-color"))
            assertEquals(palette.water, byId.getValue("water-fill").getJSONObject("paint").getString("fill-color"))
            assertEquals(palette.roadBorder, byId.getValue("roads-casing").getJSONObject("paint").getString("line-color"))
            assertEquals(palette.roads, byId.getValue("roads-surface").getJSONObject("paint").getString("line-color"))
            assertEquals(palette.majorRoads, byId.getValue("roads-main-surface").getJSONObject("paint").getString("line-color"))
            assertEquals(palette.trails, byId.getValue("roads-trail").getJSONObject("paint").getString("line-color"))
            assertEquals(palette.labels, byId.getValue("place-labels").getJSONObject("paint").getString("text-color"))
            assertEquals(palette.labelHalo, byId.getValue("place-labels").getJSONObject("paint").getString("text-halo-color"))
            assertEquals(OFFLINE_GLYPHS, style.getString("glyphs"))
            assertTrue(style.getJSONObject("sources").getJSONObject("offline").getString("url").startsWith("pmtiles://"))
            assertFalse(style.toString().contains("https://"))
        }
    }

    @Test fun roadClassesKeepStableLayersAcrossAppearancesAndSupportBothSourceSchemas() {
        for (source in listOf("roads", "transportation")) {
            val archive = map(1).copy(vectorLayers = setOf("earth", "water", source))
            val idsByAppearance = MapAppearance.entries.map { appearance ->
                val layers = JSONObject(offlineMapStyle(archive, navigationMapPalette(appearance))).getJSONArray("layers")
                val byId = (0 until layers.length()).map { layers.getJSONObject(it) }.associateBy { it.getString("id") }
                val trail = byId.getValue("$source-trail")
                assertEquals(source, trail.getString("source-layer"))
                assertEquals("[2,2]", trail.getJSONObject("paint").getJSONArray("line-dasharray").toString())
                val transport = byId.getValue("$source-transport").getJSONObject("paint")
                assertEquals(if (appearance == MapAppearance.LIGHT) 6.0 else 2.0,
                    transport.getJSONArray("line-dasharray").getDouble(0), 0.0)
                assertEquals(navigationMapPalette(appearance).transit, transport.getString("line-color"))
                assertEquals(11, trail.getInt("minzoom"))
                assertFalse(byId.getValue("$source-main-surface").getJSONObject("paint").has("line-dasharray"))
                val filter = trail.getJSONArray("filter").toString()
                for (key in listOf("kind", "pmap:kind", "class", "kind_detail", "highway")) {
                    assertTrue("Missing classification key $key", filter.contains("\"$key\""))
                }
                byId.keys
            }
            assertEquals(idsByAppearance.first(), idsByAppearance.last())
        }
    }

    @Test fun rasterTilesKeepTheirOwnColorsInBothThemes() {
        for (palette in listOf(SoftMapPalette, CyberMapPalette)) {
            val style = JSONObject(offlineMapStyle(map(2), palette))
            val raster = style.getJSONArray("layers").getJSONObject(1)
            assertEquals("raster", raster.getString("type"))
            assertFalse(raster.has("paint"))
            assertFalse(style.has("glyphs"))
        }
    }
}
