package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceAnticipationTest {
    private fun fix(speed: Float?, seconds: Long, accuracy: Float? = 5f) =
        LocationPoint(43.0, 11.0, accuracy, speed, null, null, seconds * 1_000_000_000L)

    @Test fun twelveSecondsOfLeadAreClampedAtLowAndHighSpeeds() {
        for ((speed, distance) in listOf(0f to 80.0, (20f / 3.6f) to 80.0,
            (50f / 3.6f) to (500.0 / 3), 25f to 300.0, (110f / 3.6f) to 350.0, 90f to 350.0)) {
            assertEquals(distance, GuidanceAnticipation().update(fix(speed, 1)), 0.001)
        }
    }

    @Test fun isolatedSpeedSpikeIsRejectedButSustainedAccelerationIsAccepted() {
        val filter = GuidanceAnticipation()
        assertEquals(120.0, filter.update(fix(10f, 1)), 0.0)
        assertEquals(120.0, filter.update(fix(10f, 2)), 0.0)
        assertEquals(120.0, filter.update(fix(30f, 3)), 0.0)
        assertEquals(350.0, filter.update(fix(30f, 4)), 0.0)
    }

    @Test fun sustainedDecelerationAndStoppingReduceTheNextAnnouncementDistance() {
        val filter = GuidanceAnticipation()
        filter.update(fix(25f, 1))
        filter.update(fix(25f, 2))
        assertEquals(300.0, filter.update(fix(0f, 3)), 0.0)
        assertEquals(80.0, filter.update(fix(0f, 4)), 0.0)
    }

    @Test fun duplicateAndOutOfOrderFixesCannotConfirmAnIsolatedSpike() {
        val filter = GuidanceAnticipation()
        filter.update(fix(10f, 1))
        filter.update(fix(10f, 2))
        filter.update(fix(30f, 3))
        repeat(10) { assertEquals(120.0, filter.update(fix(30f, 3)), 0.0) }
        assertEquals(120.0, filter.update(fix(30f, 2)), 0.0)
        assertEquals(120.0, filter.update(fix(10f, 4)), 0.0)
    }

    @Test fun missingInvalidOrUntimedSpeedFallsBackWithoutKeepingOldSamples() {
        for (speed in listOf(null, -1f, Float.NaN, Float.POSITIVE_INFINITY, 91f)) {
            val filter = GuidanceAnticipation()
            filter.update(fix(25f, 1))
            assertEquals(200.0, filter.update(fix(speed, 2)), 0.0)
            assertEquals(80.0, filter.update(fix(0f, 3)), 0.0)
        }
        for (time in listOf(null, -1L)) {
            val filter = GuidanceAnticipation()
            filter.update(fix(25f, 1))
            assertEquals(200.0, filter.update(fix(25f, 2).copy(elapsedRealtimeNanos = time)), 0.0)
        }
    }

    @Test fun impreciseLocationUsesFallbackEvenWithAValidSpeed() {
        for (accuracy in listOf(null, -1f, Float.NaN, Float.POSITIVE_INFINITY, 21f, 50f))
            assertEquals(200.0, GuidanceAnticipation().update(fix(25f, 1, accuracy)), 0.0)
        assertEquals(300.0, GuidanceAnticipation().update(fix(25f, 1, 20f)), 0.0)
    }

    @Test fun oldSamplesExpireAfterGpsGap() {
        val filter = GuidanceAnticipation()
        filter.update(fix(25f, 1))
        filter.update(fix(25f, 2))
        assertEquals(80.0, filter.update(fix(0f, 8)), 0.0)
    }

    @Test fun resetStartsWithTheNewSpeed() {
        val filter = GuidanceAnticipation()
        filter.update(fix(25f, 1))
        filter.update(fix(25f, 2))
        filter.reset()
        assertEquals(80.0, filter.update(fix(0f, 3)), 0.0)
    }
}
