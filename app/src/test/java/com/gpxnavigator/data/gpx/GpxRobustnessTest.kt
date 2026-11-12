package com.gpxnavigator.data.gpx

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.navigation.TrackProgressCalculator
import com.gpxnavigator.ui.map.prepareTrackOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString

class GpxRobustnessTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/$name")!!
        .use { GpxParser().parse(it) }

    @Test fun fiftyThousandPointsSurviveParsingRenderingAndProgress() {
        val track = fixture("m10-long-track.gpx")
        assertEquals(50_000, track.segments.single().points.size)
        val overlay = prepareTrackOverlay(track)
        val line = overlay.lines.features()!!.single().geometry() as LineString
        assertEquals(50_000, line.coordinates().size)
        assertEquals(11.299999, overlay.extent!!.east, 0.0000001)
        val calculator = TrackProgressCalculator(track)
        val point = LocationPoint(43.77, 11.275, 5f, null, null, null)
        val progress = calculator.progress(point)
        assertTrue(progress.totalDistanceMeters in 4_000.0..4_030.0)
        assertEquals(progress.totalDistanceMeters / 2, progress.travelledDistanceMeters!!, 1.0)
        assertEquals(progress.totalDistanceMeters,
            progress.travelledDistanceMeters + progress.remainingDistanceMeters!!, 0.001)
    }

    @Test fun singlePointRemainsVisibleWithZeroLength() {
        val track = fixture("m10-single-point.gpx")
        val overlay = prepareTrackOverlay(track)
        assertTrue(overlay.lines.features()!!.isEmpty())
        assertEquals(1, overlay.points.features()!!.size)
        assertTrue(overlay.extent!!.isSingleLocation)
        assertEquals(0.0, TrackProgressCalculator(track).totalDistanceMeters, 0.0)
    }

    @Test fun repeatedPointsDoNotBecomeAnInvisibleZeroLengthLine() {
        val point = TrackPoint(43.77, 11.25)
        val track = Track(null, listOf(TrackSegment(List(3) { point })))
        val overlay = prepareTrackOverlay(track)
        assertTrue(overlay.lines.features()!!.isEmpty())
        assertEquals(1, overlay.points.features()!!.size)
        val progress = TrackProgressCalculator(track).progress(
            LocationPoint(point.latitude, point.longitude, 5f, null, null, null))
        assertEquals(0.0, progress.travelledDistanceMeters!!, 0.0)
        assertEquals(0.0, progress.remainingDistanceMeters!!, 0.0)
    }

    @Test fun invalidAndEmptyPhoneFixturesAreRejected() {
        for (name in listOf("m10-invalid.gpx", "m10-empty.gpx")) {
            assertThrows(GpxParseException::class.java) { fixture(name) }
        }
    }
}
