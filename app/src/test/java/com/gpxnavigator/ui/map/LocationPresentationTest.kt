package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.navigation.bearingDifference
import com.gpxnavigator.navigation.longitudeDeltaDegrees
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LocationPresentationTest {
    private fun fix(seconds: Long, longitude: Double, bearing: Float? = 90f) =
        LocationPoint(0.0, longitude, 5f, 10f, bearing, null, seconds * 1_000_000_000L)

    @Test fun firstFixIsImmediateAndFollowingFixInterpolatesWithoutChangingTheMeasurement() {
        val state = LocationPresentation()
        val first = fix(1, 0.0)
        val second = fix(2, 0.0001)
        state.update(first, 1_000)
        assertSame(first, state.position(1_000))
        state.update(second, 2_000)
        assertEquals(0.00005, state.position(2_500)!!.longitude, 1e-9)
        assertSame(second, state.target)
        assertEquals(0.0001, second.longitude, 0.0)
        assertSame(second, state.position(3_000))
        assertSame(second, state.position(10_000)) // No extrapolation after the last fix.
    }

    @Test fun earlyFixRetargetsFromTheDrawnPositionWithoutRestartingFromAnOldGpsPoint() {
        val state = LocationPresentation()
        state.update(fix(1, 0.0), 1_000)
        state.update(fix(2, 0.0001), 2_000)
        val displayed = state.position(2_500)!!
        state.update(fix(3, 0.0002), 2_500)
        assertEquals(displayed.longitude, state.position(2_500)!!.longitude, 1e-9)
        assertEquals(0.000125, state.position(3_000)!!.longitude, 1e-9)
    }

    @Test fun duplicatesAndOutOfOrderFixesDoNotRestartOrReverseTheAnimation() {
        val state = LocationPresentation()
        val target = fix(2, 0.0001)
        state.update(fix(1, 0.0), 1_000)
        state.update(target, 2_000)
        assertFalse(state.update(target.copy(), 2_300))
        assertFalse(state.update(fix(1, -0.001), 2_400))
        assertFalse(state.update(target.copy(longitude = -0.001), 2_500))
        assertEquals(500L, state.remainingMillis(2_500))
        assertSame(target, state.position(3_000))
    }

    @Test fun northAndAntimeridianCrossingsUseTheShortArc() {
        val state = LocationPresentation()
        state.update(fix(1, 179.999, 350f), 1_000)
        state.update(fix(2, -179.999, 10f), 2_000)
        val middle = state.position(2_500)!!
        assertTrue(abs(longitudeDeltaDegrees(180.0, middle.longitude)) < 1e-6)
        assertTrue(abs(bearingDifference(0.0, middle.bearingDegrees!!.toDouble())) < 1e-6)
    }

    @Test fun gpsLossLongGapsAndDistantCorrectionsDoNotReplayAStaleJourney() {
        val state = LocationPresentation()
        state.update(fix(1, 0.0), 1_000)
        val afterGap = fix(10, 0.0002)
        state.update(afterGap, 10_000)
        assertSame(afterGap, state.position(10_000))
        val distant = fix(11, 1.0)
        state.update(distant, 11_000)
        assertSame(distant, state.position(11_000))
        state.update(null, 11_100)
        assertNull(state.position(11_100))
        val recovered = fix(12, 1.0001)
        state.update(recovered, 12_000)
        assertSame(recovered, state.position(12_000))
    }

    @Test fun receiptGapAlsoSnapsWhenFixTimestampsAreCloseButDeliveryWasInterrupted() {
        val state = LocationPresentation()
        state.update(fix(1, 0.0), 1_000)
        val delayed = fix(2, 0.0001)
        state.update(delayed, 10_000)
        assertSame(delayed, state.position(10_000))
    }

    @Test fun missingTimestampUsesArrivalCadenceAndMissingBearingStaysMissing() {
        val state = LocationPresentation()
        state.update(fix(1, 0.0).copy(elapsedRealtimeNanos = null), 1_000)
        state.update(fix(2, 0.0001, null).copy(elapsedRealtimeNanos = null), 2_000)
        assertEquals(0.00005, state.position(2_500)!!.longitude, 1e-9)
        assertNull(state.position(2_500)!!.bearingDegrees)
    }

    @Test fun pauseFinishesCurrentTransitionAndHiddenUpdatesDoNotAnimate() {
        val state = LocationPresentation()
        state.update(fix(1, 0.0), 1_000)
        val second = fix(2, 0.0001)
        state.update(second, 2_000)
        state.finish()
        assertSame(second, state.position(2_100))
        val hidden = fix(3, 0.0002)
        state.update(hidden, 3_000, animate = false)
        assertSame(hidden, state.position(3_000))
        assertEquals(0L, state.remainingMillis(3_000))
    }
}
