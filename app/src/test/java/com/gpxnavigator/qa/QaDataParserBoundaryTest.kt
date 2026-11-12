package com.gpxnavigator.qa

import com.gpxnavigator.data.gpx.GpxParseException
import com.gpxnavigator.data.gpx.GpxParser
import org.junit.Assert.*
import org.junit.Test

class QaDataParserBoundaryTest {
    private fun parse(xml: String) = GpxParser().parse(xml.byteInputStream())
    private fun point(latitude: String, longitude: String, extra: String = "") =
        "<gpx><trk><trkseg><trkpt lat=\"$latitude\" lon=\"$longitude\">$extra</trkpt></trkseg></trk></gpx>"

    @Test fun coordinateBoundsIncludePolesAndAntimeridianAndRejectOneStepBeyond() {
        for ((lat, lon) in listOf("90" to "180", "-90" to "-180")) {
            val result = parse(point(lat, lon)).segments.single().points.single()
            assertEquals(lat.toDouble(), result.latitude, 0.0)
            assertEquals(lon.toDouble(), result.longitude, 0.0)
        }
        for ((lat, lon) in listOf("90.000001" to "0", "-90.000001" to "0",
            "0" to "180.000001", "0" to "-180.000001", "Infinity" to "0", "0" to "NaN")) {
            assertThrows(GpxParseException::class.java) { parse(point(lat, lon)) }
        }
    }

    @Test fun malformedOptionalMeasurementsFailWithControlledParseErrors() {
        for (extra in listOf("<ele>NaN</ele>", "<ele>Infinity</ele>",
            "<time>2026-02-30T09:00:00Z</time>", "<time>yesterday</time>")) {
            assertThrows(extra, GpxParseException::class.java) { parse(point("43", "11", extra)) }
        }
    }

    @Test fun foreignNamespaceCannotInjectCoordinatesOrSegments() {
        val result = parse("""
            <gpx xmlns="http://www.topografix.com/GPX/1/1" xmlns:vendor="urn:qa:extension">
              <extensions><vendor:trk><vendor:trkseg><vendor:trkpt lat="91" lon="999"/></vendor:trkseg></vendor:trk></extensions>
              <trk><trkseg><vendor:trkpt lat="91" lon="999"/><trkpt lat="43" lon="11"/></trkseg></trk>
            </gpx>
        """.trimIndent())
        assertEquals(1, result.segments.size)
        assertEquals(1, result.segments.single().points.size)
        assertEquals(43.0, result.segments.single().points.single().latitude, 0.0)
    }
}
