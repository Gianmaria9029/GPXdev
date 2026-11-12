package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import org.junit.Assert.*
import org.junit.Test

class ArrivalTimeEstimatorTest {
    private val wallBase = 1_700_000_000_000L
    private fun point(meters: Double) = TrackPoint(Math.toDegrees(meters / EARTH_RADIUS_METERS), 0.0)
    private fun route(length: Double = 20_000.0) = Track(null,
        listOf(TrackSegment(listOf(point(0.0), point(length)))))

    private fun sample(estimator: ArrivalTimeEstimator, seconds: Long, distance: Double = seconds * 10.0,
        speed: Float? = 10f, accuracy: Float? = 5f, generation: Long = 0, segment: Int = 0,
        match: TrackMatchStatus = TrackMatchStatus.MATCHED, direction: TrackTravelDirection = TrackTravelDirection.FORWARD,
        offTrack: OffTrackStatus = OffTrackStatus.ON_TRACK, guidance: GuidanceStatus = GuidanceStatus.ACTIVE,
        wall: Long = wallBase + seconds * 1000, total: Double = 20_000.0): ArrivalEstimate {
        val point = point(distance)
        val time = (seconds + 1) * 1000
        val fix = LocationPoint(point.latitude, point.longitude, accuracy, speed, 0f, null, time * 1_000_000L)
        val progress = TrackProgress(total, distance, total - distance, 0.0, segment, match, direction, generation)
        return estimator.update(progress, fix, OffTrackState(offTrack), TrackGuidance(guidance), time, wall)
    }

    private fun ready(estimator: ArrivalTimeEstimator, until: Long = 60): ArrivalEstimate {
        var estimate = ArrivalEstimate()
        for (second in 0L..until step 2) estimate = sample(estimator, second)
        assertEquals(ArrivalStatus.READY, estimate.status)
        return estimate
    }

    @Test fun learnsStablePaceAndUsesRemainingGpxDistanceForTimeAndArrival() {
        val estimator = ArrivalTimeEstimator(route())
        for (second in 0L..28L step 2) assertEquals(ArrivalStatus.CALCULATING, sample(estimator, second).status)
        val first = sample(estimator, 30)
        assertEquals(ArrivalStatus.READY, first.status)
        assertEquals(10.0, first.referenceSpeedMetersPerSecond!!, 0.001)
        assertEquals(1970.0, first.remainingSeconds!!.toDouble(), 1.0)
        assertEquals((wallBase + 2_000_000).toDouble(), first.arrivalTimeMillis!!.toDouble(), 1000.0)
        for (second in 32L..60L step 2) {
            val estimate = sample(estimator, second)
            assertEquals(first.arrivalTimeMillis!!.toDouble(), estimate.arrivalTimeMillis!!.toDouble(), 1000.0)
        }
    }

    @Test fun requiresBothThirtyUsefulSecondsAndOneHundredMeters() {
        val estimator = ArrivalTimeEstimator(route())
        for (second in 0L..48L step 2)
            assertEquals(ArrivalStatus.CALCULATING, sample(estimator, second, second * 2.0, speed = 2f).status)
        var estimate = ArrivalEstimate()
        for (second in 50L..54L step 2) estimate = sample(estimator, second, second * 2.0, speed = 2f)
        assertEquals(ArrivalStatus.READY, estimate.status)
        assertEquals(2.0, estimate.referenceSpeedMetersPerSecond!!, 0.001)
    }

    @Test fun confirmedStopHidesTimesAndDoesNotDilutePaceOnResume() {
        val estimator = ArrivalTimeEstimator(route())
        ready(estimator)
        var stopped = ArrivalEstimate()
        for (second in 62L..72L step 2) stopped = sample(estimator, second, 600.0, speed = 0f)
        assertEquals(ArrivalStatus.PAUSED, stopped.status)
        assertNull(stopped.remainingSeconds)
        assertNull(stopped.arrivalTimeMillis)
        val resumed = sample(estimator, 74, 620.0)
        assertEquals(ArrivalStatus.READY, resumed.status)
        assertEquals(10.0, resumed.referenceSpeedMetersPerSecond!!, 0.001)
    }

    @Test fun briefSlowdownCountsButLongStopAgesOutOldPace() {
        val estimator = ArrivalTimeEstimator(route())
        ready(estimator)
        for (second in 62L..66L step 2) {
            val brief = sample(estimator, second, 600.0, speed = 0f)
            assertEquals(ArrivalStatus.READY, brief.status)
        }
        assertTrue(sample(estimator, 68, 620.0).referenceSpeedMetersPerSecond!! < 10.0)
        for (second in 70L..250L step 2) sample(estimator, second, 620.0, speed = 0f)
        assertEquals(ArrivalStatus.CALCULATING, sample(estimator, 252, 640.0).status)
        var resumed = ArrivalEstimate()
        for (second in 254L..282L step 2) resumed = sample(estimator, second, 640.0 + (second - 252) * 10)
        assertEquals(ArrivalStatus.READY, resumed.status)
    }

    @Test fun stationaryGpsJitterWithoutReportedSpeedDoesNotInventTravel() {
        val estimator = ArrivalTimeEstimator(route())
        var estimate = ArrivalEstimate()
        for (second in 0L..40L step 2) {
            val jitter = when (second % 6) { 0L -> 3.0; 2L -> 0.0; else -> 5.0 }
            estimate = sample(estimator, second, 300.0 + jitter, speed = null)
            assertNull(estimate.arrivalTimeMillis)
        }
        assertEquals(ArrivalStatus.PAUSED, estimate.status)
    }

    @Test fun estimatesWithoutGpsSpeedAndIgnoresInstantaneousSpeedSpikes() {
        val estimator = ArrivalTimeEstimator(route())
        var estimate = ArrivalEstimate()
        for (second in 0L..60L step 2) estimate = sample(estimator, second, speed = null)
        assertEquals(ArrivalStatus.READY, estimate.status)
        assertEquals(10.0, estimate.referenceSpeedMetersPerSecond!!, 0.001)
        val spike = sample(estimator, 62, speed = 80f)
        assertEquals(10.0, spike.referenceSpeedMetersPerSecond!!, 0.001)
    }

    @Test fun paceChangeAffectsAverageGraduallyWithMoreWeightOnRecentMovement() {
        val estimator = ArrivalTimeEstimator(route())
        ready(estimator, 180)
        val firstFaster = sample(estimator, 186, 1920.0, speed = 20f)
        assertTrue(firstFaster.referenceSpeedMetersPerSecond!! in 10.0..14.0)
        var later = firstFaster
        for (second in 188L..240L step 2) later = sample(estimator, second, 1800.0 + (second - 180) * 20, speed = 20f)
        assertTrue(later.referenceSpeedMetersPerSecond!! > firstFaster.referenceSpeedMetersPerSecond!!)
        assertTrue(later.referenceSpeedMetersPerSecond!! < 20.0)
    }

    @Test fun ambiguousOffTrackReverseAndPoorFixesHideOldEstimate() {
        for (kind in 0..4) {
            val estimator = ArrivalTimeEstimator(route())
            ready(estimator)
            val unavailable = when (kind) {
                0 -> sample(estimator, 62, match = TrackMatchStatus.AMBIGUOUS)
                1 -> sample(estimator, 62, offTrack = OffTrackStatus.CHECKING)
                2 -> sample(estimator, 62, direction = TrackTravelDirection.BACKWARD)
                3 -> sample(estimator, 62, accuracy = 100f)
                else -> estimator.update(null, null, OffTrackState(), TrackGuidance(), 63_000, wallBase + 62_000)
            }
            assertEquals(when (kind) {
                0 -> ArrivalStatus.MATCHING_TRACK
                1 -> ArrivalStatus.OFF_TRACK
                2 -> ArrivalStatus.REVERSE_DIRECTION
                else -> ArrivalStatus.WAITING_FOR_GPS
            }, unavailable.status)
            assertNull(unavailable.remainingSeconds)
            assertNull(unavailable.arrivalTimeMillis)
        }
    }

    @Test fun reacquisitionJumpAndMissingTimeDoNotBecomeMeasuredSpeed() {
        val estimator = ArrivalTimeEstimator(route())
        ready(estimator)
        estimator.unavailable(ArrivalStatus.WAITING_FOR_GPS, 70_000)
        val jumped = sample(estimator, 80, 5000.0, generation = 1)
        assertEquals(ArrivalStatus.READY, jumped.status)
        assertEquals(10.0, jumped.referenceSpeedMetersPerSecond!!, 0.001)
        assertEquals(1500.0, jumped.remainingSeconds!!.toDouble(), 1.0)
        val afterGap = sample(estimator, 110, 5300.0, generation = 1)
        assertEquals(10.0, afterGap.referenceSpeedMetersPerSecond!!, 0.001)
    }

    @Test fun impossibleProgressSpikeIsRejectedAndDoesNotPolluteAverage() {
        val estimator = ArrivalTimeEstimator(route())
        ready(estimator)
        assertEquals(ArrivalStatus.CALCULATING, sample(estimator, 62, 1600.0).status)
        val normal = sample(estimator, 64, 640.0)
        assertEquals(ArrivalStatus.READY, normal.status)
        assertEquals(10.0, normal.referenceSpeedMetersPerSecond!!, 0.001)
    }

    @Test fun repeatedAndOlderFixesDoNotAddEvidenceOrChangeEstimate() {
        val estimator = ArrivalTimeEstimator(route())
        val previous = ready(estimator)
        assertSame(previous, sample(estimator, 60, 1500.0))
        assertSame(previous, sample(estimator, 59, 2000.0))
    }

    @Test fun wallClockChangeMovesArrivalClockButNotMeasuredDuration() {
        val estimator = ArrivalTimeEstimator(route())
        val initial = ready(estimator)
        val adjusted = sample(estimator, 62, wall = wallBase + 62_000 + 3_600_000)
        assertEquals(10.0, adjusted.referenceSpeedMetersPerSecond!!, 0.001)
        assertEquals((initial.arrivalTimeMillis!! + 3_600_000).toDouble(), adjusted.arrivalTimeMillis!!.toDouble(), 1000.0)
    }

    @Test fun endOfTrackDoesNotNeedWarmupAndNewSessionStartsFromCalculation() {
        val estimator = ArrivalTimeEstimator(route())
        val arrived = sample(estimator, 1, 20_000.0, guidance = GuidanceStatus.TRACK_END)
        assertEquals(ArrivalStatus.ARRIVED, arrived.status)
        assertEquals(0L, arrived.remainingSeconds)
        estimator.reset()
        assertEquals(ArrivalStatus.CALCULATING, sample(estimator, 2, 0.0).status)
    }

    @Test fun futureGapHasNoInventedTravelTimeAndLastContinuousSegmentCanLearnPace() {
        val track = Track(null, listOf(
            TrackSegment(listOf(point(0.0), point(1000.0))),
            TrackSegment(listOf(point(5000.0), point(6000.0))),
        ))
        val estimator = ArrivalTimeEstimator(track)
        assertEquals(ArrivalStatus.DISCONTINUOUS_TRACK, sample(estimator, 0, 0.0, total = 2000.0).status)
        var estimate = ArrivalEstimate()
        for (second in 2L..32L step 2) estimate = sample(estimator, second, 1000.0 + second * 10, segment = 1, total = 2000.0)
        assertEquals(ArrivalStatus.READY, estimate.status)
        assertEquals(10.0, estimate.referenceSpeedMetersPerSecond!!, 0.001)
    }
}
