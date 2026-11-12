package com.gpxnavigator.ui.cockpit

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.NavigatorApplication
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.navigation.NavigationService
import com.gpxnavigator.navigation.NavigationSessionState
import com.gpxnavigator.navigation.RecordingState
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.navigation.RecordingElapsedClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CockpitUiState(
    val session: NavigationSessionState = NavigationSessionState(),
    val recording: RecordingState = RecordingState(),
    val location: LocationState = LocationState(),
    val elapsedMillis: Long? = null,
)

/** Only observes Cockpit activities, using the shared recording engine without acquiring a map. */
class CockpitViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as NavigatorApplication).navigationController
    private val mutableState = MutableStateFlow(CockpitUiState())
    val state = mutableState.asStateFlow()
    private val clock = RecordingElapsedClock()

    init {
        viewModelScope.launch {
            combine(controller.data, controller.session, controller.recording) { data, session, recording ->
                Triple(data, session, recording)
            }.collect { (data, session, recording) ->
                if (session.mode == ActivityMode.COCKPIT || recording.mode == ActivityMode.COCKPIT)
                    mutableState.update { it.copy(session = session, recording = recording, location = data.location) }
                else mutableState.value = CockpitUiState(session = session)
            }
        }
        viewModelScope.launch {
            combine(controller.session, controller.recording) { session, recording ->
                Triple(recording.id, recording.startedAtMillis,
                    session.mode == ActivityMode.COCKPIT && session.status == SessionStatus.ACTIVE &&
                        recording.id != null && session.recordingId == recording.id)
            }.distinctUntilChanged().collectLatest { (id, startedAt, running) ->
                if (id == null || !running) {
                    if (id == null) clock.clear()
                    mutableState.update { it.copy(elapsedMillis = null) }
                    return@collectLatest
                }
                while (true) {
                    val elapsed = clock.elapsedMillis(id, startedAt, System.currentTimeMillis(), SystemClock.elapsedRealtime())
                    mutableState.update { it.copy(elapsedMillis = elapsed) }
                    delay(1_000)
                }
            }
        }
    }

    fun hasLocationPermission(): Boolean = controller.hasLocationPermission()
    fun setVisible(visible: Boolean) {
        if (!visible || controller.session.value.mode == ActivityMode.COCKPIT) controller.setScreenVisible(visible)
    }
    fun retryLocation() = controller.retryLocation()
    fun stop(): Boolean = controller.session.value.mode == ActivityMode.COCKPIT && NavigationService.requestStop(getApplication())
}
