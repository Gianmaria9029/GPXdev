package com.gpxnavigator.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OffTrackMonitorTest {
    private val monitor = OffTrackMonitor()

    private fun update(distance: Double?, time: Long, accuracy: Float? = 5f, now: Long = time) =
        monitor.update(distance, accuracy, time, now)

    private fun confirmOffTrack() {
        update(80.0, 0)
        update(80.0, 3_000)
        assertEquals(OffTrackStatus.OFF_TRACK, update(80.0, 6_000).status)
    }

    @Test fun nearbyPositionDoesNotRaiseAnAlert() {
        val state = update(12.0, 0)
        assertEquals(OffTrackStatus.ON_TRACK, state.status)
        assertEquals(12.0, state.distanceMeters!!, 0.0)
    }

    @Test fun sustainedDistanceRequiresBothTimeAndDistinctFixes() {
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 0).status)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 1_000).status)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 2_000).status)
        assertEquals(OffTrackStatus.OFF_TRACK, update(80.0, 5_000).status)
        monitor.reset()
        update(80.0, 0)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 6_000).status)
        assertEquals(OffTrackStatus.OFF_TRACK, update(80.0, 8_000).status)
    }

    @Test fun duplicateAndOutOfOrderFixesCannotConfirmAnAlert() {
        update(80.0, 2_000)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 2_000, now = 8_000).status)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 1_000, now = 8_000).status)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 8_000).status)
    }

    @Test fun transientGpsJumpIsDiscardedAfterReturningNearTrack() {
        update(80.0, 0)
        assertEquals(OffTrackStatus.ON_TRACK, update(10.0, 2_000).status)
        update(80.0, 4_000)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 6_000).status)
        assertEquals(OffTrackStatus.OFF_TRACK, update(80.0, 10_000).status)
    }

    @Test fun differentExitAndReturnThresholdsAvoidFlickering() {
        confirmOffTrack()
        assertEquals(OffTrackStatus.OFF_TRACK, update(35.0, 8_000).status)
        assertEquals(OffTrackStatus.ON_TRACK, update(24.0, 10_000).status)
        assertEquals(OffTrackStatus.ON_TRACK, update(35.0, 12_000).status)
    }

    @Test fun thresholdGrowsWithGpsUncertainty() {
        assertEquals(OffTrackStatus.ON_TRACK, update(70.0, 0, accuracy = 40f).status)
        update(90.0, 2_000, accuracy = 40f)
        update(90.0, 5_000, accuracy = 40f)
        assertEquals(OffTrackStatus.OFF_TRACK, update(90.0, 8_000, accuracy = 40f).status)
        assertEquals(OffTrackStatus.OFF_TRACK, update(65.0, 10_000, accuracy = 40f).status)
        assertEquals(OffTrackStatus.ON_TRACK, update(59.0, 12_000, accuracy = 40f).status)
    }

    @Test fun MissingOrPoorAccuracyNeverRaisesAnAlert() {
        for (accuracy in listOf(null, 51f, Float.NaN, -1f)) {
            confirmOffTrack()
            val state = update(80.0, 8_000, accuracy)
            assertEquals(OffTrackStatus.UNAVAILABLE, state.status)
            assertNull(state.distanceMeters)
        }
    }

    @Test fun missingDistanceAndStaleFixClearAnOldAlert() {
        confirmOffTrack()
        assertEquals(OffTrackStatus.UNAVAILABLE, update(null, 8_000).status)
        confirmOffTrack()
        assertEquals(OffTrackStatus.UNAVAILABLE, update(80.0, 6_000, now = 20_000).status)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 20_000).status)
    }

    @Test fun longGpsGapRequiresNewConfirmation() {
        confirmOffTrack()
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 20_000).status)
        update(80.0, 23_000)
        assertEquals(OffTrackStatus.OFF_TRACK, update(80.0, 26_000).status)
    }

    @Test fun resetForANewTrackDiscardsPreviousConfirmation() {
        confirmOffTrack()
        assertEquals(OffTrackStatus.UNAVAILABLE, monitor.reset().status)
        assertEquals(OffTrackStatus.CHECKING, update(80.0, 8_000).status)
        assertEquals(OffTrackStatus.UNAVAILABLE, update(Double.NaN, 10_000).status)
    }
}
