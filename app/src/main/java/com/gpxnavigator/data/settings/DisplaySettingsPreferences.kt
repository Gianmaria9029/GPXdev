package com.gpxnavigator.data.settings

import android.annotation.SuppressLint
import android.content.Context
import com.gpxnavigator.domain.model.DisplaySettings
import com.gpxnavigator.domain.model.ScreenOrientation
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DisplaySettingsPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    suspend fun load(): DisplaySettings = withContext(Dispatchers.IO) {
        val values = preferences.all
        DisplaySettings(
            ScreenOrientation.entries.firstOrNull { it.preferenceValue == values["orientation"] } ?: ScreenOrientation.AUTOMATIC)
    }

    @SuppressLint("UseKtx") // commit's result is needed to report a failed save.
    suspend fun save(settings: DisplaySettings) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("orientation", settings.orientation.preferenceValue).commit()) {
            throw IOException("Impossibile salvare le impostazioni dello schermo.")
        }
    }
}
