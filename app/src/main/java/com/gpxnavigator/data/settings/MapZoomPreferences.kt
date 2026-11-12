package com.gpxnavigator.data.settings

import android.annotation.SuppressLint
import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MapZoomPreferences(context: Context) {
    private val application = context.applicationContext
    private val preferences by lazy { application.getSharedPreferences("navigation_map", Context.MODE_PRIVATE) }

    suspend fun load(): Double? = withContext(Dispatchers.IO) {
        (preferences.all["user_zoom"] as? Float)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..24.0 }
    }

    @SuppressLint("UseKtx")
    suspend fun save(zoom: Double) = withContext(Dispatchers.IO) {
        require(zoom.isFinite() && zoom in 0.0..24.0)
        if (!preferences.edit().putFloat("user_zoom", zoom.toFloat()).commit())
            throw IOException("Impossibile salvare lo zoom della mappa.")
    }
}
