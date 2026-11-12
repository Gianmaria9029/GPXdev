package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import org.junit.Assert.*
import org.junit.Test

class NavigationProximityTest {
    private fun point(x: Double, y: Double) = TrackPoint(
        Math.toDegrees(y / EARTH_RADIUS_METERS), Math.toDegrees(x / EARTH_RADIUS_METERS))
    private val track = Track(null, listOf(TrackSegment(listOf(point(0.0, 0.0), point(0.0, 500.0)))))
    private fun fix(offset: Double, second: Long, accuracy: Float? = 5f) = point(offset, 200.0).let {
        LocationPoint(it.latitude, it.longitude, accuracy, 5f, 0f, null, second * 1_000_000_000L)
    }

    @Test fun matchingGuidanceAndArrivalAgreeAcrossTheAccuracyDependentCorridor() {
        for ((accuracy, offset, inside) in listOf(
            Triple(5f, 35.0, true), Triple(5f, 39.9, true), Triple(5f, 40.1, false),
            Triple(20f, 39.9, true), Triple(20f, 40.1, false),
            Triple(40f, 79.9, true), Triple(40f, 80.1, false),
            Triple(50f, 99.9, true), Triple(50f, 100.1, false),
        )) {
            val calculator = TrackProgressCalculator(track)
            val monitor = OffTrackMonitor()
            val guidanceCalculator = TrackGuidanceCalculator(track)
            val estimator = ArrivalTimeEstimator(track)
            for (second in listOf(1L, 4L, 7L)) {
                val location = fix(offset, second, accuracy)
                val progress = calculator.progress(location)
                val offTrack = monitor.update(progress.distanceFromTrackMeters, accuracy, second * 1_000, second * 1_000)
                val guidance = guidanceCalculator.update(progress, location, offTrack)
                val arrival = estimator.update(progress, location, offTrack, guidance, second * 1_000, 100_000)
                if (inside) {
                    assertEquals(TrackMatchStatus.MATCHED, progress.matchStatus)
                    assertEquals(OffTrackStatus.ON_TRACK, offTrack.status)
                    assertEquals(GuidanceStatus.ACTIVE, guidance.status)
                    assertNotNull(progress.remainingDistanceMeters)
                    assertEquals(ArrivalStatus.CALCULATING, arrival.status)
                } else {
                    assertEquals(TrackMatchStatus.OFF_TRACK, progress.matchStatus)
                    assertEquals(if (second < 7) OffTrackStatus.CHECKING else OffTrackStatus.OFF_TRACK, offTrack.status)
                    assertEquals(if (second < 7) GuidanceStatus.CHECKING_TRACK else GuidanceStatus.OFF_TRACK, guidance.status)
                    assertEquals(ArrivalStatus.OFF_TRACK, arrival.status)
                    assertNull(progress.remainingDistanceMeters)
                }
            }
        }
    }

    @Test fun confirmedAlertKeepsGuidanceSuspendedUntilTheNarrowerReturnThreshold() {
        val calculator = TrackProgressCalculator(track)
        val monitor = OffTrackMonitor()
        val guidanceCalculator = TrackGuidanceCalculator(track)
        val estimator = ArrivalTimeEstimator(track)
        fun at(offset: Double, second: Long): Pair<TrackGuidance, ArrivalEstimate> {
            val location = fix(offset, second)
            val progress = calculator.progress(location)
            val offTrack = monitor.update(progress.distanceFromTrackMeters, 5f, second * 1_000, second * 1_000)
            val guidance = guidanceCalculator.update(progress, location, offTrack)
            return guidance to estimator.update(progress, location, offTrack, guidance, second * 1_000, 100_000)
        }
        at(60.0, 1)
        at(60.0, 4)
        assertEquals(GuidanceStatus.OFF_TRACK, at(60.0, 7).first.status)
        for (second in listOf(9L, 11L, 13L)) {
            val (guidance, arrival) = at(35.0, second)
            assertEquals(GuidanceStatus.OFF_TRACK, guidance.status)
            assertNull(guidance.maneuver)
            assertEquals(ArrivalStatus.OFF_TRACK, arrival.status)
        }
        assertEquals(GuidanceStatus.ACTIVE, at(24.0, 15).first.status)
    }

    @Test fun returningFromOffTrackNeedsMatchingEvidenceRatherThanAMorePreciseGps() {
        val calculator = TrackProgressCalculator(track)
        val monitor = OffTrackMonitor()
        val guidanceCalculator = TrackGuidanceCalculator(track)
        fun at(offset: Double, second: Long): GuidanceStatus {
            val location = fix(offset, second)
            val progress = calculator.progress(location)
            val offTrack = monitor.update(progress.distanceFromTrackMeters, 5f, second * 1_000, second * 1_000)
            return guidanceCalculator.update(progress, location, offTrack).status
        }
        assertEquals(GuidanceStatus.ACTIVE, at(0.0, 1))
        at(60.0, 4)
        at(60.0, 7)
        assertEquals(GuidanceStatus.OFF_TRACK, at(60.0, 10))
        assertEquals(GuidanceStatus.MATCHING_TRACK, at(20.0, 12))
        assertEquals(GuidanceStatus.MATCHING_TRACK, at(20.0, 14))
        assertEquals(GuidanceStatus.ACTIVE, at(20.0, 16))
    }

    @Test fun missingOrPoorAccuracyConsistentlyWaitsForGps() {
        for (accuracy in listOf(null, Float.NaN, -1f, 51f)) {
            val location = fix(35.0, 1, accuracy)
            val progress = TrackProgressCalculator(track).progress(location)
            val offTrack = OffTrackMonitor().update(progress.distanceFromTrackMeters, accuracy, 1_000, 1_000)
            val guidance = TrackGuidanceCalculator(track).update(progress, location, offTrack)
            assertEquals(TrackMatchStatus.UNAVAILABLE, progress.matchStatus)
            assertEquals(OffTrackStatus.UNAVAILABLE, offTrack.status)
            assertEquals(GuidanceStatus.WAITING_FOR_GPS, guidance.status)
            assertEquals(ArrivalStatus.WAITING_FOR_GPS,
                ArrivalTimeEstimator(track).update(progress, location, offTrack, guidance, 1_000, 100_000).status)
        }
    }

    @Test fun spatialOffTrackCannotBeMislabelledAsPoorGpsWhenMonitorIsUnavailable() {
        val location = fix(60.0, 1)
        val progress = TrackProgressCalculator(track).progress(location)
        val offTrack = OffTrackState()
        val guidance = TrackGuidanceCalculator(track).update(progress, location, offTrack)
        assertEquals(GuidanceStatus.OFF_TRACK, guidance.status)
        assertEquals(ArrivalStatus.OFF_TRACK,
            ArrivalTimeEstimator(track).update(progress, location, offTrack, guidance, 1_000, 100_000).status)
    }
}
