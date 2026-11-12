package com.gpxnavigator.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.data.settings.DisplaySettingsPreferences
import com.gpxnavigator.domain.model.DisplaySettings
import com.gpxnavigator.domain.model.ScreenOrientation
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DisplaySettingsUiState(val settings: DisplaySettings? = null, val saveFailed: Boolean = false)

class DisplaySettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = DisplaySettingsPreferences(application)
    private val writes = Mutex()
    private val mutableState = MutableStateFlow(DisplaySettingsUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch { mutableState.value = DisplaySettingsUiState(preferences.load()) }
    }

    fun setOrientation(orientation: ScreenOrientation) { state.value.settings?.let { select(it.copy(orientation = orientation)) } }
    fun retrySave() { state.value.settings?.let(::select) }

    private fun select(settings: DisplaySettings) {
        mutableState.value = DisplaySettingsUiState(settings)
        viewModelScope.launch {
            writes.withLock {
                try {
                    preferences.save(settings)
                } catch (_: IOException) {
                    mutableState.update { if (it.settings == settings) it.copy(saveFailed = true) else it }
                }
            }
        }
    }
}
