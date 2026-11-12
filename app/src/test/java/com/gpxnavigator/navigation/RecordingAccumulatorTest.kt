package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.LocationPoint
import org.junit.Assert.*
import org.junit.Test

class RecordingAccumulatorTest {
    private fun point(time: Long, lat: Double = 43.0, lon: Double = 11.0, accuracy: Float = 5f,
        speed: Float? = 2f) = LocationPoint(lat, lon, accuracy, speed, null, null, time * 1_000_000)
    private fun add(acc: RecordingAccumulator, point: LocationPoint) {
        val now = point.elapsedRealtimeNanos!! / 1_000_000
        acc.candidate("r", point, true, now, 1_700_000_000_000L + now)?.let(acc::commit)
    }

    @Test fun storesRealCoordinatesAndDoesNotProjectThemOntoAnItinerary() {
        val acc = RecordingAccumulator()
        add(acc, point(1_000))
        add(acc, point(4_000, lon = 11.0002))
        assertEquals(2, acc.state.pointCount)
        assertTrue(acc.state.distanceMeters > 10)
        assertEquals(11.0002, acc.state.chunks.single().points.last().longitude, 0.0)
        assertNotNull(acc.state.chunks.single().points.last().timestamp)
    }

    @Test fun rejectsDuplicateOldApproximateAndInaccurateFixes() {
        val acc = RecordingAccumulator()
        add(acc, point(20_000))
        assertNull(acc.candidate("r", point(20_000), true, 20_000, 0))
        assertNull(acc.candidate("r", point(1_000), true, 20_000, 0))
        assertNull(acc.candidate("r", point(21_000), false, 21_000, 0))
        assertNull(acc.candidate("r", point(21_000, accuracy = 70f), true, 21_000, 0))
        assertEquals(1, acc.state.pointCount)
    }

    @Test fun stationaryJitterDoesNotAccumulateDistance() {
        val acc = RecordingAccumulator()
        add(acc, point(1_000))
        for (i in 2..100) add(acc, point(i * 1_000L, lon = 11.00001, speed = 0f))
        assertEquals(1, acc.state.pointCount)
        assertEquals(0.0, acc.state.distanceMeters, 0.0)
    }

    @Test fun signalGapsAndRecoveryDoNotCreateArtificialConnectingLines() {
        val acc = RecordingAccumulator()
        add(acc, point(1_000))
        add(acc, point(30_000, lat = 44.0))
        assertEquals(2, acc.state.chunks.size)
        assertEquals(0.0, acc.state.distanceMeters, 0.0)
        acc.breakSegment()
        add(acc, point(31_000, lat = 44.01))
        assertEquals(3, acc.state.chunks.size)
        assertEquals(0.0, acc.state.distanceMeters, 0.0)
    }

    @Test fun impossibleJumpIsRejectedAndCompletedChunksAreReused() {
        val acc = RecordingAccumulator()
        add(acc, point(1_000))
        assertNull(acc.candidate("r", point(2_000, lat = 45.0), true, 2_000, 0))
        val long = RecordingAccumulator()
        repeat(300) { add(long, point(1_000L + it * 2_000L, lon = 11.0 + it * 0.0001)) }
        val first = long.state.chunks.first()
        add(long, point(601_000, lon = 11.03))
        assertSame(first, long.state.chunks.first())
        assertTrue(long.state.chunks.all { it.points.size <= 128 })
    }

    @Test fun stationarySamplesKeepTheirSpeedAndSequenceWithoutExtendingTheRoute() {
        val acc = RecordingAccumulator()
        val samples = mutableListOf<com.gpxnavigator.data.database.RecordingPointEntity>()
        for (time in 1_000L..5_000L step 1_000) {
            val sample = acc.candidate("r", point(time, speed = 0f), true, time, time)!!
            samples += sample
            acc.commit(sample)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), samples.map { it.sequence })
        assertEquals(1, samples.count { it.isTrackPoint })
        assertTrue(samples.all { it.speedMetersPerSecond == 0f })
        assertEquals(1, acc.state.pointCount)
        val restored = RecordingAccumulator()
        restored.restore("r", 0L, samples)
        val next = restored.candidate("r", point(1_000, lon = 11.0001), true, 1_000, 10_000)!!
        assertEquals(5, next.sequence)
        assertEquals(1, next.segment)
        assertTrue(next.isTrackPoint)
        restored.commit(next)
        assertEquals(0.0, restored.state.distanceMeters, 0.0)
    }

    @Test fun speedIsStoredOnlyWhenPresentFiniteAndPlausible() {
        for (speed in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f, 91f)) {
            val sample = RecordingAccumulator().candidate("r", point(1_000, speed = speed), true, 1_000, 1_000)!!
            assertNull(sample.speedMetersPerSecond)
        }
        assertEquals(25f, RecordingAccumulator().candidate("r", point(1_000, speed = 25f), true, 1_000, 1_000)!!.speedMetersPerSecond)
    }

    @Test fun sessionBoundaryRejectsCachedFixesIncludingTheStartingMillisecond() {
        val acc = RecordingAccumulator()
        acc.restore("r", 1_700_000_000_000L, emptyList())
        acc.beginSession(10_000)
        assertNull(acc.candidate("r", point(9_999), true, 10_001, 0))
        assertNull(acc.candidate("r", point(10_000), true, 10_001, 0))
        val fresh = acc.candidate("r", point(10_001), true, 10_001, 0)!!
        acc.commit(fresh)
        assertEquals(1, acc.state.pointCount)
        assertEquals(0, fresh.sequence)
        assertEquals(0, fresh.segment)
    }

    @Test fun resumedSessionAfterRebootAcceptsFreshFixEvenWithAnOldElapsedValue() {
        val acc = RecordingAccumulator()
        val saved = acc.candidate("r", point(5_000), true, 5_000, 5_000)!!
        acc.restore("r", 0, listOf(saved))
        acc.beginSession(1_000)
        assertNull(acc.candidate("r", point(900), true, 1_000, 10_000))
        val afterReboot = acc.candidate("r", point(5_000, lon = 11.1), true, 5_000, 14_000)!!
        assertEquals(1, afterReboot.sequence)
        assertEquals(1, afterReboot.segment)
        acc.commit(afterReboot)
        assertEquals(2, acc.state.pointCount)
        assertEquals(0.0, acc.state.distanceMeters, 0.0)
        assertNull(acc.candidate("r", point(5_000, lon = 11.1), true, 5_000, 14_000))
    }
}
