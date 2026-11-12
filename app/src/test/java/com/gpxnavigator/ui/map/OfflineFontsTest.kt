package com.gpxnavigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFontsTest {
    @Test fun localGlyphRangesArePackagedWithTheirLicense() {
        for (range in listOf("0-255", "256-511", "512-767", "768-1023",
            "7680-7935", "7936-8191", "8192-8447")) {
            val glyphs = javaClass.getResourceAsStream("/fonts/$MAP_FONT/$range.pbf")
            requireNotNull(glyphs) { "Missing offline font range: $range" }
            glyphs.use { assertTrue(it.readBytes().size > 100) }
        }
        val license = javaClass.getResourceAsStream("/fonts/OFL.txt")!!
            .bufferedReader().use { it.readText() }
        assertTrue(license.contains("SIL OPEN FONT LICENSE"))
        assertEquals("asset://fonts/{fontstack}/{range}.pbf", OFFLINE_GLYPHS)
    }
}
