package com.gpxnavigator.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class RecordingElapsedTimeTest {
    @Test fun clockUsesTheRecordingStartAndDoesNotJumpWhenWallTimeChanges() {
        val clock = RecordingElapsedClock()
        assertEquals(30_000L, clock.elapsedMillis("giro", 100_000, 130_000, 5_000))
        assertEquals(35_000L, clock.elapsedMillis("giro", 100_000, 9_000_000, 10_000))
        assertEquals(40_000L, clock.elapsedMillis("giro", 100_000, 1, 15_000))
    }

    @Test fun returningOrResumingKeepsTimeSinceTheOriginalStartAndANewRecordingStartsAgain() {
        val clock = RecordingElapsedClock()
        assertEquals(5_000L, clock.elapsedMillis("first", 100_000, 105_000, 10_000))
        assertEquals(125_000L, clock.elapsedMillis("first", 100_000, 225_000, 130_000))
        assertEquals(125_000L, RecordingElapsedClock().elapsedMillis("first", 100_000, 225_000, 5_000))
        assertEquals(0L, clock.elapsedMillis("second", 230_000, 230_000, 135_000))
        clock.clear()
        assertEquals(10_000L, clock.elapsedMillis("second", 230_000, 240_000, 145_000))
    }

    @Test fun missingStartIsUnavailableAndFutureStartDoesNotProduceNegativeTime() {
        val clock = RecordingElapsedClock()
        assertNull(clock.elapsedMillis("giro", 0, 100_000, 5_000))
        assertEquals(0L, clock.elapsedMillis("giro", 120_000, 100_000, 5_000))
        assertEquals(1_000L, clock.elapsedMillis("giro", 120_000, 101_000, 6_000))
    }

    @Test fun durationHasSecondsMinutesAndUnboundedHours() {
        assertEquals("00:00:00", recordingElapsedText(0))
        assertEquals("00:00:59", recordingElapsedText(59_999))
        assertEquals("01:02:03", recordingElapsedText(3_723_000))
        assertEquals("100:00:00", recordingElapsedText(360_000_000))
        assertNull(recordingElapsedText(null))
        assertNull(recordingElapsedText(-1))
    }
}
