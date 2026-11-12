package com.gpxnavigator.data.settings

import android.annotation.SuppressLint
import android.content.Context
import com.gpxnavigator.domain.model.MapAppearance
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MapAppearancePreferences(context: Context) {
    private val application = context.applicationContext
    private val preferences by lazy { application.getSharedPreferences("navigation_map", Context.MODE_PRIVATE) }

    suspend fun load(): MapAppearance = withContext(Dispatchers.IO) {
        val value = preferences.all["appearance"] as? String
        MapAppearance.entries.firstOrNull { it.preferenceValue == value } ?: MapAppearance.LIGHT
    }

    @SuppressLint("UseKtx")
    suspend fun save(appearance: MapAppearance) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("appearance", appearance.preferenceValue).commit())
            throw IOException("Impossibile salvare l’aspetto della mappa.")
    }
}
