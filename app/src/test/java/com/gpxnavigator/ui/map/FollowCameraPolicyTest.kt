package com.gpxnavigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowCameraPolicyTest {
    @Test fun smallGpsJitterDoesNotMoveCamera() {
        assertEquals(CameraMotion.STAY, followCameraMotion(5.0, 8f, forced = false))
        assertEquals(CameraMotion.STAY, followCameraMotion(20.0, 50f, forced = false))
    }

    @Test fun nearbyMotionIsAnimated() {
        assertEquals(CameraMotion.ANIMATE, followCameraMotion(40.0, 8f, forced = false))
        assertEquals(CameraMotion.ANIMATE, followCameraMotion(0.0, null, forced = true))
    }

    @Test fun reliableMovementFollowsSmallDisplacementsWithoutTheStationaryDeadband() {
        assertEquals(CameraMotion.ANIMATE, followCameraMotion(1.0, 5f, false, 1.5f))
        assertEquals(CameraMotion.ANIMATE, followCameraMotion(4.0, 20f, false, 10f))
        assertEquals(CameraMotion.STAY, followCameraMotion(0.2, 5f, false, 10f))
    }

    @Test fun stoppedUnknownOrPoorGpsKeepsTheJitterProtection() {
        for (speed in listOf(null, 0f, 0.8f, Float.NaN, 100f)) {
            assertEquals(CameraMotion.STAY, followCameraMotion(4.0, 5f, false, speed))
        }
        for (accuracy in listOf(null, 25f, 80f, Float.NaN)) {
            assertEquals(CameraMotion.STAY, followCameraMotion(4.0, accuracy, false, 10f))
        }
    }

    @Test fun distantRecenterDoesNotAnimateAcrossTheCountry() {
        assertEquals(CameraMotion.JUMP, followCameraMotion(100_000.0, 8f, forced = true))
    }

    @Test fun geographicDistanceIsStable() {
        assertEquals(0.0, cameraDistanceMeters(43.77, 11.25, 43.77, 11.25), 0.001)
        assertTrue(cameraDistanceMeters(43.77, 11.25, 43.771, 11.25) in 110.0..112.0)
    }
}
