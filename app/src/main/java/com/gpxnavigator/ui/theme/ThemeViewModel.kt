package com.gpxnavigator.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.data.settings.AppThemePreferences
import com.gpxnavigator.domain.model.AppTheme
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ThemeUiState(val theme: AppTheme? = null, val saveFailed: Boolean = false)

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppThemePreferences(application)
    private val writeMutex = Mutex()
    private val mutableState = MutableStateFlow(ThemeUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch { mutableState.value = ThemeUiState(theme = preferences.load()) }
    }

    fun select(theme: AppTheme) {
        if (mutableState.value.theme == theme && !mutableState.value.saveFailed) return
        mutableState.update { it.copy(theme = theme, saveFailed = false) }
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    preferences.save(theme)
                } catch (_: IOException) {
                    mutableState.update { if (it.theme == theme) it.copy(saveFailed = true) else it }
                }
            }
        }
    }
}
