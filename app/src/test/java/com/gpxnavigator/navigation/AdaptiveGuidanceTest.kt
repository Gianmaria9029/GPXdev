package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.ui.navigation.maneuverApproachFraction
import org.junit.Assert.*
import org.junit.Test

class AdaptiveGuidanceTest {
    private fun point(x: Double, y: Double) = TrackPoint(
        Math.toDegrees(y / EARTH_RADIUS_METERS), Math.toDegrees(x / EARTH_RADIUS_METERS))
    private val track = Track(null, listOf(TrackSegment(listOf(
        point(0.0, 0.0), point(0.0, 1000.0), point(80.0, 1000.0), point(80.0, 2000.0)))))
    private val onTrack = OffTrackState(OffTrackStatus.ON_TRACK)
    private fun fix(travelled: Double, speed: Float?, seconds: Long): LocationPoint {
        val point = when {
            travelled <= 1000 -> point(0.0, travelled)
            travelled <= 1080 -> point(travelled - 1000, 1000.0)
            else -> point(80.0, travelled - 80)
        }
        return LocationPoint(point.latitude, point.longitude, 5f, speed, null, null,
            seconds * 1_000_000_000L)
    }
    private fun progress(travelled: Double, generation: Long = 0) =
        TrackProgress(2080.0, travelled, 2080.0 - travelled, 0.0, 0, matchGeneration = generation)
    private fun at(calculator: TrackGuidanceCalculator, travelled: Double, speed: Float?, seconds: Long) =
        calculator.update(progress(travelled), fix(travelled, speed, seconds), onTrack)

    @Test fun actualGpxProjectionAnnouncesAtTheSpeedDependentThreshold() {
        for ((speed, threshold) in listOf(0f to 80.0, (50f / 3.6f) to (500.0 / 3),
            25f to 300.0, 35f to 350.0)) {
            for (offset in listOf(-2.0, 2.0)) {
                val position = fix(1000 - threshold + offset, speed, 1)
                val result = TrackGuidanceCalculator(track).update(
                    TrackProgressCalculator(track).progress(position), position, onTrack)
                assertEquals(if (offset < 0) Maneuver.STRAIGHT else Maneuver.RIGHT, result.maneuver)
                if (offset < 0) assertNull(result.announcementDistanceMeters)
                else assertEquals(threshold, result.announcementDistanceMeters!!, 0.001)
            }
        }
    }

    @Test fun brakingMissingSpeedAndAccelerationDoNotChangeAnAnnouncedBarsScale() {
        val calculator = TrackGuidanceCalculator(track)
        val first = at(calculator, 710.0, 25f, 1)
        assertEquals(Maneuver.RIGHT, first.maneuver)
        assertEquals(300.0, first.announcementDistanceMeters!!, 0.0)
        var lastFraction = maneuverApproachFraction(first)!!
        for ((index, speed) in listOf(0f, 0f, null, 35f, 35f).withIndex()) {
            val cue = at(calculator, 720.0 + index * 10, speed, index + 2L)
            assertEquals(Maneuver.RIGHT, cue.maneuver)
            assertEquals(300.0, cue.announcementDistanceMeters!!, 0.0)
            assertTrue(maneuverApproachFraction(cue)!! > lastFraction)
            lastFraction = maneuverApproachFraction(cue)!!
        }
    }

    @Test fun accelerationBringsTheNextCurveIntoViewAfterConfirmation() {
        val calculator = TrackGuidanceCalculator(track)
        assertEquals(Maneuver.STRAIGHT, at(calculator, 700.0, 5f, 1).maneuver)
        assertEquals(Maneuver.STRAIGHT, at(calculator, 705.0, 5f, 2).maneuver)
        assertEquals(Maneuver.STRAIGHT, at(calculator, 710.0, 25f, 3).maneuver)
        val cue = at(calculator, 735.0, 25f, 4)
        assertEquals(Maneuver.RIGHT, cue.maneuver)
        assertEquals(300.0, cue.announcementDistanceMeters!!, 0.0)
    }

    @Test fun missingSpeedBeforeAnnouncementUsesTwoHundredMeters() {
        val calculator = TrackGuidanceCalculator(track)
        assertEquals(Maneuver.STRAIGHT, at(calculator, 790.0, 5f, 1).maneuver)
        assertEquals(Maneuver.STRAIGHT, at(calculator, 795.0, null, 2).maneuver)
        val cue = at(calculator, 810.0, null, 3)
        assertEquals(Maneuver.RIGHT, cue.maneuver)
        assertEquals(200.0, cue.announcementDistanceMeters!!, 0.0)
    }

    @Test fun nextCurveGetsItsOwnScaleAndPassedCurveDoesNotReturn() {
        val calculator = TrackGuidanceCalculator(track)
        val first = at(calculator, 710.0, 25f, 1)
        assertEquals(Maneuver.LEFT, first.secondManeuver!!.maneuver)
        assertEquals(80.0, first.secondManeuver!!.distanceAfterMeters, 0.1)
        at(calculator, 900.0, 0f, 2)
        at(calculator, 1005.0, 0f, 3)
        val second = at(calculator, 1020.0, 0f, 4)
        assertEquals(Maneuver.LEFT, second.maneuver)
        assertEquals(80.0, second.announcementDistanceMeters!!, 0.0)
        assertEquals(0.25f, maneuverApproachFraction(second)!!, 0.001f)
        assertEquals(Maneuver.LEFT, at(calculator, 1005.0, 0f, 5).maneuver)
        val straight = at(calculator, 1100.0, 0f, 6)
        assertEquals(Maneuver.STRAIGHT, straight.maneuver)
        assertNull(straight.announcementDistanceMeters)
    }

    @Test fun reacquiringTrackClearsAnEarlierAnnouncementAndSpeedHistory() {
        val calculator = TrackGuidanceCalculator(track)
        at(calculator, 710.0, 25f, 1)
        at(calculator, 720.0, 25f, 2)
        val cue = calculator.update(progress(750.0, generation = 1), fix(750.0, 0f, 3), onTrack)
        assertEquals(Maneuver.STRAIGHT, cue.maneuver)
        assertNull(cue.announcementDistanceMeters)
        assertEquals(80.0, at(calculator, 930.0, 0f, 4).announcementDistanceMeters!!, 0.0)
    }

    @Test fun untrustedStatesHideTheBarAndClearSpeedHistory() {
        val calculator = TrackGuidanceCalculator(track)
        at(calculator, 710.0, 25f, 1)
        val p = progress(720.0)
        val location = fix(720.0, 25f, 2)
        val hidden = listOf(
            calculator.update(p, null, onTrack),
            calculator.update(p, location.copy(accuracyMeters = 100f), onTrack),
            calculator.update(p, location, OffTrackState(OffTrackStatus.CHECKING)),
            calculator.update(p, location, OffTrackState(OffTrackStatus.OFF_TRACK)),
            calculator.update(p.copy(matchStatus = TrackMatchStatus.AMBIGUOUS), location, onTrack),
            calculator.update(p.copy(travelDirection = TrackTravelDirection.BACKWARD), location, onTrack),
        )
        hidden.forEach {
            assertNull(it.maneuver)
            assertNull(it.announcementDistanceMeters)
            assertNull(maneuverApproachFraction(it))
        }
        assertEquals(Maneuver.STRAIGHT, at(calculator, 750.0, 0f, 3).maneuver)
    }

    @Test fun separateSegmentStartsWithItsOwnSpeedAndAnnouncement() {
        val multi = Track(null, track.segments + TrackSegment(listOf(
            point(3000.0, 0.0), point(3000.0, 1000.0), point(4000.0, 1000.0))))
        val calculator = TrackGuidanceCalculator(multi)
        at(calculator, 710.0, 25f, 1)
        at(calculator, 720.0, 25f, 2)
        val point = point(3000.0, 750.0)
        val cue = calculator.update(
            TrackProgress(4080.0, 2830.0, 1250.0, 0.0, 1),
            fix(750.0, 0f, 3).copy(latitude = point.latitude, longitude = point.longitude), onTrack)
        assertEquals(Maneuver.STRAIGHT, cue.maneuver)
        assertNull(cue.announcementDistanceMeters)
    }
}
