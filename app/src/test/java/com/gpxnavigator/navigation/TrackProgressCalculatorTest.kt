package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackProgressCalculatorTest {
    private fun point(latitude: Double, longitude: Double, accuracy: Float? = 5f) =
        LocationPoint(latitude, longitude, accuracy, null, null, null)

    @Test fun totalAndMidpointProgressOnStraightTrack() {
        val track = Track(null, listOf(TrackSegment(listOf(
            TrackPoint(0.0, 0.0), TrackPoint(0.0, 0.01),
        ))))
        val calculator = TrackProgressCalculator(track)
        val progress = calculator.progress(point(0.0, 0.005))

        assertEquals(1_111.95, progress.totalDistanceMeters, 1.0)
        assertEquals(progress.totalDistanceMeters / 2, progress.travelledDistanceMeters!!, 1.0)
        assertEquals(progress.totalDistanceMeters / 2, progress.remainingDistanceMeters!!, 1.0)
    }

    @Test fun separateSegmentsDoNotCountGap() {
        val track = Track(null, listOf(
            TrackSegment(listOf(TrackPoint(0.0, 0.0), TrackPoint(0.0, 0.001))),
            TrackSegment(listOf(TrackPoint(1.0, 1.0), TrackPoint(1.0, 1.001))),
        ))
        val calculator = TrackProgressCalculator(track)
        val progress = calculator.progress(point(1.0, 1.0005))

        assertTrue(progress.totalDistanceMeters in 220.0..223.0)
        assertEquals(0.75 * progress.totalDistanceMeters,
            progress.travelledDistanceMeters!!, 1.0)
    }

    @Test fun offTrackAndPoorAccuracyDoNotInventProgress() {
        val calculator = TrackProgressCalculator(Track(null, listOf(
            TrackSegment(listOf(TrackPoint(0.0, 0.0), TrackPoint(0.0, 0.01))),
        )))

        assertNull(calculator.progress(point(1.0, 1.0)).travelledDistanceMeters)
        assertNull(calculator.progress(point(0.0, 0.005, 150f)).remainingDistanceMeters)
        assertNull(calculator.progress(null).travelledDistanceMeters)
    }

    @Test fun isolatedPointHasZeroLengthProgressOnlyNearby() {
        val calculator = TrackProgressCalculator(Track(null, listOf(
            TrackSegment(listOf(TrackPoint(43.77, 11.25))),
        )))

        assertEquals(0.0, calculator.progress(point(43.77, 11.25)).remainingDistanceMeters!!, 0.0)
        assertNull(calculator.progress(point(43.78, 11.25)).travelledDistanceMeters)
    }

    @Test fun reportsDistanceToSegmentEvenWhenProgressIsUnavailable() {
        val calculator = TrackProgressCalculator(Track(null, listOf(TrackSegment(listOf(
            TrackPoint(0.0, 0.0), TrackPoint(0.0, 0.01),
        )))))
        val progress = calculator.progress(point(0.001, 0.005))
        assertNull(progress.travelledDistanceMeters)
        assertEquals(111.2, progress.distanceFromTrackMeters!!, 1.0)
        assertNull(calculator.progress(null).distanceFromTrackMeters)
        assertNull(calculator.progress(point(0.001, 0.005, 150f)).distanceFromTrackMeters)
    }

    @Test fun gapBetweenSegmentsIsNotConsideredPartOfTheRoute() {
        val calculator = TrackProgressCalculator(Track(null, listOf(
            TrackSegment(listOf(TrackPoint(0.0, 0.0), TrackPoint(0.0, 0.001))),
            TrackSegment(listOf(TrackPoint(0.0, 0.009), TrackPoint(0.0, 0.01))),
        )))
        val progress = calculator.progress(point(0.0, 0.005))
        assertEquals(444.8, progress.distanceFromTrackMeters!!, 1.0)
        assertNull(progress.travelledDistanceMeters)
    }
}
