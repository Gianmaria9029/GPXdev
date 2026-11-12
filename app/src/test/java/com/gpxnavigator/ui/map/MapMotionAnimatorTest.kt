package com.gpxnavigator.ui.map

import android.os.Looper
import android.app.Application
import com.gpxnavigator.domain.model.LocationPoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MapMotionAnimatorTest {
    @Before fun useExplicitDisplayFrames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun fix(second: Long, longitude: Double) =
        LocationPoint(0.0, longitude, 5f, 10f, 90f, null, second * 1_000_000_000L)

    private fun camera(longitude: Double, bearing: Double = 0.0) = CameraPosition.Builder()
        .target(LatLng(0.0, longitude)).zoom(16.0).bearing(bearing).padding(0.0, 0.0, 0.0, 0.0).build()

    @Test fun oneFrameClockKeepsMarkerAndCameraTogetherAndStopsAtTheTarget() {
        var now = 1_000L
        var point: LocationPoint? = null
        var position: CameraPosition? = null
        val animator = MapMotionAnimator(nowMillis = { now }, onFrame = { p, c ->
            point = p
            if (c != null) position = c
        }, onError = { fail(it) })
        try {
            animator.resume()
            animator.updateLocation(fix(1, 0.0))
            now = 2_000
            animator.updateLocation(fix(2, 0.0001))
            animator.moveCamera(camera(0.0, 350.0), camera(0.0001, 10.0), false)
            now = 2_500
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            assertEquals(0.00005, point!!.longitude, 1e-9)
            assertEquals(point!!.longitude, position!!.target!!.longitude, 1e-9)
            assertEquals(0.0, position!!.bearing, 1e-6)
            now = 3_000
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            assertFalse(animator.cameraAnimating)
            assertEquals(0.0001, point!!.longitude, 1e-9)
        } finally { animator.dispose() }
    }

    @Test fun manualPanCancelsCameraButMarkerKeepsMovingAndPauseRemovesCallbacks() {
        var now = 1_000L
        var locationFrames = 0
        var cameraFrames = 0
        val animator = MapMotionAnimator(nowMillis = { now }, onFrame = { _, c ->
            locationFrames++
            if (c != null) cameraFrames++
        }, onError = { fail(it) })
        animator.resume()
        animator.updateLocation(fix(1, 0.0))
        now = 2_000
        animator.updateLocation(fix(2, 0.0001))
        animator.moveCamera(camera(0.0), camera(0.0001), false)
        animator.cancelCamera()
        val camerasBefore = cameraFrames
        val framesBefore = locationFrames
        now = 2_500
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(camerasBefore, cameraFrames)
        assertTrue(locationFrames > framesBefore)
        animator.pause()
        val pausedFrames = locationFrames
        now = 3_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64))
        assertEquals(pausedFrames, locationFrames)
        animator.resume()
        assertTrue(locationFrames > pausedFrames)
        animator.dispose()
        val disposedFrames = locationFrames
        animator.updateLocation(fix(4, 0.0002))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(disposedFrames, locationFrames)
    }

    @Test fun lostGpsCancelsCameraAndRenderingFailureStopsFurtherFrames() {
        var now = 1_000L
        var failRendering = false
        var errors = 0
        var frames = 0
        val animator = MapMotionAnimator(nowMillis = { now }, onFrame = { _, _ ->
            frames++
            if (failRendering) throw IllegalStateException("render failure")
        }, onError = { errors++ })
        try {
            animator.resume()
            animator.updateLocation(fix(1, 0.0))
            animator.moveCamera(camera(0.0), camera(0.001), false)
            assertTrue(animator.cameraAnimating)
            animator.updateLocation(null)
            assertFalse(animator.cameraAnimating)
            failRendering = true
            now = 2_000
            animator.updateLocation(fix(2, 0.0001))
            assertEquals(1, errors)
            val stoppedFrames = frames
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64))
            assertEquals(stoppedFrames, frames)
        } finally { animator.dispose() }
    }
}
