package com.gpxnavigator.ui.map

import org.junit.Assert.*
import org.junit.Test

class MapInteractionPolicyTest {
    @Test fun panPausesFollowOnlyAfterGestureEnds() {
        val policy = MapInteractionPolicy()
        policy.begin(MapGesture.MOVE)
        assertTrue(policy.inProgress)
        assertNull(policy.finish())
        policy.end(MapGesture.MOVE)
        assertEquals(MapInteraction(zoomed = false, pauseFollow = true), policy.finish())
    }

    @Test fun pinchWithSimultaneousPanAndRotationKeepsFollowInEitherEndOrder() {
        for (order in listOf(listOf(MapGesture.MOVE, MapGesture.ROTATE, MapGesture.SCALE),
            listOf(MapGesture.SCALE, MapGesture.ROTATE, MapGesture.MOVE))) {
            val policy = MapInteractionPolicy()
            order.forEach(policy::begin)
            order.dropLast(1).forEach { policy.end(it); assertNull(policy.finish()) }
            policy.end(order.last())
            assertEquals(MapInteraction(zoomed = true, pauseFollow = false), policy.finish())
        }
    }

    @Test fun detectorHandoverWithinSameTouchEventDoesNotMisclassifyPinchAsPan() {
        val policy = MapInteractionPolicy()
        policy.begin(MapGesture.MOVE)
        policy.end(MapGesture.MOVE)
        // The posted completion runs after the scale detector receives the same touch event.
        policy.begin(MapGesture.SCALE)
        assertNull(policy.finish())
        policy.end(MapGesture.SCALE)
        assertEquals(MapInteraction(true, false), policy.finish())
    }

    @Test fun subsequentPanIsIndependentFromPreviousPinch() {
        val policy = MapInteractionPolicy()
        policy.begin(MapGesture.SCALE)
        policy.end(MapGesture.SCALE)
        assertEquals(MapInteraction(true, false), policy.finish())
        policy.begin(MapGesture.MOVE)
        policy.end(MapGesture.MOVE)
        assertEquals(MapInteraction(false, true), policy.finish())
        assertNull(policy.finish())
    }

    @Test fun rotationAndTiltWithoutZoomAllowFreeExplorationAndIdleAloneDoesNothing() {
        val policy = MapInteractionPolicy()
        assertNull(policy.finish())
        policy.begin(MapGesture.ROTATE)
        policy.begin(MapGesture.SHOVE)
        policy.end(MapGesture.ROTATE)
        policy.end(MapGesture.SHOVE)
        assertEquals(MapInteraction(false, true), policy.finish())
    }
}
