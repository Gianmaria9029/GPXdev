package com.gpxnavigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapZoomPolicyTest {
    @Test fun userCanZoomOutBelowOriginalFollowScaleAndRecenteringKeepsIt() {
        val policy = MapZoomPolicy()
        assertEquals(16.0, policy.followZoom(0.0, 22.0), 0.0)
        policy.select(8.25, 0.0, 22.0)
        repeat(5) { assertEquals(8.25, policy.followZoom(0.0, 22.0), 0.0) }
    }

    @Test fun buttonsPreserveFractionalPinchScaleAndClampAtMapLimits() {
        val policy = MapZoomPolicy(12.4)
        assertEquals(13.4, policy.select(policy.followZoom(0.0, 20.0) + 1, 0.0, 20.0), 0.001)
        assertEquals(12.4, policy.select(policy.followZoom(0.0, 20.0) - 1, 0.0, 20.0), 0.001)
        assertEquals(20.0, policy.select(30.0, 0.0, 20.0), 0.0)
        assertEquals(0.0, policy.select(-1.0, 0.0, 20.0), 0.0)
    }

    @Test fun restoredZoomSurvivesDifferentMapLimitsWithoutReplacingPreference() {
        val policy = MapZoomPolicy(19.0)
        assertEquals(15.0, policy.followZoom(0.0, 15.0), 0.0)
        assertEquals(19.0, policy.followZoom(0.0, 22.0), 0.0)
        policy.restore(10.5)
        assertEquals(10.5, policy.followZoom(0.0, 22.0), 0.0)
    }

    @Test fun malformedPreferenceUsesFiniteDefault() {
        assertEquals(16.0, MapZoomPolicy(Double.NaN).followZoom(0.0, 22.0), 0.0)
        assertEquals(14.0, MapZoomPolicy(-5.0).followZoom(0.0, 14.0), 0.0)
    }
}
