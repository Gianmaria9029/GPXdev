package com.gpxnavigator.data.settings

import android.annotation.SuppressLint
import android.content.Context
import com.gpxnavigator.domain.model.MapOrientation
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MapOrientationPreferences(context: Context) {
    private val application = context.applicationContext
    private val preferences by lazy { application.getSharedPreferences("navigation_map", Context.MODE_PRIVATE) }

    suspend fun load(): MapOrientation = withContext(Dispatchers.IO) {
        val value = preferences.all["orientation"] as? String
        MapOrientation.entries.firstOrNull { it.preferenceValue == value } ?: MapOrientation.NORTH_UP
    }

    @SuppressLint("UseKtx")
    suspend fun save(orientation: MapOrientation) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("orientation", orientation.preferenceValue).commit())
            throw IOException("Impossibile salvare l’orientamento della mappa.")
    }
}
