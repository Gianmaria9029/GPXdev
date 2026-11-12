package com.gpxnavigator.data.settings

import android.content.Context
import android.annotation.SuppressLint
import com.gpxnavigator.domain.model.AppTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppThemePreferences(context: Context) {
    private val application = context.applicationContext
    private val preferences by lazy { application.getSharedPreferences("appearance", Context.MODE_PRIVATE) }

    suspend fun load(): AppTheme = withContext(Dispatchers.IO) {
        val value = preferences.all["theme"] as? String
        AppTheme.entries.firstOrNull { it.preferenceValue == value } ?: AppTheme.SOFT
    }

    // The KTX edit helper discards commit's result, which is needed to report a failed save.
    @SuppressLint("UseKtx")
    suspend fun save(theme: AppTheme) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("theme", theme.preferenceValue).commit()) {
            throw IOException("Impossibile salvare il tema.")
        }
    }
}
