package com.gpxnavigator.navigation

import com.gpxnavigator.data.database.RecordingPointEntity
import org.junit.Assert.*
import org.junit.Test

class LiveRecordingStatisticsTest {
    private fun sample(index: Int, time: Long, speed: Float? = 10f, elevation: Double? = 100.0,
        track: Boolean = true, segment: Int = 0) = RecordingPointEntity("trip", index, segment,
        43.0, 11.0 + index * .0001, elevation, 1_700_000_000_000L + time, time, 5f, speed, track)

    @Test fun liveStatisticsIncludeSamplesThatDoNotExtendTheRoute() {
        val accumulator = RecordingAccumulator()
        accumulator.restore("trip", 1_700_000_000_000L, emptyList())
        val points = listOf(sample(0, 0), sample(1, 2_000, 20f, 200.0, track = false),
            sample(2, 10_000, 20f, 80.0))
        points.forEach(accumulator::commit)
        val state = accumulator.state
        assertEquals(2, state.pointCount)
        assertEquals(19.0, state.statistics.movingAverageSpeedMetersPerSecond!!, .000001)
        assertEquals(200.0, state.statistics.maximumElevationMeters!!, .0)
        assertEquals(80.0, state.statistics.minimumElevationMeters!!, .0)
        assertEquals(-20.0, state.statistics.elevationDifferenceMeters!!, .0)
        assertEquals(20f, state.statistics.maximumSpeedPoint!!.speedMetersPerSecond)
        assertEquals(RecordingStatistics.calculate(points), state.statistics)
        assertEquals(state.distanceMeters, state.statistics.totalDistanceMeters, .000001)
    }

    @Test fun resumingRestoresStatisticsAndDoesNotBridgeInterruptionsOrStops() {
        val first = listOf(sample(0, 0), sample(1, 2_000, 10f, 200.0), sample(2, 4_000, 0f, track = false))
        val next = listOf(sample(3, 20_000, 30f, 50.0, segment = 1), sample(4, 22_000, 30f, 80.0, segment = 1))
        val restored = RecordingAccumulator()
        restored.restore("trip", 1_700_000_000_000L, first)
        assertEquals(10.0, restored.state.statistics.movingAverageSpeedMetersPerSecond!!, .000001)
        next.forEach(restored::commit)
        assertEquals(20.0, restored.state.statistics.movingAverageSpeedMetersPerSecond!!, .000001)
        val uninterrupted = RecordingAccumulator()
        uninterrupted.restore("trip", 1_700_000_000_000L, first + next)
        assertEquals(uninterrupted.state, restored.state)
        restored.restore("new-trip", 1_700_000_100_000L, emptyList())
        assertEquals(RecordingStatistics(), restored.state.statistics)
        assertEquals(0.0, restored.state.distanceMeters, .0)
    }

    @Test fun aMissingStartElevationRemainsUnknownEvenWhenLaterHeightsAreAvailable() {
        val accumulator = RecordingAccumulator()
        accumulator.restore("trip", 1_700_000_000_000L, listOf(sample(0, 0, elevation = null)))
        accumulator.commit(sample(1, 2_000, elevation = 200.0, track = false))
        assertEquals(200.0, accumulator.state.statistics.minimumElevationMeters!!, .0)
        assertNull(accumulator.state.statistics.elevationDifferenceMeters)
        assertEquals(1, accumulator.state.pointCount)
    }
}
