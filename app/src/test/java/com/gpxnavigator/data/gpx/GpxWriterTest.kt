package com.gpxnavigator.data.gpx

import com.gpxnavigator.data.database.RecordingPointEntity
import com.gpxnavigator.navigation.TrackProgressCalculator
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Document

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GpxWriterTest {
    private val base = Instant.parse("2026-10-28T09:00:00Z").toEpochMilli()
    private fun point(sequence: Int, speed: Float? = 10f, elevation: Double? = 100.0,
        segment: Int = 0, track: Boolean = true) = RecordingPointEntity("r", sequence, segment,
        43.0, 11.0 + sequence * 0.0002, elevation, base + sequence * 2_000L,
        sequence * 2_000L, 5f, speed, track)
    private fun write(points: List<RecordingPointEntity>, endedAt: Long? = base + 60_000): ByteArray =
        ByteArrayOutputStream().also { GpxWriter.write(it, "Giro & prova", points, base - 30_000, endedAt) }.toByteArray()
    private fun document(bytes: ByteArray): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(bytes.inputStream())
    private fun Document.value(name: String): String? = getElementsByTagNameNS(GpxWriter.EXTENSION_NAMESPACE, name)
        .item(0)?.textContent

    @Test fun writesAllAgreedFieldsAndRoundTripsAsANavigableGpx() {
        val points = listOf(point(0, 10f, 120.0), point(1, 20f, 200.0),
            point(2, 30f, 150.0, track = false), point(3, 10f, 80.0), point(4, 10f, 90.0, segment = 1))
        val bytes = write(points)
        val doc = document(bytes)
        assertEquals("1.1", doc.documentElement.getAttribute("version"))
        assertEquals("GPXdev", doc.documentElement.getAttribute("creator"))
        assertEquals(Instant.ofEpochMilli(base - 30_000).toString(), doc.value("startedAt"))
        assertEquals(Instant.ofEpochMilli(base + 60_000).toString(), doc.value("endedAt"))
        val maximum = doc.getElementsByTagNameNS(GpxWriter.EXTENSION_NAMESPACE, "maximumSpeed").item(0) as org.w3c.dom.Element
        assertEquals("30.0", maximum.getAttribute("metersPerSecond"))
        assertEquals(points[2].latitude.toString(), maximum.getAttribute("latitude"))
        assertEquals(points[2].longitude.toString(), maximum.getAttribute("longitude"))
        assertEquals(Instant.ofEpochMilli(points[2].timestampMillis).toString(), maximum.getAttribute("time"))
        assertEquals("80.0", doc.value("minimumElevationMeters"))
        assertEquals("200.0", doc.value("maximumElevationMeters"))
        assertEquals("-30.0", doc.value("elevationDifferenceMeters"))
        assertEquals(20.0, doc.value("movingAverageSpeedMetersPerSecond")!!.toDouble(), 0.0)
        val track = GpxParser().parse(bytes.inputStream())
        assertEquals("Giro & prova", track.name)
        assertEquals(listOf(3, 1), track.segments.map { it.points.size })
        assertEquals(points.last().longitude, track.segments.last().points.single().longitude, 0.0)
        assertEquals(TrackProgressCalculator(track).totalDistanceMeters,
            doc.value("totalDistanceMeters")!!.toDouble(), 0.000001)
        assertEquals(listOf("name", "extensions", "trkseg", "trkseg"),
            doc.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trk").item(0)
                .childNodes.let { nodes -> (0 until nodes.length).map { nodes.item(it) }
                    .filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }.map { it.localName } })
        assertEquals(4, doc.getElementsByTagNameNS(GpxWriter.EXTENSION_NAMESPACE, "speedMetersPerSecond").length)
        // A real writer output is retained as an example for the phone verification kit.
        File("build/outputs/gpx-tests/esempio-dati-giro-1.23.0.gpx").apply { parentFile?.mkdirs(); writeBytes(bytes) }
    }

    @Test fun missingSpeedElevationAndLegacyStopTimeAreOmittedInsteadOfInvented() {
        val bytes = write(listOf(point(0, null, null), point(1, null, null)), endedAt = null)
        val doc = document(bytes)
        for (name in listOf("maximumSpeed", "movingAverageSpeedMetersPerSecond", "speedMetersPerSecond",
            "minimumElevationMeters", "maximumElevationMeters", "elevationDifferenceMeters", "endedAt")) {
            assertNull(name, doc.value(name))
        }
        assertEquals(2, GpxParser().parse(bytes.inputStream()).segments.single().points.size)
    }

    @Test fun zeroSpeedAndZeroElevationAreWrittenAsRealMeasurements() {
        val doc = document(write(listOf(point(0, 0f, 0.0))))
        assertEquals("0.0", doc.value("speedMetersPerSecond"))
        assertEquals("0.0", doc.value("minimumElevationMeters"))
        assertEquals("0.0", doc.value("elevationDifferenceMeters"))
        assertNull(doc.value("movingAverageSpeedMetersPerSecond"))
    }

    @Test fun serializationUsesDecimalDotsWithItalianLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            val bytes = write(listOf(point(0, 12.5f), point(1, 12.5f)))
            assertEquals("12.5", document(bytes).value("speedMetersPerSecond"))
            assertEquals("12.5", document(bytes).value("movingAverageSpeedMetersPerSecond"))
            assertEquals(2, GpxParser().parse(bytes.inputStream()).segments.single().points.size)
        } finally { Locale.setDefault(before) }
    }
}
