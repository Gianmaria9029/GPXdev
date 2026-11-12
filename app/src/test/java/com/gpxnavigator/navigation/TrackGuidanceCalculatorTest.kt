package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class TrackGuidanceCalculatorTest {
    private fun point(x: Double, y: Double) = TrackPoint(
        Math.toDegrees(y / EARTH_RADIUS_METERS), Math.toDegrees(x / EARTH_RADIUS_METERS))
    private fun location(x: Double, y: Double, accuracy: Float? = 5f) = point(x, y).let {
        LocationPoint(it.latitude, it.longitude, accuracy, null, null, null)
    }
    private fun track(vararg points: TrackPoint) = Track(null, listOf(TrackSegment(points.toList())))
    private fun corner(angle: Double): Track {
        val radians = Math.toRadians(angle)
        return track(point(0.0, 0.0), point(0.0, 300.0),
            point(sin(radians) * 300, 300 + cos(radians) * 300))
    }
    private fun curvedTrack(radius: Double, angle: Int): Track {
        val arc = (0..angle step 5).map {
            val radians = Math.toRadians(it.toDouble())
            point(radius - radius * cos(radians), 300 + radius * sin(radians))
        }
        val end = Math.toRadians(angle.toDouble())
        return track(*(listOf(point(0.0, 0.0)) + arc + point(
            radius - radius * cos(end) + 300 * sin(end),
            300 + radius * sin(end) + 300 * cos(end))).toTypedArray())
    }
    private fun update(calculator: TrackGuidanceCalculator, progress: TrackProgressCalculator,
        x: Double, y: Double, accuracy: Float? = 5f): TrackGuidance {
        val fix = location(x, y, accuracy)
        return calculator.update(progress.progress(fix), fix, OffTrackState(OffTrackStatus.ON_TRACK))
    }

    @Test fun classifiesBothDirectionsAndThresholds() {
        for ((angle, expected) in listOf(26.0 to Maneuver.SLIGHT_RIGHT, -40.0 to Maneuver.SLIGHT_LEFT,
            59.0 to Maneuver.SLIGHT_RIGHT, 61.0 to Maneuver.RIGHT, -90.0 to Maneuver.LEFT,
            149.0 to Maneuver.RIGHT, 151.0 to Maneuver.HAIRPIN_RIGHT, -170.0 to Maneuver.HAIRPIN_LEFT)) {
            val cue = TrackGuidanceCalculator(corner(angle)).maneuvers(0).single()
            assertEquals("Angle $angle", expected, cue.maneuver)
            assertEquals(angle, cue.angleDegrees, 0.01)
        }
        assertTrue(TrackGuidanceCalculator(corner(24.0)).maneuvers(0).isEmpty())
    }

    @Test fun noisyStraightTrackDoesNotGenerateTurns() {
        val noisy = track(*(0..200).map { point(if (it % 2 == 0) -2.0 else 2.0, it * 2.0) }.toTypedArray())
        val calculator = TrackGuidanceCalculator(noisy)
        assertTrue(calculator.maneuvers(0).isEmpty())
        assertEquals(Maneuver.STRAIGHT, update(calculator, TrackProgressCalculator(noisy), 0.0, 100.0).maneuver)
    }

    @Test fun groupsSmoothCurveAndHairpinInsteadOfShowingSeveralSmallTurns() {
        val curve = TrackGuidanceCalculator(curvedTrack(40.0, 90)).maneuvers(0).single()
        assertEquals(Maneuver.RIGHT, curve.maneuver)
        assertEquals(90.0, curve.angleDegrees, 1.0)
        assertEquals(Maneuver.HAIRPIN_RIGHT,
            TrackGuidanceCalculator(curvedTrack(12.0, 180)).maneuvers(0).single().maneuver)
    }

    @Test fun preservesOppositeTurnsAndDistinctSharpCorners() {
        val sCurve = track(point(0.0, 0.0), point(0.0, 300.0), point(70.0, 300.0), point(70.0, 600.0))
        assertEquals(listOf(Maneuver.RIGHT, Maneuver.LEFT),
            TrackGuidanceCalculator(sCurve).maneuvers(0).map { it.maneuver })
        val consecutive = track(point(0.0, 0.0), point(0.0, 300.0), point(30.0, 300.0), point(30.0, 0.0))
        assertEquals(listOf(Maneuver.RIGHT, Maneuver.RIGHT),
            TrackGuidanceCalculator(consecutive).maneuvers(0).map { it.maneuver })
    }

    @Test fun announcesWithinTwoHundredMetersAndDoesNotReshowPassedTurn() {
        val track = corner(90.0)
        val calculator = TrackGuidanceCalculator(track)
        val progress = TrackProgressCalculator(track)
        assertEquals(Maneuver.STRAIGHT, update(calculator, progress, 0.0, 75.0).maneuver)
        val approach = update(calculator, progress, 0.0, 110.0)
        assertEquals(Maneuver.RIGHT, approach.maneuver)
        assertEquals(190.0, approach.distanceMeters!!, 0.1)
        assertEquals(Maneuver.RIGHT, update(calculator, progress, 0.0, 95.0).maneuver)
        assertEquals(Maneuver.RIGHT, update(calculator, progress, 5.0, 300.0).maneuver)
        assertEquals(Maneuver.STRAIGHT, update(calculator, progress, 20.0, 300.0).maneuver)
        assertEquals(Maneuver.STRAIGHT, update(calculator, progress, 5.0, 300.0).maneuver)
        assertEquals(GuidanceStatus.TRACK_END, update(calculator, progress, 298.0, 300.0).status)
    }

    @Test fun distancesUseOriginalTrackAndNeverBridgeSeparateSegments() {
        val track = Track(null, listOf(
            TrackSegment(listOf(point(0.0, 0.0), point(0.0, 300.0))),
            TrackSegment(listOf(point(1000.0, 1000.0), point(1300.0, 1000.0), point(1300.0, 1300.0))),
        ))
        val calculator = TrackGuidanceCalculator(track)
        val progress = TrackProgressCalculator(track)
        assertTrue(calculator.maneuvers(0).isEmpty())
        assertEquals(GuidanceStatus.SEGMENT_END, update(calculator, progress, 0.0, 298.0).status)
        val second = update(calculator, progress, 1150.0, 1000.0)
        assertEquals(Maneuver.LEFT, second.maneuver)
        assertEquals(150.0, second.distanceMeters!!, 0.1)
        assertEquals(1, progress.progress(location(1150.0, 1000.0)).matchedSegmentIndex)
        assertEquals(GuidanceStatus.OFF_TRACK, update(calculator, progress, 500.0, 500.0).status)
    }

    @Test fun unreliableFixAndOffTrackHideArrows() {
        val track = corner(90.0)
        val calculator = TrackGuidanceCalculator(track)
        val progress = TrackProgressCalculator(track)
        for (accuracy in listOf(null, Float.NaN, -1f, 51f, 150f)) {
            val guidance = update(calculator, progress, 0.0, 150.0, accuracy)
            assertEquals(GuidanceStatus.WAITING_FOR_GPS, guidance.status)
            assertNull(guidance.maneuver)
        }
        for (status in listOf(OffTrackStatus.CHECKING, OffTrackStatus.OFF_TRACK)) {
            val fix = location(0.0, 150.0)
            val guidance = calculator.update(progress.progress(fix), fix, OffTrackState(status))
            assertEquals(if (status == OffTrackStatus.CHECKING) GuidanceStatus.CHECKING_TRACK
                else GuidanceStatus.OFF_TRACK, guidance.status)
            assertNull(guidance.maneuver)
        }
        assertNull(calculator.update(null, null, OffTrackState()).maneuver)
    }

    @Test fun nearbySecondTurnIsMeasuredAfterFirstAndPromotedWhenFirstIsPassed() {
        val track = track(point(0.0, 0.0), point(0.0, 300.0), point(80.0, 300.0),
            point(80.0, 350.0), point(300.0, 350.0))
        val calculator = TrackGuidanceCalculator(track)
        val progress = TrackProgressCalculator(track)
        val before = update(calculator, progress, 0.0, 180.0)
        assertEquals(Maneuver.RIGHT, before.maneuver)
        assertEquals(120.0, before.distanceMeters!!, 0.1)
        assertEquals(Maneuver.LEFT, before.secondManeuver!!.maneuver)
        assertEquals(80.0, before.secondManeuver!!.distanceAfterMeters, 0.1)
        val after = update(calculator, progress, 20.0, 300.0)
        assertEquals(Maneuver.LEFT, after.maneuver)
        assertEquals(60.0, after.distanceMeters!!, 0.1)
        assertEquals(Maneuver.RIGHT, after.secondManeuver!!.maneuver)
        assertEquals(50.0, after.secondManeuver!!.distanceAfterMeters, 0.1)
    }

    @Test fun secondTurnOnlyAppearsWithAnAnnouncedTurnAndWithinOneHundredMeters() {
        for (gap in listOf(80.0, 100.0, 100.1, 150.0)) {
            val track = track(point(0.0, 0.0), point(0.0, 300.0), point(gap, 300.0), point(gap, 600.0))
            val calculator = TrackGuidanceCalculator(track)
            val progress = TrackProgressCalculator(track)
            val far = update(calculator, progress, 0.0, 50.0)
            assertEquals(Maneuver.STRAIGHT, far.maneuver)
            assertNull(far.secondManeuver)
            val near = update(calculator, progress, 0.0, 180.0)
            if (gap <= 100.0) assertNotNull(near.secondManeuver) else assertNull(near.secondManeuver)
        }
    }

    @Test fun secondTurnNeverComesFromAnotherGpxSegment() {
        val track = Track(null, listOf(
            TrackSegment(listOf(point(0.0, 0.0), point(0.0, 300.0), point(30.0, 300.0))),
            TrackSegment(listOf(point(1000.0, 0.0), point(1000.0, 30.0), point(1030.0, 30.0))),
        ))
        val near = update(TrackGuidanceCalculator(track), TrackProgressCalculator(track), 0.0, 180.0)
        assertEquals(Maneuver.RIGHT, near.maneuver)
        assertNull(near.secondManeuver)
    }

    @Test fun untrustedAndReverseStatesClearTheSecondTurn() {
        val track = track(point(0.0, 0.0), point(0.0, 300.0), point(80.0, 300.0), point(80.0, 600.0))
        val calculator = TrackGuidanceCalculator(track)
        val fix = location(0.0, 180.0)
        val progress = TrackProgressCalculator(track).progress(fix)
        val onTrack = OffTrackState(OffTrackStatus.ON_TRACK)
        assertNotNull(calculator.update(progress, fix, onTrack).secondManeuver)
        assertNull(calculator.update(progress, null, onTrack).secondManeuver)
        assertNull(calculator.update(progress, fix, OffTrackState(OffTrackStatus.CHECKING)).secondManeuver)
        assertNull(calculator.update(progress.copy(matchStatus = TrackMatchStatus.AMBIGUOUS), fix, onTrack).secondManeuver)
        assertNull(calculator.update(progress.copy(travelDirection = TrackTravelDirection.BACKWARD), fix, onTrack).secondManeuver)
    }

    @Test fun handlesEmptyRepeatedIsolatedAndLongTracks() {
        for (track in listOf(Track(null, emptyList()), track(point(0.0, 0.0)),
            track(point(0.0, 0.0), point(0.0, 0.0)))) {
            assertEquals(GuidanceStatus.WAITING_FOR_GPS,
                update(TrackGuidanceCalculator(track), TrackProgressCalculator(track), 0.0, 0.0).status)
        }
        val long = track(*(0 until 50_000).map { point(0.0, it * 2.0) }.toTypedArray())
        assertTrue(TrackGuidanceCalculator(long).maneuvers(0).isEmpty())
    }

    @Test fun ambiguousMatchHidesCueAndConfirmedBacktrackingResetsPassedTurns() {
        val track = corner(90.0)
        val guidance = TrackGuidanceCalculator(track)
        val progress = TrackProgressCalculator(track)
        val onTrack = OffTrackState(OffTrackStatus.ON_TRACK)
        fun at(x: Double, y: Double, seconds: Long, bearing: Float): TrackGuidance {
            val fix = location(x, y).copy(elapsedRealtimeNanos = seconds * 1_000_000_000L,
                speedMetersPerSecond = 5f, bearingDegrees = bearing)
            return guidance.update(progress.progress(fix), fix, onTrack)
        }
        assertEquals(Maneuver.RIGHT, at(0.0, 280.0, 1, 0f).maneuver)
        assertEquals(Maneuver.STRAIGHT, at(35.0, 300.0, 3, 90f).maneuver)
        assertEquals(GuidanceStatus.REVERSE_DIRECTION, at(5.0, 300.0, 5, 270f).status)
        assertEquals(GuidanceStatus.REVERSE_DIRECTION, at(0.0, 270.0, 7, 180f).status)
        assertEquals(Maneuver.RIGHT, at(0.0, 295.0, 9, 0f).maneuver)
        val ambiguous = TrackProgress(600.0, null, null, 0.0, matchStatus = TrackMatchStatus.AMBIGUOUS)
        val hidden = guidance.update(ambiguous, location(0.0, 295.0), onTrack)
        assertEquals(GuidanceStatus.MATCHING_TRACK, hidden.status)
        assertNull(hidden.maneuver)
    }

    @Test fun reacquiringEarlierPartOfTrackRestoresAnUpcomingPassedCue() {
        val track = corner(90.0)
        val guidance = TrackGuidanceCalculator(track)
        val progress = TrackProgressCalculator(track)
        update(guidance, progress, 50.0, 300.0)
        val fix = location(0.0, 250.0)
        val reacquired = progress.progress(fix).copy(matchGeneration = 1)
        assertEquals(Maneuver.RIGHT, guidance.update(reacquired, fix, OffTrackState(OffTrackStatus.ON_TRACK)).maneuver)
    }
}
