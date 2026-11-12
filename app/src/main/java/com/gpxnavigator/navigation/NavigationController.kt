package com.gpxnavigator.navigation

import com.gpxnavigator.data.gpx.LocalGpxRepository
import com.gpxnavigator.data.gpx.ImportedGpx
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.data.navigation.NavigationSessionStore
import com.gpxnavigator.data.navigation.SavedNavigationSession
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.domain.model.normalizedRecordingName
import com.gpxnavigator.location.LocationProvider
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SessionStatus { RESTORING, IDLE, STARTING, ACTIVE, INTERRUPTED }

data class NavigationSessionState(
    val status: SessionStatus = SessionStatus.RESTORING,
    val trackId: String? = null,
    val title: String? = null,
    val error: String? = null,
    val recordingId: String? = null,
    val mode: ActivityMode = ActivityMode.forTrack(trackId),
) {
    val isRunning: Boolean get() = status == SessionStatus.ACTIVE || status == SessionStatus.STARTING
}

data class NavigationData(
    val track: Track? = null,
    val trackId: String? = null,
    val location: LocationState = LocationState(),
    val progress: TrackProgress? = null,
    val offTrack: OffTrackState = OffTrackState(),
    val guidance: TrackGuidance = TrackGuidance(),
    val arrival: ArrivalEstimate = ArrivalEstimate(),
    val maximumSpeedMetersPerSecond: Float? = null,
    val trackError: String? = null,
)

class NavigationController(
    private val repository: LocalGpxRepository,
    private val locationProvider: LocationProvider,
    private val store: NavigationSessionStore,
    private val scope: CoroutineScope,
    private val elapsedMillis: () -> Long,
    private val wallMillis: () -> Long = System::currentTimeMillis,
    private val recordings: RecordingRepository? = null,
) {
    private data class Prepared(val id: String?, val track: Track, val calculator: TrackProgressCalculator,
        val guidance: TrackGuidanceCalculator, val arrival: ArrivalTimeEstimator,
        val maximumSpeed: SessionMaximumSpeed)
    private val mutableSession = MutableStateFlow(NavigationSessionState())
    val session = mutableSession.asStateFlow()
    private val mutableData = MutableStateFlow(NavigationData())
    val data = mutableData.asStateFlow()
    private val prepared = MutableStateFlow<Prepared?>(null)
    private val operations = Mutex()
    private var screenVisible = false
    private var serviceAttached = false
    private var locationRequested = false
    private val monitor = OffTrackMonitor()
    private val recordingLock = Mutex()
    private val accumulator = RecordingAccumulator()
    private val mutableRecording = MutableStateFlow(RecordingState())
    val recording = mutableRecording.asStateFlow()
    private var freeMaximum = SessionMaximumSpeed(elapsedMillis())
    private val restoration = scope.launch {
        try {
            val saved = store.load()
            val draft = recordings?.current()
            if (draft != null) {
                val points = recordings!!.points(draft.id)
                withContext(Dispatchers.Default) { accumulator.restore(draft.id, draft.startedAtMillis, points,
                    ActivityMode.restored(draft.activityMode, draft.sourceTrackId), draft.title, draft.endedAtMillis) }
                mutableRecording.value = accumulator.state.copy(pending = draft.pending)
            }
            val restored = when {
                draft?.pending == true -> NavigationSessionState(SessionStatus.IDLE)
                draft != null -> NavigationSessionState(SessionStatus.INTERRUPTED, draft.sourceTrackId,
                    draft.title, recordingId = draft.id, mode = ActivityMode.restored(draft.activityMode, draft.sourceTrackId))
                saved == null -> NavigationSessionState(SessionStatus.IDLE)
                saved.recordingId != null -> {
                    // A recording already saved/discarded cannot be resumed from a stale preference.
                    store.clear()
                    NavigationSessionState(SessionStatus.IDLE)
                }
                else -> NavigationSessionState(SessionStatus.INTERRUPTED, saved.trackId, saved.title, mode = saved.mode)
            }
            mutableSession.update { restored.copy(error = it.error) }
        } catch (exception: IOException) {
            mutableSession.value = NavigationSessionState(SessionStatus.IDLE, error = exception.message)
        }
    }

    init {
        scope.launch {
            restoration.join()
            combine(locationProvider.state, mutableSession) { location, session -> location to session }
                .collect { (location, _) ->
                    recordingLock.withLock {
                        val id = mutableSession.value.recordingId
                        if (mutableSession.value.status != SessionStatus.ACTIVE || id == null ||
                            mutableRecording.value.error != null) return@withLock
                        if (location.status != LocationStatus.ACTIVE) {
                            accumulator.breakSegment()
                            return@withLock
                        }
                        val candidate = accumulator.candidate(id, location.point, location.precise,
                            elapsedMillis(), wallMillis()) ?: return@withLock
                        try {
                            withContext(NonCancellable) {
                                recordings!!.append(candidate)
                                accumulator.commit(candidate)
                                mutableRecording.value = accumulator.state.copy(active =
                                    mutableSession.value.status == SessionStatus.ACTIVE)
                            }
                        } catch (exception: IOException) {
                            accumulator.breakSegment()
                            mutableRecording.update { it.copy(active = false, error = exception.message) }
                        }
                    }
                }
        }
        scope.launch {
            var previousPrepared: Prepared? = null
            combine(locationProvider.state, prepared, mutableSession) { location, track, session -> Triple(location, track, session) }
                .collectLatest { (location, track, session) ->
                    val now = elapsedMillis()
                    val point = location.point
                    val fixMillis = point?.elapsedRealtimeNanos?.div(1_000_000L) ?: now
                    val fresh = point != null && location.status == LocationStatus.ACTIVE &&
                        fixMillis in 0..now && now - fixMillis <= MAX_OFF_TRACK_FIX_AGE_MILLIS
                    val progress = withContext(Dispatchers.Default) {
                        track?.calculator?.progress(point.takeIf { fresh })
                    }
                    if (track !== previousPrepared) {
                        monitor.reset()
                        previousPrepared = track
                    }
                    val offTrack = if (track == null || point == null || location.status != LocationStatus.ACTIVE)
                        monitor.reset()
                    else monitor.update(progress?.distanceFromTrackMeters, point.accuracyMeters, fixMillis, now)
                    val guidance = if (fresh)
                        track?.guidance?.update(progress, point, offTrack) ?: TrackGuidance()
                    else TrackGuidance()
                    val arrival = if (track != null && session.status == SessionStatus.ACTIVE) {
                        if (fresh) track.arrival.update(progress, point, offTrack, guidance, now, wallMillis())
                        else track.arrival.unavailable(ArrivalStatus.WAITING_FOR_GPS, now)
                    } else {
                        track?.arrival?.reset()
                        ArrivalEstimate()
                    }
                    val maximumSpeed = if (session.status == SessionStatus.ACTIVE) {
                        (track?.maximumSpeed ?: freeMaximum).update(point.takeIf { fresh }, now)
                    } else {
                        track?.maximumSpeed?.reset()
                        freeMaximum.reset()
                        null
                    }
                    mutableData.update { it.copy(track = track?.track, trackId = track?.id,
                        location = location, progress = progress, offTrack = offTrack, guidance = guidance,
                        arrival = arrival, maximumSpeedMetersPerSecond = maximumSpeed) }
                    if (point != null && location.status == LocationStatus.ACTIVE) {
                        val remaining = MAX_OFF_TRACK_FIX_AGE_MILLIS + 1 - (elapsedMillis() - fixMillis)
                        if (remaining > 0) delay(remaining)
                        track?.calculator?.invalidate()
                        val expiredArrival = if (session.status == SessionStatus.ACTIVE)
                            track?.arrival?.unavailable(ArrivalStatus.WAITING_FOR_GPS, elapsedMillis()) ?: ArrivalEstimate()
                            else ArrivalEstimate()
                        mutableData.update { it.copy(location = LocationState(LocationStatus.UNAVAILABLE,
                            precise = location.precise), offTrack = monitor.reset(),
                            guidance = TrackGuidance(), arrival = expiredArrival,
                            progress = progress?.copy(travelledDistanceMeters = null,
                                remainingDistanceMeters = null, distanceFromTrackMeters = null,
                                matchedSegmentIndex = null, matchStatus = TrackMatchStatus.UNAVAILABLE,
                                travelDirection = TrackTravelDirection.UNKNOWN)) }
                    }
                }
        }
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    fun setScreenVisible(visible: Boolean) {
        val alreadyRequested = locationRequested
        screenVisible = visible
        updateLocationDemand()
        if (visible && alreadyRequested) locationProvider.start()
    }

    fun retryLocation() {
        if (!locationRequested) return
        locationProvider.stop()
        locationProvider.start()
    }

    fun attachService() { serviceAttached = true }

    fun detachService() {
        serviceAttached = false
        val current = mutableSession.value
        if (current.isRunning) {
            current.trackId?.let(repository::releaseNavigationTrack)
            mutableSession.value = current.copy(status = SessionStatus.INTERRUPTED)
            accumulator.breakSegment()
            mutableRecording.update { it.copy(active = false) }
        }
        updateLocationDemand()
    }

    fun reportError(message: String) { mutableSession.update { it.copy(error = message) } }

    suspend fun prepareMap() {
        restoration.join()
        operations.withLock {
            // The library selection belongs to the catalogue, not to map exploration.
            if (!mutableSession.value.isRunning) clearTrack()
        }
    }

    private fun clearTrack() {
        prepared.value = null
        mutableData.update { NavigationData(location = it.location) }
    }

    suspend fun startSession(id: String?, record: Boolean = false, recordingName: String? = null,
        mode: ActivityMode = ActivityMode.forTrack(id)) {
        restoration.join()
        operations.withLock {
            val previous = mutableSession.value
            if ((mode == ActivityMode.NAVIGATION) != (id != null)) throw IOException("Attività non compatibile con la traccia richiesta.")
            if (previous.status == SessionStatus.ACTIVE && previous.trackId == id && previous.mode == mode) return
            if (previous.isRunning) throw IOException("Una navigazione è già attiva.")
            if (previous.status == SessionStatus.INTERRUPTED && (previous.mode != mode || previous.trackId != id))
                throw IOException("Riprendi o termina l’attività precedente prima di iniziarne un’altra.")
            if (!serviceAttached) throw IOException("Servizio di navigazione non disponibile.")
            if (!hasLocationPermission()) throw IOException("Concedi il permesso di posizione per avviare la navigazione.")
            val resumeId = previous.recordingId?.takeIf { previous.trackId == id && !mutableRecording.value.pending }
            val shouldRecord = record || resumeId != null || mode == ActivityMode.COCKPIT
            if (shouldRecord && !locationProvider.hasPrecisePermission())
                throw IOException("Concedi la posizione precisa per registrare il GPX.")
            if (id == null && !shouldRecord) throw IOException("Seleziona una traccia o avvia Registrazione libera.")
            if (mutableRecording.value.id != null && resumeId == null)
                throw IOException("Salva o scarta la registrazione precedente prima di iniziare.")
            mutableSession.value = NavigationSessionState(SessionStatus.STARTING, id, mode = mode)
            var committed = false
            var newDraft: String? = null
            try {
                val imported = id?.let { repository.pinNavigationTrack(it) }
                val track = imported?.let { repository.loadTrack(it) }
                val ready = track?.let { makePrepared(id, it) }
                val customName = if (id == null && shouldRecord && resumeId == null) normalizedRecordingName(recordingName) else null
                val title = track?.name?.takeIf { it.isNotBlank() } ?: imported?.displayName ?:
                    (if (resumeId != null) previous.title else customName) ?: if (mode == ActivityMode.COCKPIT) "Cockpit" else "Registrazione libera"
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    recordingLock.withLock {
                        val recordingId = if (resumeId != null) resumeId else if (shouldRecord) {
                            val draft = (recordings ?: throw IOException("Archivio registrazioni non disponibile."))
                                .start(id, title, wallMillis(), customName, mode)
                            newDraft = draft.id
                            accumulator.restore(draft.id, draft.startedAtMillis, emptyList(), mode, title)
                            draft.id
                        } else null
                        store.save(SavedNavigationSession(id, title, recordingId, mode))
                        val startedElapsedMillis = elapsedMillis()
                        prepared.value = ready?.copy(maximumSpeed = SessionMaximumSpeed(startedElapsedMillis))
                        freeMaximum = SessionMaximumSpeed(startedElapsedMillis)
                        accumulator.beginSession(startedElapsedMillis)
                        mutableRecording.value = if (recordingId == null) RecordingState()
                            else accumulator.state.copy(active = serviceAttached)
                        mutableData.update { it.copy(trackError = null) }
                        mutableSession.value = NavigationSessionState(
                            if (serviceAttached) SessionStatus.ACTIVE else SessionStatus.INTERRUPTED,
                            id, title, recordingId = recordingId, mode = mode)
                        if (!serviceAttached && id != null) repository.releaseNavigationTrack(id)
                        updateLocationDemand()
                        committed = true
                    }
                }
            } catch (exception: Exception) {
                if (!committed) {
                    id?.let(repository::releaseNavigationTrack)
                    withContext(NonCancellable) {
                        newDraft?.let { recordings?.finish(it, wallMillis()) }
                        if (newDraft != null) mutableRecording.value = accumulator.state.copy(pending = true)
                    }
                    mutableSession.value = previous.copy(error = if (exception is CancellationException)
                        previous.error else exception.message ?: "Impossibile avviare la navigazione.")
                    updateLocationDemand()
                }
                throw exception
            }
        }
    }

    suspend fun stopSession() {
        restoration.join()
        operations.withLock {
            withContext(NonCancellable) {
                val current = mutableSession.value
                val finalLocation = locationProvider.state.value
                val endedAtMillis = wallMillis()
                current.trackId?.let(repository::releaseNavigationTrack)
                mutableSession.value = NavigationSessionState(SessionStatus.IDLE)
                updateLocationDemand()
                try {
                    recordingLock.withLock {
                        mutableRecording.value.id?.let { id ->
                            if (current.status == SessionStatus.ACTIVE && !mutableRecording.value.pending &&
                                finalLocation.status == LocationStatus.ACTIVE && mutableRecording.value.error == null) {
                                val point = accumulator.candidate(id, finalLocation.point, finalLocation.precise,
                                    elapsedMillis(), wallMillis())
                                if (point != null) try {
                                    recordings!!.append(point)
                                    accumulator.commit(point)
                                    mutableRecording.value = accumulator.state
                                } catch (exception: IOException) {
                                    mutableRecording.update { it.copy(error = exception.message) }
                                }
                            }
                            recordings!!.finish(id, endedAtMillis)
                            mutableRecording.update { it.copy(active = false, pending = true,
                                endedAtMillis = it.endedAtMillis ?: endedAtMillis) }
                        }
                    }
                    store.clear()
                    clearTrack()
                } catch (exception: IOException) {
                    mutableSession.value = current.copy(status = SessionStatus.INTERRUPTED,
                        error = exception.message ?: "Impossibile chiudere la sessione salvata.")
                    mutableRecording.update { it.copy(active = false) }
                }
            }
        }
    }

    suspend fun saveRecording(): ImportedGpx = operations.withLock {
        val id = mutableRecording.value.id?.takeIf { mutableRecording.value.pending }
            ?: throw IOException("Nessuna registrazione da salvare.")
        withContext(NonCancellable) {
            // Keep the draft available for retry if clearing the interrupted session fails.
            store.clear()
            val saved = repository.saveRecording(id)
            mutableRecording.value = RecordingState()
            mutableSession.value = NavigationSessionState(SessionStatus.IDLE)
            saved
        }
    }

    suspend fun discardRecording() = operations.withLock {
        val id = mutableRecording.value.id?.takeIf { mutableRecording.value.pending }
            ?: throw IOException("Nessuna registrazione da scartare.")
        withContext(NonCancellable) {
            store.clear()
            recordings!!.discard(id)
            mutableRecording.value = RecordingState()
            mutableSession.value = NavigationSessionState(SessionStatus.IDLE)
        }
    }

    private suspend fun makePrepared(id: String?, track: Track): Prepared = withContext(Dispatchers.Default) {
        Prepared(id, track, TrackProgressCalculator(track), TrackGuidanceCalculator(track), ArrivalTimeEstimator(track),
            SessionMaximumSpeed(elapsedMillis()))
    }

    private fun updateLocationDemand() {
        val wanted = screenVisible || (serviceAttached && mutableSession.value.status == SessionStatus.ACTIVE)
        if (wanted == locationRequested) return
        locationRequested = wanted
        if (wanted) locationProvider.start() else locationProvider.stop()
    }
}
