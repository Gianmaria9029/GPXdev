package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import org.junit.Assert.*
import org.junit.Test

class ArrivalEndpointTest {
    private fun point(x: Double, y: Double) = TrackPoint(
        Math.toDegrees(y / EARTH_RADIUS_METERS), Math.toDegrees(x / EARTH_RADIUS_METERS))
    private val track = Track(null, listOf(TrackSegment(listOf(point(0.0, 0.0), point(0.0, 500.0)))))
    private fun guidance(x: Double, y: Double, accuracy: Float = 5f): Pair<TrackGuidance, ArrivalEstimate> {
        val coordinate = point(x, y)
        val location = LocationPoint(coordinate.latitude, coordinate.longitude, accuracy, 2f, 0f, null, 1_000_000_000)
        val progress = TrackProgressCalculator(track).progress(location)
        val offTrack = OffTrackMonitor().update(progress.distanceFromTrackMeters, accuracy, 1_000, 1_000)
        val guidance = TrackGuidanceCalculator(track).update(progress, location, offTrack)
        return guidance to ArrivalTimeEstimator(track).update(progress, location, offTrack, guidance, 1_000, 100_000)
    }

    @Test fun aParallelRoadAndAPlaceBeyondTheEndpointAreNotArrival() {
        for ((x, y) in listOf(25.0 to 500.0, 0.0 to 525.0, 9.0 to 491.0)) {
            val (guide, arrival) = guidance(x, y)
            assertNotEquals(GuidanceStatus.TRACK_END, guide.status)
            assertNotEquals(ArrivalStatus.ARRIVED, arrival.status)
            assertNull(arrival.remainingSeconds)
        }
    }

    @Test fun reachingWithinTenMetersWithGoodGpsConfirmsArrival() {
        for ((x, y) in listOf(0.0 to 500.0, 6.0 to 494.0, 0.0 to 509.0)) {
            val (guide, arrival) = guidance(x, y)
            assertEquals(GuidanceStatus.TRACK_END, guide.status)
            assertEquals(ArrivalStatus.ARRIVED, arrival.status)
        }
    }

    @Test fun uncertainGpsAtTheEndpointWaitsForAnAccurateFix() {
        for (accuracy in listOf(10.1f, 20f, 50f)) {
            val (guide, arrival) = guidance(0.0, 500.0, accuracy)
            assertNotEquals(GuidanceStatus.TRACK_END, guide.status)
            assertEquals(GuidanceStatus.WAITING_FOR_GPS, guide.status)
            assertEquals(ArrivalStatus.WAITING_FOR_GPS, arrival.status)
        }
        assertEquals(GuidanceStatus.TRACK_END, guidance(0.0, 500.0, 10f).first.status)
    }
}
