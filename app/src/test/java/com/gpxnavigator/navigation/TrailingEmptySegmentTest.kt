package com.gpxnavigator.navigation

import com.gpxnavigator.data.gpx.GpxParser
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import org.junit.Assert.*
import org.junit.Test

class TrailingEmptySegmentTest {
    private fun latitude(meters: Double) = Math.toDegrees(meters / EARTH_RADIUS_METERS)
    private fun segment(from: Double, to: Double) =
        "<trkseg><trkpt lat=\"${latitude(from)}\" lon=\"0\"/><trkpt lat=\"${latitude(to)}\" lon=\"0\"/></trkseg>"
    private fun parse(segments: String): Track = GpxParser().parse(
        "<gpx><trk>$segments</trk></gpx>".byteInputStream())
    private fun guidance(track: Track, meters: Double, offset: Double = 0.0,
        accuracy: Float = 5f): Pair<TrackGuidance, ArrivalEstimate> {
        val location = LocationPoint(latitude(meters), latitude(offset), accuracy, 2f, 0f, null, 1_000_000_000L)
        val progress = TrackProgressCalculator(track).progress(location)
        val offTrack = OffTrackMonitor().update(progress.distanceFromTrackMeters, accuracy, 1_000, 1_000)
        val guidance = TrackGuidanceCalculator(track).update(progress, location, offTrack)
        return guidance to ArrivalTimeEstimator(track).update(progress, location, offTrack, guidance, 1_000, 100_000)
    }

    @Test fun parsedLeadingAndTrailingEmptySegmentsDoNotHideTheRealArrival() {
        for (leading in listOf(0, 2)) for (trailing in listOf(1, 3)) {
            val track = parse("<trkseg/>".repeat(leading) + segment(0.0, 500.0) + "<trkseg/>".repeat(trailing))
            assertEquals(leading + 1 + trailing, track.segments.size)
            val (guide, arrival) = guidance(track, 500.0)
            assertEquals(GuidanceStatus.TRACK_END, guide.status)
            assertEquals(ArrivalStatus.ARRIVED, arrival.status)
        }
    }

    @Test fun emptyMiddleSegmentsNeverJoinRealSegmentsOrCreatePrematureArrival() {
        val track = parse(segment(0.0, 500.0) + "<trkseg/><trkseg/>" + segment(1_000.0, 1_500.0) + "<trkseg/>")
        val earlier = guidance(track, 500.0)
        assertEquals(GuidanceStatus.SEGMENT_END, earlier.first.status)
        assertNotEquals(ArrivalStatus.ARRIVED, earlier.second.status)
        val location = LocationPoint(latitude(500.0), 0.0, 5f, null, null, null)
        assertEquals(1_000.0, TrackProgressCalculator(track).progress(location).totalDistanceMeters, 0.01)
        val end = guidance(track, 1_500.0)
        assertEquals(GuidanceStatus.TRACK_END, end.first.status)
        assertEquals(ArrivalStatus.ARRIVED, end.second.status)
    }

    @Test fun trailingEmptySegmentsKeepRealEndpointAndAccuracyRequirements() {
        val track = parse(segment(0.0, 500.0) + "<trkseg/><trkseg/>")
        for ((offset, accuracy) in listOf(25.0 to 5f, 0.0 to 20f)) {
            val (guide, arrival) = guidance(track, 500.0, offset, accuracy)
            assertNotEquals(GuidanceStatus.TRACK_END, guide.status)
            assertNotEquals(ArrivalStatus.ARRIVED, arrival.status)
        }
    }

    @Test fun aRealFinalIsolatedPointIsNotDiscardedAsAnEmptySegment() {
        val track = parse(segment(0.0, 500.0) + "<trkseg><trkpt lat=\"${latitude(1_000.0)}\" lon=\"0\"/></trkseg><trkseg/>")
        assertEquals(GuidanceStatus.SEGMENT_END, guidance(track, 500.0).first.status)
    }
}
