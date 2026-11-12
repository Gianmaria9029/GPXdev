package com.gpxnavigator.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.NavigatorApplication
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OfflineMapInfo(val name: String, val sizeBytes: Long)
data class OfflineMapSettingsState(
    val map: OfflineMapInfo? = null,
    val busy: Boolean = true,
    val importing: Boolean = false,
    val error: String? = null,
    val imported: Boolean = false,
)

class OfflineMapSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as NavigatorApplication).offlineMapRepository
    private val mutableState = MutableStateFlow(OfflineMapSettingsState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun reload() = load(null)
    fun importMap(uri: Uri) = load(uri)

    private fun load(uri: Uri?) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, importing = uri != null, error = null, imported = false) }
            try {
                val info = withContext(Dispatchers.IO) {
                    repository.acquireMap(uri)?.use { lease -> OfflineMapInfo(lease.map.name, lease.map.file.length()) }
                }
                mutableState.value = OfflineMapSettingsState(map = info, busy = false, imported = uri != null)
            } catch (exception: IOException) {
                mutableState.update { it.copy(map = if (uri == null) null else it.map, busy = false, importing = false,
                    error = exception.message ?: "Impossibile leggere la mappa offline.") }
            } catch (_: SecurityException) {
                mutableState.update { it.copy(map = if (uri == null) null else it.map, busy = false, importing = false,
                    error = "Accesso al file negato. Seleziona nuovamente la mappa PMTiles.") }
            }
        }
    }
}
