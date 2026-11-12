package com.gpxnavigator.navigation

import com.gpxnavigator.data.database.RecordingPointEntity
import org.junit.Assert.*
import org.junit.Test

class RecordingStatisticsTest {
    private fun sample(sequence: Int, time: Long, speed: Float? = 10f, segment: Int = 0,
        lon: Double = 11.0 + sequence * 0.0001, elevation: Double? = 100.0,
        track: Boolean = true) = RecordingPointEntity("r", sequence, segment, 43.0, lon,
        elevation, 1_700_000_000_000L + time, time, 5f, speed, track)

    @Test fun distanceMatchesFilteredRouteAndNeverConnectsSeparateSegments() {
        val a = sample(0, 0)
        val jitter = sample(1, 1_000, track = false, lon = 11.00001)
        val b = sample(2, 2_000)
        val c = sample(3, 4_000, segment = 1, lon = 12.0)
        val d = sample(4, 6_000, segment = 1, lon = 12.0001)
        val stats = RecordingStatistics.calculate(listOf(a, jitter, b, c, d))
        assertEquals(distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) +
            distanceMeters(c.latitude, c.longitude, d.latitude, d.longitude), stats.totalDistanceMeters, 0.000001)
    }

    @Test fun movingAverageWeightsTimeInsteadOfCountingSamples() {
        // 2 s at a mean 15 m/s + 8 s at 20 m/s = 190 m / 10 s.
        val stats = RecordingStatistics.calculate(listOf(sample(0, 0, 10f),
            sample(1, 2_000, 20f), sample(2, 10_000, 20f)))
        assertEquals(19.0, stats.movingAverageSpeedMetersPerSecond!!, 0.000001)
        assertEquals(20f, stats.maximumSpeedPoint!!.speedMetersPerSecond)
        assertEquals(1, stats.maximumSpeedPoint.sequence) // First occurrence wins a tie.
    }

    @Test fun stopsAndStopTransitionsDoNotLowerTheMovingAverage() {
        val stats = RecordingStatistics.calculate(listOf(sample(0, 0, 10f), sample(1, 2_000, 10f),
            sample(2, 4_000, 0f, track = false), sample(3, 14_000, 0.8f, track = false),
            sample(4, 16_000, 20f), sample(5, 18_000, 20f)))
        assertEquals(15.0, stats.movingAverageSpeedMetersPerSecond!!, 0.000001)
    }

    @Test fun missingSpeedSignalGapsSegmentsAndRebootsAreExcluded() {
        val stats = RecordingStatistics.calculate(listOf(sample(0, 0, 10f), sample(1, 2_000, 10f),
            sample(2, 4_000, null), sample(3, 6_000, 80f), sample(4, 26_000, 80f),
            sample(5, 27_000, 80f, segment = 1), sample(6, 1_000, 80f, segment = 1)))
        assertEquals(10.0, stats.movingAverageSpeedMetersPerSecond!!, 0.000001)
    }

    @Test fun maximumIncludesAcceptedSamplesThatDoNotExtendThePolyline() {
        val peak = sample(1, 1_000, 30f, track = false)
        val stats = RecordingStatistics.calculate(listOf(sample(0, 0), peak, sample(2, 2_000)))
        assertSame(peak, stats.maximumSpeedPoint)
    }

    @Test fun elevationExtremaAndSignedEndpointDifferenceAreIndependent() {
        val stats = RecordingStatistics.calculate(listOf(sample(0, 0, elevation = 120.0),
            sample(1, 2_000, elevation = 200.0), sample(2, 4_000, elevation = 80.0)))
        assertEquals(80.0, stats.minimumElevationMeters!!, 0.0)
        assertEquals(200.0, stats.maximumElevationMeters!!, 0.0)
        assertEquals(-40.0, stats.elevationDifferenceMeters!!, 0.0)
    }

    @Test fun missingEndpointElevationDoesNotUseAnInteriorPointAsTheEndpoint() {
        val points = listOf(sample(0, 0, elevation = null), sample(1, 2_000, elevation = 123.0))
        assertNull(RecordingStatistics.calculate(points).elevationDifferenceMeters)
        assertNull(RecordingStatistics.calculate(points.reversed()).elevationDifferenceMeters)
        assertEquals(123.0, RecordingStatistics.calculate(points).minimumElevationMeters!!, 0.0)
    }

    @Test fun zeroIsAMeasurementButUnavailableAndInvalidValuesStayAbsent() {
        val stationary = RecordingStatistics.calculate(listOf(sample(0, 0, 0f), sample(1, 2_000, 0f)))
        assertEquals(0f, stationary.maximumSpeedPoint!!.speedMetersPerSecond)
        assertNull(stationary.movingAverageSpeedMetersPerSecond)
        val invalid = listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f, 91f)
            .mapIndexed { index, speed -> sample(index, index * 2_000L, speed, elevation = Double.NaN) }
        val stats = RecordingStatistics.calculate(invalid)
        assertNull(stats.maximumSpeedPoint)
        assertNull(stats.movingAverageSpeedMetersPerSecond)
        assertNull(stats.minimumElevationMeters)
        assertNull(stats.maximumElevationMeters)
        assertNull(stats.elevationDifferenceMeters)
        assertNull(RecordingStatistics.calculate(listOf(sample(0, 0))).movingAverageSpeedMetersPerSecond)
    }
}
