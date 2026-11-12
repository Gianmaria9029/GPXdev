package com.gpxnavigator.ui.map

import android.os.SystemClock
import android.view.Choreographer
import com.gpxnavigator.domain.model.LocationPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

private const val RECENTER_ANIMATION_MS = 650L

/** One display-frame callback drives both the GeoJSON marker and the native GL camera. */
internal class MapMotionAnimator(
    private val choreographer: Choreographer = Choreographer.getInstance(),
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val onFrame: (LocationPoint?, CameraPosition?) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val location = LocationPresentation()
    private var camera: CameraAnimation? = null
    private var active = false
    private var disposed = false
    private var posted = false
    val cameraAnimating: Boolean get() = camera?.finished(nowMillis()) == false
    private val callback = Choreographer.FrameCallback {
        posted = false
        render()
    }

    fun updateLocation(point: LocationPoint?): Boolean {
        val changed = location.update(point, nowMillis(), animate = active)
        if (changed) {
            if (point == null) camera = null
            render()
        }
        return changed
    }

    fun moveCamera(from: CameraPosition, to: CameraPosition, jump: Boolean) {
        val now = nowMillis()
        val remaining = location.remainingMillis(now)
        val duration = if (jump || !active) 0L else remaining.takeIf { it > 0 } ?: RECENTER_ANIMATION_MS
        camera = CameraAnimation(from, to, now, duration)
        render()
    }

    fun cancelCamera() { camera = null }

    fun resume() {
        if (disposed) return
        active = true
        location.finish()
        render()
    }

    fun pause() {
        active = false
        choreographer.removeFrameCallback(callback)
        posted = false
        location.finish()
        camera = null
    }

    fun dispose() { pause(); disposed = true }

    private fun render() {
        if (!active || disposed) return
        val now = nowMillis()
        val transition = camera
        try {
            onFrame(location.position(now), transition?.position(now))
        } catch (exception: RuntimeException) {
            pause()
            onError(exception.message ?: "Impossibile aggiornare il movimento sulla mappa.")
            return
        }
        if (camera === transition && transition?.finished(now) == true) camera = null
        if (active && !disposed && !posted && (location.remainingMillis(now) > 0L || camera != null)) {
            posted = true
            choreographer.postFrameCallback(callback)
        }
    }
}

internal class CameraAnimation(
    private val from: CameraPosition,
    private val to: CameraPosition,
    private val startedAt: Long,
    private val duration: Long,
) {
    fun finished(now: Long): Boolean = now - startedAt >= duration

    fun position(now: Long): CameraPosition {
        if (duration == 0L || finished(now)) return to
        val fraction = ((now - startedAt).toDouble() / duration).coerceIn(0.0, 1.0)
        val start = from.target
        val end = to.target
        val target = if (start == null || end == null) end else LatLng(
            interpolate(start.latitude, end.latitude, fraction),
            interpolateLongitude(start.longitude, end.longitude, fraction),
        )
        return CameraPosition.Builder(from).target(target)
            .zoom(interpolate(from.zoom, to.zoom, fraction))
            .tilt(interpolate(from.tilt, to.tilt, fraction))
            .bearing(interpolateBearing(from.bearing, to.bearing, fraction))
            .padding(DoubleArray(4) {
                interpolate(from.padding?.get(it) ?: 0.0, to.padding?.get(it) ?: 0.0, fraction)
            })
            .build()
    }
}
