package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import java.time.Instant
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class TrackSummaryTest {
    private fun point(lon: Double, elevation: Double? = null, seconds: Long? = null) =
        TrackPoint(0.0, lon, elevation, seconds?.let(Instant::ofEpochSecond))

    @Test fun excludesGapsFromDistanceElevationAndRecordedDuration() {
        val a = point(0.0, 10.0, 0)
        val b = point(0.001, 30.0, 60)
        val c = point(1.0, 1000.0, 3600)
        val d = point(1.001, 990.0, 3720)
        val result = summarizeTrack(Track(null, listOf(TrackSegment(emptyList()), TrackSegment(listOf(a, b)),
            TrackSegment(listOf(c, d)), TrackSegment(emptyList()))))
        assertEquals(222.39, result.distanceMeters, 0.1)
        assertEquals(20.0, result.ascentMeters!!, 0.0)
        assertEquals(10.0, result.descentMeters!!, 0.0)
        assertEquals(Duration.ofSeconds(180), result.recordedDuration)
        assertEquals(4, result.pointCount)
        assertEquals(2, result.segmentCount)
        assertEquals(a, result.start)
        assertEquals(d, result.finish)
    }

    @Test fun missingElevationsNeverBridgeAnUnmeasuredClimb() {
        val result = summarizeTrack(Track(null, listOf(TrackSegment(listOf(
            point(0.0, 10.0), point(0.001), point(0.002, 100.0), point(0.003, 90.0),
        )))))
        assertTrue(result.elevationIncomplete)
        assertEquals(10.0, result.minimumElevation!!, 0.0)
        assertEquals(100.0, result.maximumElevation!!, 0.0)
        assertEquals(0.0, result.ascentMeters!!, 0.0)
        assertEquals(10.0, result.descentMeters!!, 0.0)
        assertNull(result.recordedDuration)
    }

    @Test fun isolatedPointsHaveNoInventedClimbsOrDuration() {
        val result = summarizeTrack(Track(null, listOf(TrackSegment(listOf(point(0.0, 40.0, 10))),
            TrackSegment(listOf(point(1.0, 60.0, 20))))))
        assertEquals(0.0, result.distanceMeters, 0.0)
        assertNull(result.ascentMeters)
        assertNull(result.descentMeters)
        assertNull(result.recordedDuration)
    }

    @Test fun invalidOrIncompleteTimesAreNotPresentedAsARecordedDuration() {
        for (points in listOf(listOf(point(0.0, seconds = 60), point(0.001, seconds = 0)),
            listOf(point(0.0, seconds = 0), point(0.001), point(0.002, seconds = 120)))) {
            assertNull(summarizeTrack(Track(null, listOf(TrackSegment(points)))).recordedDuration)
        }
    }

    @Test fun datelineDistanceIsLocalAndEmptyTracksHaveNoEndpoints() {
        val result = summarizeTrack(Track(null, listOf(TrackSegment(listOf(point(179.999), point(-179.999))))))
        assertEquals(222.39, result.distanceMeters, 0.1)
        val empty = summarizeTrack(Track(null, emptyList()))
        assertNull(empty.start)
        assertNull(empty.finish)
        assertNull(empty.minimumElevation)
        assertNull(empty.recordedDuration)
    }
}
