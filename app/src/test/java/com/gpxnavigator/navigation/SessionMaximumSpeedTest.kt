package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import org.junit.Assert.*
import org.junit.Test

class SessionMaximumSpeedTest {
    private val tracker = SessionMaximumSpeed(100_000L)

    private fun point(speed: Float? = 20f, time: Long = 101_000L, accuracy: Float? = 5f) =
        LocationPoint(43.77, 11.25, accuracy, speed, null, null, time * 1_000_000L)

    @Test fun keepsMaximumThroughSlowerFixesAndSignalLoss() {
        assertEquals(20f, tracker.update(point(), 101_000L))
        assertEquals(20f, tracker.update(point(10f, 102_000L), 102_000L))
        assertEquals(20f, tracker.update(null, 103_000L))
        assertEquals(30f, tracker.update(point(30f, 104_000L), 104_000L))
    }

    @Test fun rejectsOldFutureDuplicateAndOutOfOrderFixes() {
        assertNull(tracker.update(point(time = 99_000L), 101_000L))
        assertNull(tracker.update(point(time = 102_000L), 101_000L))
        assertNull(tracker.update(point(), 101_001L + MAX_OFF_TRACK_FIX_AGE_MILLIS))
        assertEquals(20f, tracker.update(point(), 101_000L))
        assertEquals(20f, tracker.update(point(80f), 101_000L))
        assertEquals(20f, tracker.update(point(80f, 100_500L), 101_000L))
    }

    @Test fun ignoresMissingInvalidAndImpreciseSpeedSamples() {
        val invalid = listOf(point(null), point(Float.NaN), point(Float.POSITIVE_INFINITY),
            point(-1f), point(91f), point(accuracy = null), point(accuracy = Float.NaN),
            point(accuracy = -1f), point(accuracy = 51f), point().copy(elapsedRealtimeNanos = null))
        for (sample in invalid) {
            assertNull(SessionMaximumSpeed(100_000L).update(sample, 101_000L))
        }
    }

    @Test fun resetClearsMaximumAndZeroIsARealMeasurement() {
        tracker.update(point(), 101_000L)
        tracker.reset()
        assertNull(tracker.update(null, 102_000L))
        assertEquals(0f, tracker.update(point(0f, 102_000L), 102_000L))
    }

    @Test fun cachedFixInTheStartingMillisecondCannotBecomeTheMaximum() {
        assertNull(tracker.update(point(70f, 100_000L), 100_001L))
        assertEquals(5f, tracker.update(point(5f, 100_001L), 100_001L))
    }
}
