package com.gpxnavigator.data.gpx

import android.util.Xml
import com.gpxnavigator.data.database.RecordingPointEntity
import com.gpxnavigator.navigation.RecordingStatistics
import com.gpxnavigator.navigation.recordingSpeed
import java.io.OutputStream
import java.time.Instant
import org.xmlpull.v1.XmlSerializer

object GpxWriter {
    const val EXTENSION_NAMESPACE = "urn:gpxdev:recording:1"

    fun write(output: OutputStream, name: String, points: List<RecordingPointEntity>,
        startedAtMillis: Long, endedAtMillis: Long?) {
        val xml = Xml.newSerializer()
        xml.setOutput(output, "UTF-8")
        xml.startDocument("UTF-8", true)
        xml.setPrefix("", "http://www.topografix.com/GPX/1/1")
        xml.setPrefix("gpxdev", EXTENSION_NAMESPACE)
        val ns = "http://www.topografix.com/GPX/1/1"
        xml.startTag(ns, "gpx").attribute(null, "version", "1.1").attribute(null, "creator", "GPXdev")
        xml.startTag(ns, "trk").startTag(ns, "name").text(name).endTag(ns, "name")
        xml.startTag(ns, "extensions")
        writeSummary(xml, points, startedAtMillis, endedAtMillis)
        xml.endTag(ns, "extensions")
        var segment: Int? = null
        for (point in points) {
            if (!point.isTrackPoint) continue
            if (segment != point.segment) {
                if (segment != null) xml.endTag(ns, "trkseg")
                xml.startTag(ns, "trkseg")
                segment = point.segment
            }
            xml.startTag(ns, "trkpt").attribute(null, "lat", point.latitude.toString())
                .attribute(null, "lon", point.longitude.toString())
            point.elevation?.let { xml.startTag(ns, "ele").text(it.toString()).endTag(ns, "ele") }
            xml.startTag(ns, "time").text(Instant.ofEpochMilli(point.timestampMillis).toString()).endTag(ns, "time")
            recordingSpeed(point.speedMetersPerSecond)?.let { speed ->
                xml.startTag(ns, "extensions")
                xml.field("speedMetersPerSecond", speed)
                xml.endTag(ns, "extensions")
            }
            xml.endTag(ns, "trkpt")
        }
        if (segment != null) xml.endTag(ns, "trkseg")
        xml.endTag(ns, "trk").endTag(ns, "gpx")
        xml.endDocument()
        xml.flush()
    }

    private fun writeSummary(xml: XmlSerializer, points: List<RecordingPointEntity>,
        startedAtMillis: Long, endedAtMillis: Long?) {
        val statistics = RecordingStatistics.calculate(points)
        xml.startTag(EXTENSION_NAMESPACE, "RecordingSummary")
        xml.field("startedAt", Instant.ofEpochMilli(startedAtMillis))
        endedAtMillis?.let { xml.field("endedAt", Instant.ofEpochMilli(it)) }
        xml.field("totalDistanceMeters", statistics.totalDistanceMeters)
        statistics.maximumSpeedPoint?.let { point ->
            xml.startTag(EXTENSION_NAMESPACE, "maximumSpeed")
                .attribute(null, "metersPerSecond", point.speedMetersPerSecond.toString())
                .attribute(null, "latitude", point.latitude.toString())
                .attribute(null, "longitude", point.longitude.toString())
                .attribute(null, "time", Instant.ofEpochMilli(point.timestampMillis).toString())
                .endTag(EXTENSION_NAMESPACE, "maximumSpeed")
        }
        statistics.minimumElevationMeters?.let { xml.field("minimumElevationMeters", it) }
        statistics.maximumElevationMeters?.let { xml.field("maximumElevationMeters", it) }
        statistics.elevationDifferenceMeters?.let { xml.field("elevationDifferenceMeters", it) }
        statistics.movingAverageSpeedMetersPerSecond?.let { xml.field("movingAverageSpeedMetersPerSecond", it) }
        xml.endTag(EXTENSION_NAMESPACE, "RecordingSummary")
    }

    private fun XmlSerializer.field(name: String, value: Any) {
        startTag(EXTENSION_NAMESPACE, name).text(value.toString()).endTag(EXTENSION_NAMESPACE, name)
    }
}
