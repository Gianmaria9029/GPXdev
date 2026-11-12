package com.gpxnavigator.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gpxnavigator.data.maps.OfflineMap
import com.gpxnavigator.R
import com.gpxnavigator.NavigatorApplication
import java.io.Closeable
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.navigation.RecordingChunk
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.MapOrientation
import com.gpxnavigator.navigation.bearingDifference
import kotlin.math.abs
import kotlin.math.roundToInt
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import com.gpxnavigator.ui.theme.MapPalette
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView as NativeMapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

private const val SAVED_OVERVIEW_INDEX = 5

@Composable
fun MapView(offlineMap: OfflineMap, track: Track?, location: LocationPoint?,
    followEnabled: Boolean, centerRequestId: Long, onManualCameraMove: () -> Unit,
    onError: (String) -> Unit, modifier: Modifier = Modifier,
    orientation: MapOrientation = MapOrientation.NORTH_UP, userZoom: Double? = null,
    zoomStepCount: Int = 0, onUserZoomChanged: (Double) -> Unit = {},
    overlayInsets: ViewportPadding = ViewportPadding(), trackOverlayPosition: TrackOverlayPosition? = null,
    recordingId: String? = null, recordingChunks: List<RecordingChunk> = emptyList(),
    colors: MapPalette = LocalNavigatorPalette.current.map) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val directionImage = remember(resources.displayMetrics.density, colors.location, colors.locationOutline) {
        locationDirectionImage(resources.displayMetrics.density, colors)
    }
    val currentDirectionImage = rememberUpdatedState(directionImage)
    val trackImages = remember(resources, context) {
        listOf(TRACK_ARROW_IMAGE to R.drawable.ic_track_arrow, TRACK_START_IMAGE to R.drawable.ic_track_start,
            TRACK_FINISH_IMAGE to R.drawable.ic_track_finish, TRACK_LOOP_IMAGE to R.drawable.ic_track_loop)
            .associate { (id, resource) -> id to resources.getDrawable(resource, context.theme) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapFile = remember(context, offlineMap, lifecycle) {
        RememberedMapFile((context.applicationContext as NavigatorApplication).offlineMapRepository.retainMap(offlineMap))
    }
    val preparedTrack by produceState<PreparedTrackOverlay?>(null, track) {
        value = withContext(Dispatchers.Default) { track?.let(::prepareTrackOverlay) }
    }
    var savedCamera by rememberSaveable(offlineMap.file.absolutePath) {
        mutableStateOf<DoubleArray?>(null)
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = MapViewport(viewportSize.width, viewportSize.height, overlayInsets)
    val reportError = rememberUpdatedState(onError)
    val currentColors = rememberUpdatedState(colors)
    val currentTrack = rememberUpdatedState(preparedTrack)
    val currentTrackPosition = rememberUpdatedState(trackOverlayPosition)
    val currentRecordingId = rememberUpdatedState(recordingId)
    val currentRecordingChunks = rememberUpdatedState(recordingChunks)
    val currentLocation = rememberUpdatedState(location)
    val currentFollow = rememberUpdatedState(followEnabled)
    val currentCenterRequest = rememberUpdatedState(centerRequestId)
    val currentOrientation = rememberUpdatedState(orientation)
    val currentViewport = rememberUpdatedState(viewport)
    val manualCameraMove = rememberUpdatedState(onManualCameraMove)
    val currentUserZoom = rememberUpdatedState(userZoom)
    val currentZoomStepCount = rememberUpdatedState(zoomStepCount)
    val userZoomChanged = rememberUpdatedState(onUserZoomChanged)
    val rendered = remember(context, offlineMap, lifecycle) {
        TrackRenderState(lastCenterRequestId = centerRequestId, zoomPolicy = MapZoomPolicy(userZoom),
            observedUserZoom = userZoom, lastZoomStepCount = zoomStepCount, orientation = orientation,
            automaticOverview = savedCamera?.getOrNull(SAVED_OVERVIEW_INDEX)?.let { it == 1.0 } ?: (savedCamera == null),
            bearingPolicy = CameraBearingPolicy(
            savedCamera?.getOrNull(3)?.takeIf { orientation == MapOrientation.COURSE_UP }))
    }
    val mapView = remember(context, offlineMap, lifecycle) {
        val cameraToRestore = savedCamera
        MapLibre.getInstance(context)
        // Bypass ConnectivityManager: this app uses local assets without network permissions.
        MapLibre.setConnected(false)
        NativeMapView(context).apply {
            mapFile.destroyAbandoned = { rendered.disposed = true; onDestroy() }
            onCreate(null)
            addOnDidFailLoadingMapListener { message ->
                if (!rendered.disposed) reportError.value(message)
            }
            getMapAsync { map ->
                if (rendered.disposed) return@getMapAsync
                rendered.map = map
                rendered.interaction = ManualMapInteraction(map,
                    post = { action -> post { action() } },
                    onZoomSelected = { zoom ->
                        rendered.automaticOverview = false
                        val selected = rendered.zoomPolicy.select(zoom, map.minZoomLevel, map.maxZoomLevel)
                        userZoomChanged.value(selected)
                    },
                    onPan = {
                        rendered.automaticOverview = false
                        rendered.followSuspendedByGesture = true
                        manualCameraMove.value()
                    },
                    onZoomFinished = {
                        rendered.cameraLocation = null
                        applyZoomSteps(map, currentZoomStepCount.value, rendered, userZoomChanged.value, reportError.value)
                        applyFollowCamera(map, rendered.location, currentFollow.value,
                            currentCenterRequest.value, currentOrientation.value, currentViewport.value,
                            offlineMap, currentTrack.value,
                            rendered, reportError.value)
                    },
                    onGestureStarted = { rendered.motion?.cancelCamera() })
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
                // Orientation is controlled by the explicit Nord/Marcia selector.
                map.uiSettings.isCompassEnabled = false
                val idleListener = MapLibreMap.OnCameraIdleListener {
                    val camera = map.cameraPosition
                    if (rendered.motion?.cameraAnimating != true) {
                        camera.target?.let { target ->
                            savedCamera = doubleArrayOf(target.latitude, target.longitude,
                                camera.zoom, camera.bearing, camera.tilt, if (rendered.automaticOverview) 1.0 else 0.0)
                        }
                    }
                    rendered.interaction?.onCameraIdle()
                }
                rendered.idleListener = idleListener
                map.setMaxZoomPreference((offlineMap.header.maxZoom + 2).coerceAtMost(24).toDouble())
                val initialColors = currentColors.value
                map.setStyle(Style.Builder().fromJson(offlineMapStyle(offlineMap, initialColors))) { style ->
                    if (rendered.disposed) return@setStyle
                    rendered.styleReady = true
                    rendered.colors = initialColors
                    applyMapColors(style, offlineMap, currentColors.value, currentDirectionImage.value, rendered, reportError.value)
                    style.addImage(DIRECTION_IMAGE, currentDirectionImage.value)
                    trackImages.forEach { (id, drawable) -> style.addImage(id, drawable) }
                    rendered.motion = MapMotionAnimator(onFrame = { point, camera ->
                        if (!rendered.disposed && rendered.styleReady) {
                            updateLocationOverlay(style, point, currentColors.value)
                            if (camera != null && rendered.interaction?.inProgress != true) {
                                map.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
                            }
                        }
                    }, onError = { message -> reportError.value(message) }).also {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) it.resume()
                    }
                    applyTrack(map, style, offlineMap, currentTrack.value, currentColors.value, currentViewport.value, rendered,
                        reportError.value)
                    applyTrackProgress(style, currentTrack.value, currentTrackPosition.value, rendered, reportError.value)
                    if (cameraToRestore != null) {
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                            .target(LatLng(cameraToRestore[0], cameraToRestore[1]))
                            .zoom(cameraToRestore[2]).bearing(cameraToRestore[3])
                            .tilt(cameraToRestore[4]).build()))
                        rendered.preserveRestoredCamera = currentTrack.value == null
                        rendered.automaticOverview = cameraToRestore.getOrNull(SAVED_OVERVIEW_INDEX) == 1.0
                        rendered.cameraPadding = null
                    }
                    applyLocation(currentLocation.value, rendered)
                    rendered.recording.update(style, currentRecordingId.value, currentRecordingChunks.value)
                    syncZoomPreference(rendered, currentUserZoom.value)
                    applyZoomSteps(map, currentZoomStepCount.value, rendered, userZoomChanged.value, reportError.value)
                    applyFollowCamera(map, rendered.location, currentFollow.value,
                        currentCenterRequest.value, currentOrientation.value, currentViewport.value,
                        offlineMap, currentTrack.value,
                        rendered, reportError.value)
                    map.addOnCameraIdleListener(idleListener)
                }
            }
        }
    }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> if (!resumed) {
                    mapView.onResume()
                    resumed = true
                    rendered.motion?.resume()
                    rendered.cameraLocation = null
                    if (rendered.styleReady) rendered.map?.let { map ->
                        applyFollowCamera(map, rendered.location, currentFollow.value,
                            currentCenterRequest.value, currentOrientation.value, currentViewport.value,
                            offlineMap, currentTrack.value, rendered, reportError.value)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> if (resumed) {
                    rendered.motion?.pause()
                    mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> if (started) {
                    mapView.onStop()
                    started = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            rendered.disposed = true
            rendered.styleReady = false
            rendered.motion?.dispose()
            lifecycle.removeObserver(observer)
            rendered.interaction?.dispose()
            rendered.idleListener?.let { listener ->
                rendered.map?.removeOnCameraIdleListener(listener)
            }
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
            mapFile.close()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.onSizeChanged { viewportSize = it }, update = {
        if (rendered.styleReady) rendered.map?.let { map ->
            map.style?.let { style ->
                applyMapColors(style, offlineMap, colors, directionImage, rendered, reportError.value)
                applyTrack(map, style, offlineMap, preparedTrack, colors, viewport, rendered, reportError.value)
                applyTrackProgress(style, preparedTrack, trackOverlayPosition, rendered, reportError.value)
                applyLocation(location, rendered)
                rendered.recording.update(style, recordingId, recordingChunks)
                syncZoomPreference(rendered, userZoom)
                applyZoomSteps(map, zoomStepCount, rendered, userZoomChanged.value, reportError.value)
                applyFollowCamera(map, rendered.location, followEnabled,
                    centerRequestId, orientation, viewport, offlineMap, preparedTrack, rendered, reportError.value)
            }
        }
    })
}

private class RememberedMapFile(private val lease: Closeable) : RememberObserver, Closeable {
    var destroyAbandoned: (() -> Unit)? = null
    override fun onRemembered() = Unit
    // The committed view releases its lease only after native onDestroy in DisposableEffect.
    override fun onForgotten() = Unit
    override fun onAbandoned() { destroyAbandoned?.invoke(); close() }
    override fun close() = lease.close()
}

private class TrackRenderState(var styleReady: Boolean = false, var track: PreparedTrackOverlay? = null,
    var colors: MapPalette? = null,
    val recording: RecordingOverlay = RecordingOverlay(),
    var disposed: Boolean = false, var preserveRestoredCamera: Boolean = false,
    var initialized: Boolean = false, var location: LocationPoint? = null,
    var progressInitialized: Boolean = false, var drawnTrackPosition: TrackOverlayPosition? = null,
    var locationInitialized: Boolean = false, var followEnabled: Boolean = false,
    var lastCenterRequestId: Long = 0, var cameraLocation: LocationPoint? = null,
    val zoomPolicy: MapZoomPolicy = MapZoomPolicy(), var observedUserZoom: Double? = null,
    var lastZoomStepCount: Int = 0, var followSuspendedByGesture: Boolean = false,
    val bearingPolicy: CameraBearingPolicy = CameraBearingPolicy(),
    var orientation: MapOrientation? = null, var viewport: MapViewport = MapViewport(),
    var cameraPadding: ViewportPadding? = null, var automaticOverview: Boolean = true,
    var map: MapLibreMap? = null,
    var idleListener: MapLibreMap.OnCameraIdleListener? = null,
    var motion: MapMotionAnimator? = null,
    var interaction: ManualMapInteraction? = null)

private fun applyMapColors(style: Style, offlineMap: OfflineMap, colors: MapPalette,
    directionImage: android.graphics.Bitmap, rendered: TrackRenderState, onError: (String) -> Unit) {
    if (rendered.colors == colors) return
    rendered.colors = colors
    try {
        updateOfflineMapPaint(style, offlineMap, colors)
        updateTrackOverlayColors(style, colors)
        updateLocationColors(style, colors)
        style.addImage(DIRECTION_IMAGE, directionImage)
    } catch (exception: RuntimeException) {
        onError(exception.message ?: "Impossibile aggiornare i colori della mappa.")
    }
}

private fun syncZoomPreference(rendered: TrackRenderState, selected: Double?) {
    if (rendered.observedUserZoom == selected) return
    rendered.observedUserZoom = selected
    rendered.zoomPolicy.restore(selected)
}

private fun applyZoomSteps(map: MapLibreMap, count: Int, rendered: TrackRenderState,
    onUserZoom: (Double) -> Unit, onError: (String) -> Unit) {
    if (count == rendered.lastZoomStepCount || rendered.interaction?.inProgress == true) return
    val steps = count - rendered.lastZoomStepCount
    rendered.lastZoomStepCount = count
    try {
        val camera = map.cameraPosition
        val zoom = rendered.zoomPolicy.select(camera.zoom + steps, map.minZoomLevel, map.maxZoomLevel)
        rendered.automaticOverview = false
        onUserZoom(zoom)
        rendered.cameraLocation = null
        rendered.motion?.cancelCamera()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera).zoom(zoom).build()))
    } catch (exception: RuntimeException) {
        onError(exception.message ?: "Impossibile modificare lo zoom della mappa.")
    }
}

private fun applyFollowCamera(map: MapLibreMap, location: LocationPoint?,
    followEnabled: Boolean, centerRequestId: Long, orientation: MapOrientation, viewport: MapViewport,
    offlineMap: OfflineMap, track: PreparedTrackOverlay?,
    rendered: TrackRenderState,
    onError: (String) -> Unit) {
    if (rendered.interaction?.inProgress == true || viewport.width <= 0 || viewport.height <= 0) return
    val centerRequested = centerRequestId != rendered.lastCenterRequestId
    val justEnabled = followEnabled && !rendered.followEnabled
    val orientationChanged = orientation != rendered.orientation
    val sizeChanged = viewport != rendered.viewport
    if (!followEnabled || centerRequested || orientationChanged) rendered.followSuspendedByGesture = false
    val centerOnLocation = location != null && ((followEnabled && !rendered.followSuspendedByGesture) ||
        centerRequested || orientationChanged)
    val padding = viewport.cameraPadding(courseUp = centerOnLocation && orientation == MapOrientation.COURSE_UP)
    val paddingChanged = padding != rendered.cameraPadding
    try {
        val camera = map.cameraPosition
        if (!centerOnLocation && !orientationChanged) {
            // A one-shot recenter/orientation change must survive unrelated UI updates.
            if ((!followEnabled && rendered.followEnabled) || rendered.followSuspendedByGesture) {
                rendered.motion?.cancelCamera()
            }
            if (rendered.automaticOverview && sizeChanged) {
                fitOverview(map, offlineMap, track, viewport)
            } else if (paddingChanged) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera)
                    .padding(padding.left, padding.top, padding.right, padding.bottom).build()))
            }
            rendered.lastCenterRequestId = centerRequestId
            rendered.followEnabled = followEnabled
            rendered.viewport = viewport
            rendered.cameraPadding = padding
            return
        }
        val desiredBearing = rendered.bearingPolicy.bearing(orientation, location)
        val desiredZoom = if (!centerOnLocation) camera.zoom else
            rendered.zoomPolicy.followZoom(map.minZoomLevel, map.maxZoomLevel)
        val zoomChanged = abs(camera.zoom - desiredZoom) > 0.01
        if (!centerRequested && !justEnabled && !orientationChanged && !sizeChanged && !paddingChanged && !zoomChanged &&
            rendered.cameraLocation === location) return
        val target = location?.takeIf { centerOnLocation }?.let { LatLng(it.latitude, it.longitude) }
            ?: camera.target ?: return
        val currentCenter = camera.target
        val distance = if (currentCenter == null) Double.POSITIVE_INFINITY else
            cameraDistanceMeters(currentCenter.latitude, currentCenter.longitude,
                target.latitude, target.longitude)
        val forced = centerRequested || justEnabled || orientationChanged || sizeChanged || rendered.cameraLocation == null
        val motion = followCameraMotion(distance, location?.accuracyMeters, forced, location?.speedMetersPerSecond)
        if (motion != CameraMotion.STAY || zoomChanged || paddingChanged ||
            abs(bearingDifference(camera.bearing, desiredBearing)) >= 3) {
            val position = CameraPosition.Builder(camera)
                .target(if (motion == CameraMotion.STAY) currentCenter ?: target else target)
                .zoom(desiredZoom).bearing(desiredBearing)
                .padding(padding.left, padding.top, padding.right, padding.bottom).build()
            if (centerOnLocation) rendered.automaticOverview = false
            rendered.motion?.moveCamera(camera, position, jump = motion == CameraMotion.JUMP)
        }
        rendered.cameraLocation = location
        rendered.lastCenterRequestId = centerRequestId
        rendered.followEnabled = followEnabled
        rendered.orientation = orientation
        rendered.viewport = viewport
        rendered.cameraPadding = padding
    } catch (exception: RuntimeException) {
        rendered.cameraLocation = location
        onError(exception.message ?: "Impossibile seguire la posizione sulla mappa.")
    }
}

private fun applyLocation(location: LocationPoint?, rendered: TrackRenderState) {
    if (rendered.locationInitialized && rendered.location === location) return
    if (rendered.motion?.updateLocation(location) == true) rendered.location = location
    rendered.locationInitialized = true
}

private fun applyTrack(map: MapLibreMap, style: Style, offlineMap: OfflineMap, track: PreparedTrackOverlay?,
    colors: MapPalette, viewport: MapViewport, rendered: TrackRenderState, onError: (String) -> Unit) {
    if (rendered.initialized && rendered.track === track) return
    try {
        replaceTrackOverlay(style, track, colors)
        rendered.progressInitialized = false
        if (rendered.preserveRestoredCamera && !rendered.automaticOverview) {
            rendered.preserveRestoredCamera = false
            rendered.track = track
            rendered.initialized = true
            return
        }
        rendered.preserveRestoredCamera = false
        rendered.motion?.cancelCamera()
        fitOverview(map, offlineMap, track, viewport)
        rendered.automaticOverview = true
        rendered.cameraPadding = viewport.cameraPadding()
        rendered.cameraLocation = null
        rendered.track = track
        rendered.initialized = true
    } catch (exception: RuntimeException) {
        rendered.track = track
        rendered.initialized = true
        onError(exception.message ?: "Errore durante il disegno della traccia.")
    }
}

private fun applyTrackProgress(style: Style, track: PreparedTrackOverlay?, position: TrackOverlayPosition?,
    rendered: TrackRenderState, onError: (String) -> Unit) {
    if (track == null || (rendered.progressInitialized && rendered.drawnTrackPosition == position)) return
    try {
        updateTrackProgressOverlay(style, track, position)
        rendered.drawnTrackPosition = position
        rendered.progressInitialized = true
    } catch (exception: RuntimeException) {
        onError(exception.message ?: "Impossibile aggiornare l’avanzamento sulla traccia.")
    }
}

private fun fitOverview(map: MapLibreMap, offlineMap: OfflineMap, track: PreparedTrackOverlay?, viewport: MapViewport) {
    if (viewport.width <= 0 || viewport.height <= 0) return
    val padding = viewport.cameraPadding()
    val extent = track?.extent
    val camera = if (extent?.isSingleLocation == true) {
        CameraPosition.Builder(map.cameraPosition).target(LatLng(extent.north, extent.east)).zoom(14.0).build()
    } else {
        val bounds = if (extent == null) offlineMap.header.let {
            LatLngBounds.from(it.north, it.east, it.south, it.west)
        } else LatLngBounds.from(extent.north, extent.east, extent.south, extent.west)
        val fit = viewport.cameraPadding(margin = 16.0)
        CameraUpdateFactory.newLatLngBounds(bounds, map.cameraPosition.bearing, map.cameraPosition.tilt,
            fit.left.roundToInt(), fit.top.roundToInt(), fit.right.roundToInt(), fit.bottom.roundToInt())
            .getCameraPosition(map) ?: return
    }
    map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera)
        .padding(padding.left, padding.top, padding.right, padding.bottom).build()))
}
