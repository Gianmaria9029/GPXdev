package com.gpxnavigator.ui.navigation

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.data.maps.OfflineMap
import com.gpxnavigator.NavigatorApplication
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.MapAppearance
import com.gpxnavigator.domain.model.MapOrientation
import com.gpxnavigator.data.settings.MapAppearancePreferences
import com.gpxnavigator.data.settings.MapOrientationPreferences
import com.gpxnavigator.data.settings.MapZoomPreferences
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.navigation.NavigationSessionState
import com.gpxnavigator.navigation.NavigationService
import com.gpxnavigator.navigation.OffTrackState
import com.gpxnavigator.navigation.TrackProgress
import com.gpxnavigator.navigation.TrackGuidance
import com.gpxnavigator.navigation.ArrivalEstimate
import com.gpxnavigator.ui.map.TrackOverlayPosition
import com.gpxnavigator.ui.map.confirmedTrackOverlayPosition
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.navigation.RecordingState
import java.io.IOException
import java.io.Closeable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NavigationUiState(
    val map: OfflineMap? = null,
    val track: Track? = null,
    val location: LocationState = LocationState(),
    val progress: TrackProgress? = null,
    val offTrack: OffTrackState = OffTrackState(),
    val guidance: TrackGuidance = TrackGuidance(),
    val arrival: ArrivalEstimate = ArrivalEstimate(),
    val maximumSpeedMetersPerSecond: Float? = null,
    val trackOverlayPosition: TrackOverlayPosition? = null,
    val isFollowingLocation: Boolean = true,
    val mapOrientation: MapOrientation = MapOrientation.NORTH_UP,
    val mapAppearance: MapAppearance = MapAppearance.LIGHT,
    val centerRequestId: Long = 0,
    val userZoom: Double? = null,
    val zoomStepCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val trackError: String? = null,
    val session: NavigationSessionState = NavigationSessionState(),
    val recording: RecordingState = RecordingState(),
    val recordingElapsedMillis: Long? = null,
) {
    val isFreeRecording: Boolean get() = session.isRunning && session.trackId == null
}

class NavigationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as NavigatorApplication).offlineMapRepository
    private val controller = (application as NavigatorApplication).navigationController
    private val orientationPreferences = MapOrientationPreferences(application)
    private val orientationWrites = Mutex()
    private var orientationSelected = false
    private val appearancePreferences = MapAppearancePreferences(application)
    private val appearanceWrites = Mutex()
    private var appearanceSelected = false
    private val zoomPreferences = MapZoomPreferences(application)
    private val zoomWrites = Mutex()
    private var zoomSelected = false
    private val mutableState = MutableStateFlow(NavigationUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var mapEntryJob: Job? = null
    private var mapLease: Closeable? = null
    private val recordingClock = RecordingElapsedClock()

    init {
        loadMap()
        viewModelScope.launch {
            val orientation = orientationPreferences.load()
            if (!orientationSelected) mutableState.update { it.copy(mapOrientation = orientation) }
        }
        viewModelScope.launch {
            val appearance = appearancePreferences.load()
            if (!appearanceSelected) mutableState.update { it.copy(mapAppearance = appearance) }
        }
        viewModelScope.launch {
            val zoom = zoomPreferences.load()
            if (!zoomSelected) mutableState.update { it.copy(userZoom = zoom) }
        }
        viewModelScope.launch {
            controller.recording.collect { recording -> mutableState.update { it.copy(recording = recording) } }
        }
        viewModelScope.launch {
            combine(controller.recording, controller.session) { recording, session ->
                Triple(recording.id, recording.startedAtMillis,
                    session.status == SessionStatus.ACTIVE && session.trackId == null &&
                        recording.id != null && session.recordingId == recording.id)
            }.distinctUntilChanged().collectLatest { (id, startedAt, running) ->
                if (id == null || !running) {
                    if (id == null) recordingClock.clear()
                    mutableState.update { it.copy(recordingElapsedMillis = null) }
                    return@collectLatest
                }
                while (true) {
                    val elapsed = recordingClock.elapsedMillis(id, startedAt, System.currentTimeMillis(), SystemClock.elapsedRealtime())
                    mutableState.update { it.copy(recordingElapsedMillis = elapsed) }
                    delay(1_000)
                }
            }
        }
        viewModelScope.launch {
            combine(controller.data, controller.session) { data, session -> data to session }
                .collect { (data, session) ->
                    mutableState.update { previous -> previous.copy(track = data.track, location = data.location,
                        trackOverlayPosition = confirmedTrackOverlayPosition(data.progress, data.offTrack.status,
                            session.status == SessionStatus.ACTIVE,
                            previous.trackOverlayPosition.takeIf { data.track === previous.track }),
                        progress = data.progress, offTrack = data.offTrack, guidance = data.guidance, arrival = data.arrival,
                        maximumSpeedMetersPerSecond = data.maximumSpeedMetersPerSecond,
                        trackError = data.trackError, session = session) }
                }
        }
    }

    fun hasLocationPermission(): Boolean = controller.hasLocationPermission()
    fun startLocation() = controller.setScreenVisible(true)
    fun stopLocation() = controller.setScreenVisible(false)
    fun retryLocation() = controller.retryLocation()
    fun stopNavigation() = NavigationService.requestStop(getApplication())

    fun setFollowingLocation(enabled: Boolean) {
        mutableState.update { it.copy(isFollowingLocation = enabled) }
    }

    fun setMapOrientation(orientation: MapOrientation) {
        orientationSelected = true
        mutableState.update { it.copy(mapOrientation = orientation, isFollowingLocation = true,
            centerRequestId = it.centerRequestId + 1) }
        viewModelScope.launch {
            orientationWrites.withLock {
                try { orientationPreferences.save(orientation) }
                catch (exception: IOException) { controller.reportError(exception.message ?: "Impossibile salvare l’orientamento.") }
            }
        }
    }

    fun setMapAppearance(appearance: MapAppearance) {
        appearanceSelected = true
        mutableState.update { it.copy(mapAppearance = appearance) }
        viewModelScope.launch {
            appearanceWrites.withLock {
                try {
                    appearancePreferences.save(appearance)
                } catch (exception: IOException) {
                    controller.reportError(exception.message ?: "Impossibile salvare l’aspetto della mappa.")
                }
            }
        }
    }

    fun zoomBy(step: Int) {
        mutableState.update { it.copy(zoomStepCount = it.zoomStepCount + step.coerceIn(-1, 1)) }
    }

    fun selectZoom(zoom: Double) {
        if (!zoom.isFinite() || zoom !in 0.0..24.0) return
        zoomSelected = true
        mutableState.update { it.copy(userZoom = zoom) }
        viewModelScope.launch {
            zoomWrites.withLock {
                try { zoomPreferences.save(zoom) }
                catch (exception: IOException) { controller.reportError(exception.message ?: "Impossibile salvare lo zoom.") }
            }
        }
    }

    fun loadMap(uri: Uri? = null) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val loaded = repository.acquireMap(uri)
                val previousLease = mapLease
                mapLease = loaded
                mutableState.update { it.copy(map = loaded?.map, loading = false, error = null) }
                previousLease?.close()
            } catch (exception: IOException) {
                if (uri == null) { mapLease?.close(); mapLease = null }
                mutableState.update { it.copy(map = if (uri == null) null else it.map,
                    loading = false, error = exception.message ?: "Impossibile leggere la mappa.") }
            } catch (exception: SecurityException) {
                if (uri == null) { mapLease?.close(); mapLease = null }
                mutableState.update { it.copy(map = if (uri == null) null else it.map,
                    loading = false, error = "Accesso al file negato. Seleziona nuovamente la mappa.") }
            }
        }
    }

    fun prepareMap() {
        if (mapEntryJob?.isActive == true) return
        mapEntryJob = viewModelScope.launch { controller.prepareMap() }
    }

    fun reportRenderError(message: String) {
        mutableState.update { it.copy(error = "Errore di visualizzazione: $message") }
    }

    override fun onCleared() {
        mapLease?.close()
        mapLease = null
        controller.setScreenVisible(false)
        super.onCleared()
    }
}
