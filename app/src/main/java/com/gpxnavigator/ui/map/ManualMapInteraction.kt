package com.gpxnavigator.ui.map

import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.RotateGestureDetector
import org.maplibre.android.gestures.ShoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

internal class ManualMapInteraction(
    private val map: MapLibreMap,
    private val post: (() -> Unit) -> Unit,
    private val onZoomSelected: (Double) -> Unit,
    private val onPan: () -> Unit,
    private val onZoomFinished: () -> Unit,
    private val onGestureStarted: () -> Unit = {},
) {
    private val policy = MapInteractionPolicy()
    private var beforeZoom: Double? = null
    private var cameraGesture = false
    private var rememberFinalZoom = false
    private var disposed = false
    val inProgress: Boolean get() = cameraGesture || policy.inProgress

    private val cameraListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            onGestureStarted()
            if (beforeZoom == null) beforeZoom = map.cameraPosition.zoom
            cameraGesture = true
        } else rememberFinalZoom = false
    }
    private val moveListener = object : MapLibreMap.OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) = begin(MapGesture.MOVE)
        override fun onMove(detector: MoveGestureDetector) = Unit
        override fun onMoveEnd(detector: MoveGestureDetector) = end(MapGesture.MOVE)
    }
    private val scaleListener = object : MapLibreMap.OnScaleListener {
        override fun onScaleBegin(detector: StandardScaleGestureDetector) = begin(MapGesture.SCALE)
        override fun onScale(detector: StandardScaleGestureDetector) = Unit
        override fun onScaleEnd(detector: StandardScaleGestureDetector) = end(MapGesture.SCALE)
    }
    private val rotateListener = object : MapLibreMap.OnRotateListener {
        override fun onRotateBegin(detector: RotateGestureDetector) = begin(MapGesture.ROTATE)
        override fun onRotate(detector: RotateGestureDetector) = Unit
        override fun onRotateEnd(detector: RotateGestureDetector) = end(MapGesture.ROTATE)
    }
    private val shoveListener = object : MapLibreMap.OnShoveListener {
        override fun onShoveBegin(detector: ShoveGestureDetector) = begin(MapGesture.SHOVE)
        override fun onShove(detector: ShoveGestureDetector) = Unit
        override fun onShoveEnd(detector: ShoveGestureDetector) = end(MapGesture.SHOVE)
    }

    init {
        map.addOnCameraMoveStartedListener(cameraListener)
        map.addOnMoveListener(moveListener)
        map.addOnScaleListener(scaleListener)
        map.addOnRotateListener(rotateListener)
        map.addOnShoveListener(shoveListener)
    }

    private fun begin(gesture: MapGesture) {
        onGestureStarted()
        rememberFinalZoom = false
        if (beforeZoom == null) beforeZoom = map.cameraPosition.zoom
        policy.begin(gesture)
    }

    private fun end(gesture: MapGesture) {
        policy.end(gesture)
        // Let all detectors process this touch event before deciding whether it was a pan or pinch.
        post { finish() }
    }

    fun onCameraIdle() {
        if (disposed || policy.inProgress) return
        // In free exploration the native pinch can continue settling after the fingers lift.
        if (rememberFinalZoom) {
            rememberFinalZoom = false
            onZoomSelected(map.cameraPosition.zoom)
        }
        finish()
    }

    private fun finish() {
        if (disposed || policy.inProgress) return
        val interaction = policy.finish()
        val initial = beforeZoom
        if (interaction == null && !cameraGesture) return
        val zoom = map.cameraPosition.zoom
        val zoomed = interaction?.zoomed == true || (initial != null && abs(initial - zoom) > 0.01)
        beforeZoom = null
        cameraGesture = false
        if (zoomed) {
            rememberFinalZoom = true
            onZoomSelected(zoom)
            onZoomFinished()
        } else if (interaction?.pauseFollow == true) onPan()
    }

    fun dispose() {
        disposed = true
        map.removeOnCameraMoveStartedListener(cameraListener)
        map.removeOnMoveListener(moveListener)
        map.removeOnScaleListener(scaleListener)
        map.removeOnRotateListener(rotateListener)
        map.removeOnShoveListener(shoveListener)
    }
}
