package com.gpxnavigator.data.navigation

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.database.RecordingEntity
import com.gpxnavigator.data.database.RecordingPointEntity
import com.gpxnavigator.domain.model.normalizedRecordingName
import com.gpxnavigator.domain.model.recordingNameError
import com.gpxnavigator.domain.model.ActivityMode
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingRepository(private val database: NavigatorDatabase) {
    private val dao = database.recordings()
    suspend fun current() = io { dao.current() }
    suspend fun points(id: String) = io { dao.points(id) }
    suspend fun start(trackId: String?, title: String, now: Long, trackName: String? = null,
        mode: ActivityMode = ActivityMode.forTrack(trackId)): RecordingEntity = io {
        if (recordingNameError(trackName) != null) throw IOException("Nome della traccia non valido.")
        database.withTransaction {
            if (dao.current() != null) throw IOException("Salva o scarta la registrazione precedente prima di iniziare.")
            RecordingEntity(UUID.randomUUID().toString(), now, trackId, title,
                trackName = normalizedRecordingName(trackName), activityMode = mode.name).also { dao.insert(it) }
        }
    }
    suspend fun append(point: RecordingPointEntity) = io { dao.insert(point) }
    suspend fun finish(id: String, endedAtMillis: Long) = io { dao.finish(id, endedAtMillis) }
    suspend fun discard(id: String) = io { dao.delete(id) }
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (exception: SQLiteException) {
            throw IOException("Impossibile salvare i punti del giro. I dati già raccolti sono conservati.", exception)
        }
    }
}
