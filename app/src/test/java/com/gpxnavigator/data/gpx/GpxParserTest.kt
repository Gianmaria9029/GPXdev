package com.gpxnavigator.data.gpx

import java.io.ByteArrayInputStream
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class GpxParserTest {
    private val parser = GpxParser()

    @Test fun validGpx() {
        val track = parse("""
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">
              <trk><name>Giro Firenze</name><trkseg>
                <trkpt lat="43.77" lon="11.25"><ele>52.5</ele><time>2026-09-23T10:00:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """)
        assertEquals("Giro Firenze", track.name)
        assertEquals(1, track.segments.size)
        assertEquals(43.77, track.segments[0].points[0].latitude, 0.0)
        assertEquals(52.5, track.segments[0].points[0].elevation!!, 0.0)
        assertEquals(Instant.parse("2026-09-23T10:00:00Z"), track.segments[0].points[0].timestamp)
    }

    @Test fun multipleSegmentsRemainSeparate() {
        val track = parse("""
            <gpx><trk><trkseg><trkpt lat="1" lon="2"/></trkseg>
            <trkseg><trkpt lat="3" lon="4"/><trkpt lat="5" lon="6"/></trkseg></trk></gpx>
        """)
        assertEquals(2, track.segments.size)
        assertEquals(1, track.segments[0].points.size)
        assertEquals(2, track.segments[1].points.size)
    }

    @Test fun multipleTracksKeepTheirSegments() {
        val track = parse("""
            <gpx><trk><name>Prima</name><trkseg><trkpt lat="1" lon="2"/></trkseg></trk>
            <trk><name>Seconda</name><trkseg><trkpt lat="3" lon="4"/></trkseg></trk></gpx>
        """)
        assertEquals("Prima", track.name)
        assertEquals(2, track.segments.size)
        assertEquals(3.0, track.segments[1].points.single().latitude, 0.0)
    }

    @Test fun readsPhoneTestFixture() {
        val track = javaClass.getResourceAsStream("/short-track.gpx")!!.use(parser::parse)
        assertEquals("Breve percorso a Firenze", track.name)
        assertEquals(3, track.segments.single().points.size)
    }

    @Test fun missingElevationAndTimeAreOptional() {
        val point = parse("<gpx><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg></trk></gpx>")
            .segments.single().points.single()
        assertNull(point.elevation)
        assertNull(point.timestamp)
    }

    @Test fun emptyGpxIsRejected() {
        assertInvalid("<gpx><trk><trkseg/></trk></gpx>")
        assertInvalid("")
    }

    @Test fun malformedGpxIsRejected() {
        assertInvalid("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"></trkseg></trk></gpx>")
    }

    @Test fun invalidCoordinatesAreRejected() {
        assertInvalid("<gpx><trk><trkseg><trkpt lat=\"91\" lon=\"2\"/></trkseg></trk></gpx>")
        assertInvalid("<gpx><trk><trkseg><trkpt lat=\"NaN\" lon=\"2\"/></trkseg></trk></gpx>")
    }

    @Test fun doctypesAreRejected() {
        assertInvalid("<!DOCTYPE gpx [<!ENTITY x \"hello\">]><gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>")
    }

    private fun parse(xml: String) = parser.parse(ByteArrayInputStream(xml.trimIndent().toByteArray()))

    private fun assertInvalid(xml: String) {
        try {
            parse(xml)
            fail("GPX invalido accettato")
        } catch (_: GpxParseException) {
            // Atteso: la UI riceverà questo errore in modo controllato.
        }
    }
}
