package com.gpxnavigator.ui.components

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.NavigatorApplication
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GpxExportState(val requestName: String? = null, val busy: Boolean = false,
    val message: String? = null)

class GpxExportViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as NavigatorApplication).gpxRepository
    private val mutableState = MutableStateFlow(GpxExportState(requestName = savedState[LAUNCH_NAME]))
    val state = mutableState.asStateFlow()

    init {
        if (savedState.get<Boolean>(COPYING) == true) {
            clearPending()
            mutableState.value = GpxExportState(message = "Esportazione interrotta. Riprova dalla traccia in archivio.")
        }
    }

    fun request(id: String) {
        if (mutableState.value.busy || savedState.get<String>(TRACK_ID) != null) return
        mutableState.value = GpxExportState(busy = true)
        viewModelScope.launch {
            try {
                val track = repository.loadForExport(id)
                savedState[TRACK_ID] = id
                savedState[LAUNCH_NAME] = track.displayName
                mutableState.value = GpxExportState(requestName = track.displayName)
            } catch (exception: IOException) {
                mutableState.value = GpxExportState(message = exception.message ?: "Traccia non disponibile per l’esportazione.")
            }
        }
    }

    fun pickerLaunched() {
        savedState[LAUNCH_NAME] = null
        mutableState.update { it.copy(requestName = null) }
    }

    fun export(uri: Uri?) {
        if (uri == null) {
            clearPending()
            mutableState.value = GpxExportState()
            return
        }
        val id = savedState.get<String>(TRACK_ID)
        if (id == null) {
            mutableState.value = GpxExportState(message = "Richiesta di esportazione non disponibile. Riprova dall’archivio.")
            return
        }
        if (mutableState.value.busy) return
        savedState[COPYING] = true
        mutableState.value = GpxExportState(busy = true)
        viewModelScope.launch {
            val message = try {
                repository.export(id, uri)
                "GPX esportato."
            } catch (exception: IOException) {
                exception.message ?: "Esportazione non riuscita. Riprova dall’archivio."
            } catch (_: SecurityException) {
                "Accesso alla destinazione negato. Scegli un’altra destinazione."
            } finally {
                clearPending()
            }
            mutableState.value = GpxExportState(message = message)
        }
    }

    fun messageShown() { mutableState.update { it.copy(message = null) } }

    private fun clearPending() {
        savedState[TRACK_ID] = null
        savedState[LAUNCH_NAME] = null
        savedState[COPYING] = false
    }

    private companion object {
        const val TRACK_ID = "gpx_export_track_id"
        const val LAUNCH_NAME = "gpx_export_launch_name"
        const val COPYING = "gpx_export_copying"
    }
}
