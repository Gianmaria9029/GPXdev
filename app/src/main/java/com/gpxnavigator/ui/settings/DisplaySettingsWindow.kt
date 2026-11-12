package com.gpxnavigator.ui.settings

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gpxnavigator.domain.model.ScreenOrientation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

fun ComponentActivity.bindDisplaySettings(settings: StateFlow<DisplaySettingsUiState>) = lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            settings.collect { appearance ->
                val orientation = when (appearance.settings?.orientation) {
                    ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                if (requestedOrientation != orientation) requestedOrientation = orientation
            }
        } finally {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
