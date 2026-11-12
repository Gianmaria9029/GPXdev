package com.gpxnavigator.data.gpx

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.gpxnavigator.data.database.CatalogSettings
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.database.TrackEntity
import com.gpxnavigator.data.database.RecordingCounter
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import com.gpxnavigator.domain.model.GpxCatalog
import com.gpxnavigator.domain.model.SavedTrack
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackSortOrder
import com.gpxnavigator.domain.model.trackNameError
import com.gpxnavigator.domain.model.normalizedRecordingName
import com.gpxnavigator.navigation.TrackProgressCalculator
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ImportedGpx(val file: File, val displayName: String, val sizeBytes: Long, val id: String? = null)
data class LoadedGpx(val imported: ImportedGpx, val track: Track)

class LocalGpxRepository(
    context: Context,
    private val database: NavigatorDatabase,
    private val directory: File = File(context.filesDir, "gpx"),
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("imported_gpx", Context.MODE_PRIVATE)
    private val dao = database.tracks()
    private val mutations = Mutex()
    private var initialized = false
    private val navigationTrackId = AtomicReference<String?>(null)

    fun observeCatalog(): Flow<GpxCatalog> = flow {
        access { Unit }
        emitAll(combine(dao.observeTracks(), dao.observeSettings()) { rows, settings ->
            GpxCatalog(rows.map { row ->
                val track = row.track
                SavedTrack(track.id, track.displayName, track.trackName, track.importedAtMillis,
                    track.sizeBytes, track.pointCount, track.segmentCount, track.totalDistanceMeters,
                    track.problem, com.gpxnavigator.domain.model.ActivityMode.entries.firstOrNull { it.name == track.activityMode },
                    track.customName, track.isFavorite)
            }, rows.firstOrNull { it.isSelected }?.track?.id, TrackSortOrder.restored(settings?.sortOrder))
        })
    }.catch { exception ->
        if (exception is SQLiteException) throw IOException("Archivio GPX non disponibile.", exception)
        throw exception
    }.flowOn(Dispatchers.IO)

    suspend fun loadSelected(): ImportedGpx? = access {
        val entry = dao.selected() ?: return@access null
        val file = checkedFile(entry.fileName)
        if (!file.isFile) throw FileNotFoundException("La traccia selezionata non è disponibile. Importa di nuovo il GPX.")
        ImportedGpx(file, entry.displayName, file.length(), entry.id)
    }

    suspend fun pinNavigationTrack(id: String): ImportedGpx = access {
        if (navigationTrackId.get() != null && navigationTrackId.get() != id)
            throw IOException("Termina la navigazione prima di cambiare traccia.")
        val entry = dao.find(id) ?: throw IOException("La traccia non è più presente nell'archivio.")
        entry.problem?.let { throw IOException(it) }
        val file = checkedFile(entry.fileName)
        if (!file.isFile) throw IOException("File GPX mancante. Importa di nuovo la traccia.")
        dao.select(id)
        navigationTrackId.set(id)
        ImportedGpx(file, entry.displayName, file.length(), id)
    }

    fun releaseNavigationTrack(id: String) {
        navigationTrackId.compareAndSet(id, null)
    }

    suspend fun loadTrack(imported: ImportedGpx): Track = access {
        val entry = imported.id?.let { dao.find(it) }?.takeIf {
            it.problem == null && checkedFile(it.fileName).canonicalFile == imported.file.canonicalFile
        }
        val defaults = GpxLimits()
        // Saved recordings may exceed import budgets; only trust already validated catalog metadata.
        val limits = if (entry == null) defaults else defaults.copy(
            maxBytes = maxOf(defaults.maxBytes, entry.sizeBytes),
            maxPoints = maxOf(defaults.maxPoints, entry.pointCount ?: 0),
            maxSegments = maxOf(defaults.maxSegments, entry.segmentCount ?: 0),
        )
        val coroutineContext = currentCoroutineContext()
        val parsed = imported.file.inputStream().use { GpxParser(limits).parse(it) { coroutineContext.ensureActive() } }
        entry?.customName?.let { parsed.copy(name = it) } ?: parsed
    }

    suspend fun loadForExport(id: String): ImportedGpx = access {
        val entry = dao.find(id) ?: throw IOException("La traccia non è più presente nell'archivio.")
        val file = checkedFile(entry.fileName)
        if (!file.isFile) throw FileNotFoundException("File GPX mancante. Importa di nuovo la traccia.")
        ImportedGpx(file, entry.customName?.let { "$it.gpx" } ?: entry.displayName, file.length(), entry.id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = access {
        if (dao.setFavorite(id, favorite) == 0) throw IOException("La traccia non è più presente nell'archivio.")
    }

    suspend fun setSortOrder(order: TrackSortOrder) = access { dao.setSortOrder(order.name) }

    suspend fun rename(id: String, name: String) = access {
        if (navigationTrackId.get() == id)
            throw IOException("Termina la navigazione prima di rinominare questa traccia.")
        if (trackNameError(name) != null) throw IOException("Inserisci un nome valido da 1 a 80 caratteri.")
        if (dao.rename(id, normalizedRecordingName(name)!!) == 0)
            throw IOException("La traccia non è più presente nell'archivio.")
    }

    suspend fun export(id: String, uri: Uri) = access {
        val entry = dao.find(id) ?: throw IOException("La traccia non è più presente nell'archivio.")
        val coroutineContext = currentCoroutineContext()
        // Keep the catalog lock through the copy so deletion cannot remove the source during export.
        checkedFile(entry.fileName).inputStream().use { input ->
            val output = appContext.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Destinazione non disponibile.")
            output.use { destination ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                }
            }
        }
    }

    suspend fun select(id: String) = access {
        if (navigationTrackId.get() != null && navigationTrackId.get() != id)
            throw IOException("Termina la navigazione prima di cambiare traccia.")
        val entry = dao.find(id) ?: throw IOException("La traccia non è più presente nell'archivio.")
        entry.problem?.let { throw IOException(it) }
        if (!checkedFile(entry.fileName).isFile) throw FileNotFoundException("File GPX mancante. Importa di nuovo la traccia.")
        dao.select(id)
    }

    // Platform logging is intentional for exceptional cleanup failures.
    @SuppressLint("LogNotTimber")
    suspend fun import(uri: Uri): LoadedGpx = access {
        val name = appContext.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: throw IOException("Impossibile leggere il nome del file selezionato.")
        if (!name.endsWith(".gpx", ignoreCase = true)) throw IOException("Seleziona un file con estensione .gpx.")
        ensureDirectory()
        val id = UUID.randomUUID().toString()
        val target = checkedFile("$id.gpx")
        val partial = File(directory, "${target.name}.part")
        var registered = false
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Il file GPX selezionato non è leggibile.")
            input.use { source ->
                partial.outputStream().use { destination ->
                    val coroutineContext = currentCoroutineContext()
                    copyLimitedGpx(source, destination, GpxLimits()) { coroutineContext.ensureActive() }
                }
            }
            if (partial.length() == 0L) throw IOException("Il file GPX selezionato è vuoto.")
            val coroutineContext = currentCoroutineContext()
            val track = partial.inputStream().use { GpxParser().parse(it) { coroutineContext.ensureActive() } }
            val entry = metadata(id, target.name, name, partial.length(), System.currentTimeMillis(), track)
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(target)) throw IOException("Impossibile completare il salvataggio del GPX.")
            // Once a row is committed its file must survive cancellation of the caller.
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.insert(entry)
                    if (navigationTrackId.get() == null) dao.select(id)
                }
                registered = true
            }
            LoadedGpx(ImportedGpx(target, name, target.length(), id), track)
        } finally {
            if (!registered) {
                for (file in listOf(partial, target)) {
                    if (file.exists() && !file.delete()) {
                        android.util.Log.w("LocalGpxRepository", "Impossibile rimuovere una copia GPX incompleta.")
                    }
                }
            }
        }
    }

    suspend fun delete(id: String) = access {
        if (navigationTrackId.get() == id) throw IOException("Termina la navigazione prima di eliminare questa traccia.")
        val entry = dao.find(id) ?: return@access
        val file = checkedFile(entry.fileName)
        val trash = File(directory, "${file.name}.trash")
        if (file.exists() && !file.renameTo(trash)) throw IOException("Impossibile eliminare il file GPX.")
        var removed = false
        try {
            withContext(NonCancellable) {
                database.withTransaction { dao.delete(id) }
                removed = true
            }
        } catch (exception: Exception) {
            if (!removed && trash.exists() && !trash.renameTo(file)) {
                exception.addSuppressed(IOException("Il file GPX sarà ripristinato al prossimo avvio."))
            }
            throw exception
        } finally {
            if (removed && trash.exists() && !trash.delete()) {
                throw IOException("Traccia rimossa dall'elenco; pulizia del file da riprovare al prossimo avvio.")
            }
        }
    }

    @SuppressLint("LogNotTimber") // Retain a failed cleanup diagnostic without another logging library.
    suspend fun saveRecording(recordingId: String): ImportedGpx = access {
        val recordings = database.recordings()
        val session = recordings.current()?.takeIf { it.id == recordingId && it.pending }
            ?: throw IOException("Registrazione non disponibile per il salvataggio.")
        val points = recordings.points(recordingId)
        if (points.isEmpty()) throw IOException("Nessuna posizione GPS registrata. Puoi scartare questo giro.")
        val next = (recordings.counter() ?: 0L) + 1
        val date = Instant.ofEpochMilli(session.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val name = session.trackName ?: "Giro_${date}_${String.format(Locale.ROOT, "%03d", next)}"
        val id = recordingId
        val target = checkedFile("$id.gpx")
        val partial = File(directory, "${target.name}.part")
        var registered = false
        try {
            partial.outputStream().use { GpxWriter.write(it, name, points, session.startedAtMillis, session.endedAtMillis) }
            // Import budgets must not strand points already recorded safely in our database.
            val defaults = GpxLimits()
            val recordedLimits = defaults.copy(maxBytes = maxOf(defaults.maxBytes, partial.length()),
                maxPoints = maxOf(defaults.maxPoints, points.size), maxSegments = maxOf(defaults.maxSegments, points.size))
            val track = partial.inputStream().use { GpxParser(recordedLimits).parse(it) }
            val entry = metadata(id, target.name, "$name.gpx", partial.length(), System.currentTimeMillis(), track)
                .copy(activityMode = session.activityMode)
            currentCoroutineContext().ensureActive()
            // Reuse the same target after process death between rename and database commit.
            if (target.exists() && !target.delete()) throw IOException("Impossibile ripristinare il salvataggio del giro.")
            if (!partial.renameTo(target)) throw IOException("Impossibile completare il salvataggio del giro.")
            withContext(NonCancellable) {
                // File registration, sequence allocation and draft removal succeed as one database transaction.
                database.withTransaction {
                    dao.insert(entry)
                    if (navigationTrackId.get() == null) dao.select(id)
                    recordings.counter(RecordingCounter(value = next))
                    recordings.delete(recordingId)
                }
                registered = true
            }
            ImportedGpx(target, entry.displayName, target.length(), id)
        } finally {
            if (!registered) for (file in listOf(partial, target)) {
                if (file.exists() && !file.delete()) android.util.Log.w("LocalGpxRepository", "Copia del giro incompleta da pulire.")
            }
        }
    }

    private suspend fun <T> access(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutations.withLock {
            try {
                initialize()
                block()
            } catch (exception: SQLiteException) {
                throw IOException("Impossibile aggiornare l'archivio GPX.", exception)
            }
        }
    }

    private suspend fun initialize() {
        if (initialized) return
        ensureDirectory()
        if (dao.settings()?.legacyMigrationCompleted != true) migrateLegacyTrack()
        // Restore an interrupted deletion if its catalog row still exists.
        for (trash in directory.listFiles().orEmpty().filter { it.name.endsWith(".gpx.trash") }) {
            val file = checkedFile(trash.name.removeSuffix(".trash"))
            if (dao.findByFileName(file.name) != null && !file.exists()) {
                if (!trash.renameTo(file)) throw IOException("Impossibile ripristinare una traccia GPX.")
            } else if (!trash.delete()) throw IOException("Impossibile completare la pulizia di una traccia eliminata.")
        }
        for (partial in directory.listFiles().orEmpty().filter { it.name.endsWith(".gpx.part") }) {
            if (!partial.delete()) throw IOException("Impossibile rimuovere una copia GPX incompleta.")
        }
        initialized = true
    }

    private suspend fun migrateLegacyTrack() {
        val filename = preferences.getString("file", null)
        val legacy = filename?.let {
            val file = checkedFile(it)
            val name = preferences.getString("name", "Traccia GPX") ?: "Traccia GPX"
            val id = UUID.nameUUIDFromBytes(file.name.toByteArray(Charsets.UTF_8)).toString()
            try {
                val track = file.inputStream().use { input -> GpxParser().parse(input) }
                metadata(id, file.name, name, file.length(), file.lastModified(), track)
            } catch (exception: IOException) {
                // Keep an identifiable entry so a missing/invalid legacy file can be removed by the user.
                TrackEntity(id, file.name, name, null, file.lastModified(), file.length(),
                    null, null, null, exception.message ?: "File GPX non disponibile.")
            }
        }
        withContext(NonCancellable) {
            database.withTransaction {
                dao.createSettings(CatalogSettings())
                if (legacy != null) {
                    val existing = dao.findByFileName(legacy.fileName)
                    if (existing == null) dao.insert(legacy)
                    dao.select(existing?.id ?: legacy.id)
                }
                dao.completeLegacyMigration()
            }
        }
    }

    private fun metadata(id: String, filename: String, displayName: String, size: Long,
        importedAt: Long, track: Track): TrackEntity = TrackEntity(
        id, filename, displayName, track.name, importedAt, size,
        track.segments.sumOf { it.points.size }, track.segments.size,
        TrackProgressCalculator(track).totalDistanceMeters,
    )

    private fun checkedFile(filename: String): File {
        if (File(filename).name != filename || !filename.endsWith(".gpx", ignoreCase = true)) {
            throw IOException("Riferimento al file GPX non valido.")
        }
        return File(directory, filename)
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Impossibile creare la cartella delle tracce.")
    }
}
