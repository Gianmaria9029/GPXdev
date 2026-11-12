package com.gpxnavigator.ui.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.NavigatorApplication
import com.gpxnavigator.data.gpx.ImportedGpx
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordingDecisionState(val busy: Boolean = false, val error: String? = null,
    val saved: ImportedGpx? = null, val dashboardRequest: Int = 0)

class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as NavigatorApplication).navigationController
    val recording = controller.recording
    val session = controller.session
    private val mutableState = MutableStateFlow(RecordingDecisionState())
    val state = mutableState.asStateFlow()

    fun dashboardShown() { mutableState.value = mutableState.value.copy(dashboardRequest = 0) }

    fun finish(save: Boolean) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val saved = if (save) controller.saveRecording() else { controller.discardRecording(); null }
                mutableState.value = RecordingDecisionState(saved = saved,
                    dashboardRequest = mutableState.value.dashboardRequest + 1)
            } catch (exception: IOException) {
                mutableState.value = mutableState.value.copy(busy = false, error = exception.message ?: "Salvataggio non riuscito.")
            }
        }
    }

}
