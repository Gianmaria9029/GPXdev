package com.gpxnavigator.qa

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.navigation.TrackMatchStatus
import com.gpxnavigator.navigation.TrackProgressCalculator
import com.gpxnavigator.navigation.TrackGuidanceCalculator
import com.gpxnavigator.navigation.TrackTravelDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent geographic fixtures: the short dateline leg must use the same earth as its length. */
class QaNavigationGeographyTest {
    private fun calculator(startLongitude: Double, endLongitude: Double) = TrackProgressCalculator(
        Track("QA synthetic", listOf(TrackSegment(listOf(
            TrackPoint(0.0, startLongitude), TrackPoint(0.0, endLongitude),
        )))),
    )

    private fun fix(longitude: Double, second: Long = 1) = LocationPoint(
        0.0, longitude, 5f, null, null, null, second * 1_000_000_000L,
    )

    @Test fun ordinaryShortLegHasTheExpectedLengthAndMidpoint() {
        val progress = calculator(10.0, 10.002).progress(fix(10.001))
        assertEquals(222.39, progress.totalDistanceMeters, 0.1)
        assertEquals(TrackMatchStatus.MATCHED, progress.matchStatus)
        assertEquals(progress.totalDistanceMeters / 2, progress.travelledDistanceMeters!!, 0.1)
        assertEquals(0.0, progress.distanceFromTrackMeters!!, 0.1)
    }

    @Test fun antimeridianMidpointMatchesTheShort222MeterLeg() {
        val progress = calculator(179.999, -179.999).progress(fix(180.0))
        assertEquals(222.39, progress.totalDistanceMeters, 0.1)
        assertTrue("At 180 degrees the short leg's midpoint must be on track; actual=$progress",
            progress.matchStatus == TrackMatchStatus.MATCHED)
        assertEquals(progress.totalDistanceMeters / 2, progress.travelledDistanceMeters!!, 0.1)
        assertEquals(0.0, progress.distanceFromTrackMeters!!, 0.1)
    }

    @Test fun greenwichCannotMatchATrackOnTheOppositeSideOfEarth() {
        val progress = calculator(179.999, -179.999).progress(fix(0.0))
        assertTrue("Greenwich is about 20,015 km from this 222 m leg; actual=$progress",
            progress.matchStatus == TrackMatchStatus.OFF_TRACK)
        assertNull(progress.travelledDistanceMeters)
        assertTrue("Distance to the dateline must exceed 19,000 km; actual=$progress",
            progress.distanceFromTrackMeters!! > 19_000_000.0)
    }

    @Test fun equivalent180AndMinus180CoordinatesHaveIdenticalProgress() {
        val east = calculator(180.0, 179.999).progress(fix(180.0))
        val west = calculator(180.0, 179.999).progress(fix(-180.0))
        assertTrue("+180 and -180 are the same geographic location; east=$east; west=$west",
            east.matchStatus == west.matchStatus)
        assertEquals(east.travelledDistanceMeters!!, west.travelledDistanceMeters!!, 0.1)
        assertEquals(east.distanceFromTrackMeters!!, west.distanceFromTrackMeters!!, 0.1)
    }

    @Test fun westboundCrossingAlsoMatchesItsMidpoint() {
        val progress = calculator(-179.999, 179.999).progress(fix(-180.0))
        assertEquals(TrackMatchStatus.MATCHED, progress.matchStatus)
        assertEquals(progress.totalDistanceMeters / 2, progress.travelledDistanceMeters!!, 0.1)
        assertEquals(0.0, progress.distanceFromTrackMeters!!, 0.1)
    }

    @Test fun consecutiveFixesAcrossTheDatelineKeepForwardProgress() {
        val calculator = calculator(179.999, -179.999)
        var previous = -1.0
        for ((index, longitude) in listOf(179.999, 179.9995, 180.0, -179.9995, -179.999).withIndex()) {
            val progress = calculator.progress(fix(longitude, 1L + index * 2))
            assertEquals(progress.toString(), TrackMatchStatus.MATCHED, progress.matchStatus)
            assertTrue(progress.travelledDistanceMeters!! >= previous)
            previous = progress.travelledDistanceMeters!!
            if (index >= 1) assertEquals(TrackTravelDirection.FORWARD, progress.travelDirection)
        }
        assertEquals(222.39, previous, 0.1)
    }

    @Test fun guidanceGeometryIsInvariantWhenMovedAcrossTheDateline() {
        fun track(shift: Double) = Track("QA bend", listOf(TrackSegment(listOf(
            0.0 to 0.0, 0.0 to 0.003, 0.003 to 0.003,
        ).map { (lat, lon) ->
            val shifted = lon + shift
            TrackPoint(lat, if (shifted > 180.0) shifted - 360.0 else shifted)
        })))
        val ordinary = TrackGuidanceCalculator(track(0.0)).maneuvers(0)
        val crossing = TrackGuidanceCalculator(track(179.999)).maneuvers(0)
        assertTrue(ordinary.isNotEmpty())
        assertEquals(ordinary.map { it.maneuver }, crossing.map { it.maneuver })
        assertEquals(ordinary.single().angleDegrees, crossing.single().angleDegrees, 0.01)
        assertEquals(ordinary.single().distanceMeters, crossing.single().distanceMeters, 0.1)
    }

    @Test fun longSyntheticTrackSurvivesRepeatedFixesLossAndReacquisition() {
        val points = (0..20_000).map { TrackPoint(0.0, it * 0.00001) }
        val calculator = TrackProgressCalculator(Track("QA 20k points", listOf(TrackSegment(points))))
        var second = 100L
        for (index in 0..200) {
            val longitude = 0.02 + index * 0.00001
            val progress = calculator.progress(fix(longitude, second++))
            assertEquals("Index=$index; actual=$progress", TrackMatchStatus.MATCHED, progress.matchStatus)
            assertTrue(progress.travelledDistanceMeters!! in 0.0..progress.totalDistanceMeters)
            assertEquals(progress.totalDistanceMeters,
                progress.travelledDistanceMeters!! + progress.remainingDistanceMeters!!, 0.001)
            assertEquals("Repeated GPS callbacks must be idempotent", progress,
                calculator.progress(fix(longitude, second - 1)))
        }
        assertNull(calculator.progress(null).travelledDistanceMeters)
        val statuses = (0..2).map { calculator.progress(fix(0.022, second++)).matchStatus }
        assertEquals(listOf(TrackMatchStatus.SEARCHING, TrackMatchStatus.SEARCHING, TrackMatchStatus.MATCHED), statuses)
    }
}
