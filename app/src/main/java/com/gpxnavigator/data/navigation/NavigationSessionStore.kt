package com.gpxnavigator.data.navigation

import android.content.Context
import android.annotation.SuppressLint
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gpxnavigator.domain.model.ActivityMode

data class SavedNavigationSession(val trackId: String?, val title: String, val recordingId: String? = null,
    val mode: ActivityMode = ActivityMode.forTrack(trackId))

interface NavigationSessionStore {
    suspend fun load(): SavedNavigationSession?
    suspend fun save(session: SavedNavigationSession)
    suspend fun clear()
}

// SharedPreferences.edit KTX returns Unit; the commit result is needed to report storage failures.
@SuppressLint("UseKtx")
class PreferencesNavigationSessionStore(context: Context) : NavigationSessionStore {
    private val preferences = context.getSharedPreferences("navigation_session", Context.MODE_PRIVATE)

    override suspend fun load(): SavedNavigationSession? = withContext(Dispatchers.IO) {
        val id = preferences.getString("track_id", null)?.takeIf { it.isNotBlank() }
        val recordingId = preferences.getString("recording_id", null)
        if (id == null && recordingId == null) return@withContext null
        SavedNavigationSession(id, preferences.getString("title", "Traccia GPX") ?: "Traccia GPX", recordingId,
            ActivityMode.restored(preferences.getString("activity_mode", null), id))
    }

    override suspend fun save(session: SavedNavigationSession) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("track_id", session.trackId).putString("recording_id", session.recordingId)
                .putString("title", session.title).putString("activity_mode", session.mode.name).commit()) {
            throw IOException("Impossibile salvare la sessione di navigazione.")
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        if (!preferences.edit().clear().commit()) throw IOException("Impossibile chiudere la sessione salvata.")
    }
}
