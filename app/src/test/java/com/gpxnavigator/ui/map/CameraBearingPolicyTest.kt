package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.MapOrientation
import com.gpxnavigator.navigation.EARTH_RADIUS_METERS
import com.gpxnavigator.navigation.bearingDifference
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class CameraBearingPolicyTest {
    private fun fix(time: Long, bearing: Float?, speed: Float? = 5f, accuracy: Float? = 5f,
        x: Double = 0.0, bearingAccuracy: Float? = 5f) = LocationPoint(0.0,
        Math.toDegrees(x / EARTH_RADIUS_METERS), accuracy, speed, bearing, null,
        time * 1_000_000_000L, bearingAccuracy)

    @Test fun northModeAlwaysUsesNorthAndCanSwitchToCourseWithoutAnotherFix() {
        val policy = CameraBearingPolicy()
        val point = fix(1, 90f)
        assertEquals(0.0, policy.bearing(MapOrientation.NORTH_UP, point), 0.0)
        assertEquals(90.0, policy.bearing(MapOrientation.COURSE_UP, point), 0.0)
        assertEquals(0.0, policy.bearing(MapOrientation.NORTH_UP, point), 0.0)
    }

    @Test fun rotationAcrossNorthTakesShortArcAndDuplicatesDoNotAdvanceSmoothing() {
        val policy = CameraBearingPolicy()
        policy.bearing(MapOrientation.COURSE_UP, fix(1, 350f))
        val point = fix(3, 10f)
        val north = policy.bearing(MapOrientation.COURSE_UP, point)
        assertTrue(abs(bearingDifference(0.0, north)) < 0.01)
        assertEquals(north, policy.bearing(MapOrientation.COURSE_UP, point), 0.0)
    }

    @Test fun stationaryMissingAndPoorFixesKeepLastReliableOrientation() {
        val policy = CameraBearingPolicy()
        policy.bearing(MapOrientation.COURSE_UP, fix(1, 90f))
        assertEquals(90.0, policy.bearing(MapOrientation.COURSE_UP, fix(3, 180f, speed = 0f)), 0.0)
        assertEquals(90.0, policy.bearing(MapOrientation.COURSE_UP, fix(5, 180f, accuracy = 80f)), 0.0)
        assertEquals(90.0, policy.bearing(MapOrientation.COURSE_UP, fix(7, 180f, bearingAccuracy = 90f)), 0.0)
        assertEquals(90.0, policy.bearing(MapOrientation.COURSE_UP, null), 0.0)
    }

    @Test fun missingSpeedDoesNotTrustBearingUntilDisplacementConfirmsMovement() {
        val policy = CameraBearingPolicy()
        assertEquals(0.0, policy.bearing(MapOrientation.COURSE_UP, fix(1, 270f, speed = null)), 0.0)
        assertEquals(0.0, policy.bearing(MapOrientation.COURSE_UP, fix(3, 270f, speed = null, x = 3.0)), 0.0)
        assertEquals(90.0, policy.bearing(MapOrientation.COURSE_UP, fix(11, 270f, speed = null, x = 15.0)), 0.1)
    }

    @Test fun restoredHeadingSurvivesPauseAndOldFixCannotTurnCamera() {
        val policy = CameraBearingPolicy(120.0)
        assertEquals(120.0, policy.bearing(MapOrientation.COURSE_UP, null), 0.0)
        policy.bearing(MapOrientation.COURSE_UP, fix(10, 120f))
        assertEquals(120.0, policy.bearing(MapOrientation.COURSE_UP, fix(9, 300f)), 0.0)
        assertEquals(0.0, policy.bearing(MapOrientation.NORTH_UP, null), 0.0)
    }
}
