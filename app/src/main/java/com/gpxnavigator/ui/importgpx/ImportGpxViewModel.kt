package com.gpxnavigator.ui.importgpx

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.NavigatorApplication
import com.gpxnavigator.domain.model.SavedTrack
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.domain.model.TrackSortOrder
import com.gpxnavigator.domain.model.libraryTracks
import com.gpxnavigator.navigation.NavigationSessionState
import com.gpxnavigator.navigation.NavigationService
import com.gpxnavigator.navigation.RecordingState
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.gpxnavigator.data.maps.OfflineMap
import com.gpxnavigator.data.maps.OfflineMapLease
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.navigation.TrackSummary
import com.gpxnavigator.navigation.summarizeTrack

internal data class TrackDetailUiState(
    val id: String? = null,
    val loading: Boolean = false,
    val track: Track? = null,
    val summary: TrackSummary? = null,
    val map: OfflineMap? = null,
    val error: String? = null,
    val mapError: String? = null,
    val sketch: TrackSketch = TrackSketch(),
)

data class ImportGpxUiState(
    val tracks: List<SavedTrack> = emptyList(),
    val selectedTrackId: String? = null,
    val loading: Boolean = true,
    val working: Boolean = false,
    val catalogLoaded: Boolean = false,
    val error: String? = null,
    val session: NavigationSessionState = NavigationSessionState(),
    val recording: RecordingState = RecordingState(),
    val recordGpx: Boolean = false,
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val sortOrder: TrackSortOrder = TrackSortOrder.RECENT,
    val visibleTracks: List<SavedTrack> = tracks,
)

class ImportGpxViewModel(application: Application,
    private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as NavigatorApplication).gpxRepository
    private val controller = (application as NavigatorApplication).navigationController
    private val mutableState = MutableStateFlow(ImportGpxUiState(
        query = savedState["library_query"] ?: "", favoritesOnly = savedState["library_favorites"] ?: false))
    val state = mutableState.asStateFlow()
    private var observeJob: Job? = null
    private var actionJob: Job? = null
    private val maps = (application as NavigatorApplication).offlineMapRepository
    private val mutableDetail = MutableStateFlow(TrackDetailUiState())
    internal val detail = mutableDetail.asStateFlow()
    private var detailJob: Job? = null
    private var detailMapLease: OfflineMapLease? = null

    init {
        reload()
        viewModelScope.launch {
            controller.session.collect { session -> mutableState.update { it.copy(session = session) } }
        }
        viewModelScope.launch {
            controller.recording.collect { recording -> mutableState.update { it.copy(recording = recording) } }
        }
    }

    fun hasLocationPermission(): Boolean = controller.hasLocationPermission()
    fun toggleRecording() { mutableState.update { it.copy(recordGpx = !it.recordGpx) } }
    fun startNavigation(id: String?, record: Boolean, recordingName: String? = null,
        mode: ActivityMode = ActivityMode.forTrack(id)): Boolean =
        NavigationService.requestStart(getApplication(), id, record, recordingName, mode)
    fun stopNavigation() = NavigationService.requestStop(getApplication())
    fun reportActivityConflict() = controller.reportError(getApplication<Application>().getString(com.gpxnavigator.R.string.activity_finish_first))
    fun dismissInterrupted() = perform { controller.stopSession() }
    fun reportPermissionDenied(record: Boolean = false) = controller.reportError(if (record)
        "Concedi la posizione precisa per registrare il GPX." else "Concedi il permesso di posizione per avviare la navigazione.")

    fun reload() {
        observeJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observeJob = viewModelScope.launch {
            try {
                repository.observeCatalog().collect { catalog ->
                    mutableState.update { it.copy(tracks = catalog.tracks,
                        selectedTrackId = catalog.selectedTrackId, sortOrder = catalog.sortOrder,
                        loading = false, catalogLoaded = true).withLibraryResults() }
                }
            } catch (exception: IOException) {
                mutableState.update { it.copy(loading = false, catalogLoaded = false,
                    error = exception.message ?: "Impossibile leggere l'archivio GPX.") }
            }
        }
    }

    fun import(uri: Uri) = perform { repository.import(uri) }
    fun delete(id: String) = perform { repository.delete(id) }

    fun setQuery(query: String) {
        savedState["library_query"] = query
        mutableState.update { it.copy(query = query).withLibraryResults() }
    }

    fun setFavoritesOnly(only: Boolean) {
        savedState["library_favorites"] = only
        mutableState.update { it.copy(favoritesOnly = only).withLibraryResults() }
    }

    fun clearFilters() {
        savedState["library_query"] = ""
        savedState["library_favorites"] = false
        mutableState.update { it.copy(query = "", favoritesOnly = false).withLibraryResults() }
    }

    fun setSortOrder(order: TrackSortOrder) = perform { repository.setSortOrder(order) }
    fun setFavorite(id: String, favorite: Boolean) = perform { repository.setFavorite(id, favorite) }
    fun clearError() { mutableState.update { it.copy(error = null) } }

    fun rename(id: String, name: String, onSuccess: () -> Unit) = perform {
        val session = controller.session.value
        if (session.status == com.gpxnavigator.navigation.SessionStatus.RESTORING ||
            (session.trackId == id && session.status != com.gpxnavigator.navigation.SessionStatus.IDLE))
            throw IOException("Termina la navigazione prima di rinominare questa traccia.")
        repository.rename(id, name)
        onSuccess()
    }

    private fun ImportGpxUiState.withLibraryResults() = copy(
        visibleTracks = libraryTracks(tracks, query, favoritesOnly, sortOrder))

    fun openDetails(id: String) {
        closeDetails()
        mutableDetail.value = TrackDetailUiState(id, loading = true)
        detailJob = viewModelScope.launch {
            try {
                val imported = repository.loadForExport(id)
                val track = repository.loadTrack(imported)
                val (summary, sketch) = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    summarizeTrack(track) { context.ensureActive() } to prepareTrackSketch(track) { context.ensureActive() }
                }
                mutableDetail.update { it.copy(track = track, summary = summary, sketch = sketch) }
                // A missing/broken basemap must not prevent inspecting or following a valid GPX.
                try {
                    detailMapLease = maps.acquireMap()
                    mutableDetail.update { it.copy(map = detailMapLease?.map) }
                } catch (exception: IOException) {
                    mutableDetail.update { it.copy(mapError = exception.message ?: "Mappa offline non disponibile.") }
                } catch (_: SecurityException) {
                    mutableDetail.update { it.copy(mapError = "Accesso alla mappa offline negato.") }
                }
                mutableDetail.update { it.copy(loading = false) }
            } catch (exception: IOException) {
                mutableDetail.update { it.copy(loading = false, error = exception.message ?: "Impossibile leggere la traccia.") }
            } catch (_: SecurityException) {
                mutableDetail.update { it.copy(loading = false, error = "Accesso al GPX negato. Importa nuovamente la traccia.") }
            }
        }
    }

    fun reportPreviewError(message: String) { mutableDetail.update { it.copy(mapError = message) } }

    fun closeDetails() {
        detailJob?.cancel()
        detailJob = null
        detailMapLease?.close()
        detailMapLease = null
        mutableDetail.value = TrackDetailUiState()
    }

    override fun onCleared() {
        detailMapLease?.close()
        super.onCleared()
    }

    private fun perform(action: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            mutableState.update { it.copy(working = true, error = null) }
            try {
                action()
            } catch (exception: IOException) {
                mutableState.update { it.copy(error = exception.message ?: "Impossibile aggiornare le tracce.") }
            } catch (_: SecurityException) {
                mutableState.update { it.copy(error = "Accesso al file negato. Seleziona nuovamente il GPX.") }
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }
}
