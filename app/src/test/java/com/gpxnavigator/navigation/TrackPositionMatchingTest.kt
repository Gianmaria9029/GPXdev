package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import org.junit.Assert.*
import org.junit.Test

class TrackPositionMatchingTest {
    private fun point(x: Double, y: Double) = TrackPoint(
        Math.toDegrees(y / EARTH_RADIUS_METERS), Math.toDegrees(x / EARTH_RADIUS_METERS))
    private fun fix(x: Double, y: Double, seconds: Long, bearing: Float? = null, accuracy: Float? = 5f) =
        point(x, y).let { LocationPoint(it.latitude, it.longitude, accuracy,
            if (bearing == null) null else 5f, bearing, null, seconds * 1_000_000_000L) }
    private fun track(vararg points: TrackPoint) = Track(null, listOf(TrackSegment(points.toList())))
    private fun crossing() = track(point(-100.0, -100.0), point(100.0, 100.0),
        point(100.0, -100.0), point(-100.0, 100.0))

    @Test fun crossingKeepsEstablishedBranchEvenWhenOtherBranchIsCloser() {
        val calculator = TrackProgressCalculator(crossing())
        calculator.progress(fix(-40.0, -40.0, 1))
        calculator.progress(fix(-20.0, -20.0, 3))
        val crossing = calculator.progress(fix(1.0, -1.0, 5))
        assertEquals(TrackMatchStatus.MATCHED, crossing.matchStatus)
        assertEquals(141.42, crossing.travelledDistanceMeters!!, 1.0)
        val after = calculator.progress(fix(20.0, 20.0, 7))
        assertEquals(169.71, after.travelledDistanceMeters!!, 1.0)
    }

    @Test fun startingAtCrossingWaitsForMotionAndDistinctConsistentFixes() {
        val calculator = TrackProgressCalculator(crossing())
        val ambiguous = calculator.progress(fix(0.0, 0.0, 1))
        assertEquals(TrackMatchStatus.AMBIGUOUS, ambiguous.matchStatus)
        assertNull(ambiguous.travelledDistanceMeters)
        assertEquals(TrackMatchStatus.AMBIGUOUS, calculator.progress(fix(0.0, 0.0, 3)).matchStatus)
        assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(fix(15.0, 15.0, 5)).matchStatus)
        assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(fix(25.0, 25.0, 7)).matchStatus)
        val found = calculator.progress(fix(35.0, 35.0, 9))
        assertEquals(TrackMatchStatus.MATCHED, found.matchStatus)
        assertEquals(190.92, found.travelledDistanceMeters!!, 1.0)
    }

    @Test fun identicalOverlapsCannotBeResolvedByRepeatedFixesAlone() {
        val loop = listOf(point(0.0, 0.0), point(0.0, 200.0), point(200.0, 200.0),
            point(200.0, 0.0), point(0.0, 0.0), point(0.0, 200.0))
        val calculator = TrackProgressCalculator(track(*loop.toTypedArray()))
        for (second in 1L..7L) {
            val progress = calculator.progress(fix(0.0, 50.0 + second * 5, second, 0f))
            assertEquals(TrackMatchStatus.AMBIGUOUS, progress.matchStatus)
            assertNull(progress.remainingDistanceMeters)
        }
    }

    @Test fun parallelHairpinBranchesDoNotSwapOnGpsJitter() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 200.0),
            point(12.0, 200.0), point(12.0, 0.0)))
        calculator.progress(fix(0.0, 50.0, 1, 0f))
        val jitter = calculator.progress(fix(8.0, 60.0, 3, 0f))
        assertEquals(TrackMatchStatus.MATCHED, jitter.matchStatus)
        assertEquals(60.0, jitter.travelledDistanceMeters!!, 0.5)
        assertEquals(4.0, jitter.distanceFromTrackMeters!!, 0.5)
    }

    @Test fun actualHairpinContinuesOntoReturnLeg() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 200.0),
            point(12.0, 200.0), point(12.0, 0.0)))
        calculator.progress(fix(0.0, 180.0, 1, 0f))
        calculator.progress(fix(0.0, 195.0, 3, 0f))
        calculator.progress(fix(6.0, 200.0, 5, 90f))
        calculator.progress(fix(12.0, 190.0, 7, 180f))
        val after = calculator.progress(fix(12.0, 160.0, 9, 180f))
        assertEquals(TrackMatchStatus.MATCHED, after.matchStatus)
        assertEquals(252.0, after.travelledDistanceMeters!!, 0.5)
        assertEquals(TrackTravelDirection.FORWARD, after.travelDirection)
    }

    @Test fun realBacktrackingDecreasesProgressAndCanResumeForward() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 500.0)))
        calculator.progress(fix(0.0, 200.0, 1, 0f))
        val back = calculator.progress(fix(0.0, 175.0, 3, 180f))
        assertEquals(175.0, back.travelledDistanceMeters!!, 0.1)
        assertEquals(TrackTravelDirection.BACKWARD, back.travelDirection)
        calculator.progress(fix(0.0, 150.0, 5, 180f))
        assertEquals(TrackTravelDirection.FORWARD, calculator.progress(fix(0.0, 175.0, 7, 0f)).travelDirection)
    }

    @Test fun smallStationaryOscillationsDoNotIndicateBacktracking() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 500.0)))
        calculator.progress(fix(0.0, 200.0, 1, 0f))
        for ((index, y) in listOf(202.0, 197.0, 201.0, 199.0).withIndex()) {
            assertEquals(TrackTravelDirection.FORWARD,
                calculator.progress(fix(0.0, y, 3L + index * 2)).travelDirection)
        }
    }

    @Test fun lossOfSignalReacquiresAfterThreeNewFixesAndDuplicatesDoNotCount() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 1000.0)))
        calculator.progress(fix(0.0, 100.0, 1, 0f))
        calculator.progress(null)
        val first = fix(0.0, 400.0, 31, 0f)
        assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(first).matchStatus)
        repeat(4) { assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(first).matchStatus) }
        assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(fix(0.0, 410.0, 33, 0f)).matchStatus)
        val found = calculator.progress(fix(0.0, 420.0, 35, 0f))
        assertEquals(TrackMatchStatus.MATCHED, found.matchStatus)
        assertEquals(420.0, found.travelledDistanceMeters!!, 0.1)
        assertEquals(1L, found.matchGeneration)
    }

    @Test fun longGapAndImplausibleJumpRequireConfirmationEvenWithoutNullFix() {
        for (gap in listOf(2L, 30L)) {
            val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 2000.0)))
            calculator.progress(fix(0.0, 100.0, 1, 0f))
            assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(fix(0.0, 1000.0, 1 + gap, 0f)).matchStatus)
        }
    }

    @Test fun oldFixCannotMoveConfirmedPositionOrConfirmCandidate() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 1000.0)))
        val current = calculator.progress(fix(0.0, 200.0, 10, 0f))
        val old = calculator.progress(fix(0.0, 800.0, 9, 0f))
        assertEquals(current.travelledDistanceMeters, old.travelledDistanceMeters)
        assertEquals(TrackMatchStatus.MATCHED, old.matchStatus)
    }

    @Test fun startsInMiddleAndHandlesQualityWithoutInventingProgress() {
        val calculator = TrackProgressCalculator(track(point(0.0, 0.0), point(0.0, 1000.0)))
        assertEquals(700.0, calculator.progress(fix(0.0, 700.0, 1)).travelledDistanceMeters!!, 0.1)
        for ((index, accuracy) in listOf(null, Float.NaN, -1f, 60f).withIndex()) {
            val result = calculator.progress(fix(0.0, 710.0, 3L + index * 2, accuracy = accuracy))
            assertNull(result.travelledDistanceMeters)
            assertEquals(TrackMatchStatus.UNAVAILABLE, result.matchStatus)
        }
    }

    @Test fun disjointSegmentsNeedReacquisitionAndDoNotIncludeTheirGap() {
        val calculator = TrackProgressCalculator(Track(null, listOf(
            TrackSegment(listOf(point(0.0, 0.0), point(0.0, 100.0))),
            TrackSegment(listOf(point(100.0, 100.0), point(100.0, 200.0))),
        )))
        calculator.progress(fix(0.0, 90.0, 1))
        assertEquals(TrackMatchStatus.SEARCHING, calculator.progress(fix(100.0, 110.0, 3)).matchStatus)
        calculator.progress(fix(100.0, 120.0, 5))
        val found = calculator.progress(fix(100.0, 130.0, 7))
        assertEquals(TrackMatchStatus.MATCHED, found.matchStatus)
        assertEquals(1, found.matchedSegmentIndex)
        assertEquals(130.0, found.travelledDistanceMeters!!, 0.1)
        assertEquals(200.0, found.totalDistanceMeters, 0.1)
    }
}
