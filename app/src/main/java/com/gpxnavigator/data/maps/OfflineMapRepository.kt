package com.gpxnavigator.data.maps

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class OfflineMap(
    val file: File,
    val name: String,
    val header: PmTilesHeader,
    val vectorLayers: Set<String>,
    val attribution: String,
)

class OfflineMapLease internal constructor(val map: OfflineMap, private val reference: Closeable) : Closeable {
    override fun close() = reference.close()
}

class OfflineMapRepository(context: Context,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val deleteFile: (File) -> Boolean = { it.delete() },
) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "maps")
    private val preferences = appContext.getSharedPreferences("offline_map", Context.MODE_PRIVATE)
    private val operations = Mutex()
    private val usageLock = Any()
    private val readers = mutableMapOf<String, Int>()

    suspend fun loadSelected(): OfflineMap? = withContext(Dispatchers.IO) {
        operations.withLock { loadSelectedLocked() }
    }

    // Transfer ownership before unlocking, including when another screen imports a new map concurrently.
    suspend fun acquireMap(uri: Uri? = null): OfflineMapLease? {
        var acquired: OfflineMapLease? = null
        try {
            return withContext(Dispatchers.IO) {
                operations.withLock {
                    val map = if (uri == null) loadSelectedLocked() else importMapLocked(uri)
                    map?.let { OfflineMapLease(it, retainMap(it)).also { lease -> acquired = lease } }
                }
            }
        } catch (exception: Exception) {
            // withContext can be cancelled after acquisition, before delivering the map to the caller.
            acquired?.close()
            throw exception
        }
    }

    private suspend fun loadSelectedLocked(): OfflineMap? {
        val filename = preferences.getString("file", null)
        val coroutineContext = currentCoroutineContext()
        val selected = filename?.let {
            inspect(File(directory, it), preferences.getString("name", "Mappa offline")!!) { coroutineContext.ensureActive() }
        }
        cleanupUnusedMapsLocked()
        return selected
    }

    fun retainMap(map: OfflineMap): Closeable {
        require(map.file.parentFile == directory) { "La mappa deve appartenere all'archivio privato." }
        val filename = map.file.name
        synchronized(usageLock) { readers[filename] = (readers[filename] ?: 0) + 1 }
        val closed = AtomicBoolean()
        return Closeable {
            if (closed.compareAndSet(false, true)) {
                synchronized(usageLock) {
                    val count = readers.getValue(filename)
                    if (count == 1) readers.remove(filename) else readers[filename] = count - 1
                }
                cleanupScope.launch { cleanupUnusedMaps() }
            }
        }
    }

    suspend fun cleanupUnusedMaps(): Boolean = withContext(Dispatchers.IO) {
        operations.withLock { cleanupUnusedMapsLocked() }
    }

    // Cleanup never invalidates an already committed map; failed deletions are retried on load/release.
    @SuppressLint("LogNotTimber")
    private fun cleanupUnusedMapsLocked(): Boolean {
        try {
            if (!directory.exists()) return true
            val active = preferences.getString("file", null)
            if (active != null && !File(directory, active).isFile) return false
            val files = directory.listFiles() ?: throw IOException("Impossibile leggere le copie delle mappe.")
            var complete = true
            for (file in files) {
                if (file.name == active || !file.isFile || !file.name.endsWith(".pmtiles", ignoreCase = true)) continue
                synchronized(usageLock) {
                    if (readers[file.name] == null && !deleteFile(file)) {
                        complete = false
                        android.util.Log.w("OfflineMapRepository", "Pulizia della vecchia mappa da riprovare alla prossima apertura.")
                    }
                }
            }
            return complete
        } catch (exception: IOException) {
            android.util.Log.w("OfflineMapRepository", "Pulizia delle mappe da riprovare.", exception)
        } catch (exception: SecurityException) {
            android.util.Log.w("OfflineMapRepository", "Pulizia delle mappe non consentita; nuovo tentativo alla prossima apertura.", exception)
        }
        return false
    }

    suspend fun importMap(uri: Uri): OfflineMap = withContext(Dispatchers.IO) {
        operations.withLock { importMapLocked(uri) }
    }

    // Check commit's Boolean result; use platform logging for exceptional cleanup failures.
    @SuppressLint("UseKtx", "LogNotTimber")
    private suspend fun importMapLocked(uri: Uri): OfflineMap {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Impossibile creare la cartella delle mappe.")
        val name = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Mappa offline"
        val target = File(directory, "${UUID.randomUUID()}.pmtiles")
        var saved = false
        return try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Il file selezionato non è leggibile.")
            input.use { source ->
                target.outputStream().use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            val coroutineContext = currentCoroutineContext()
            val map = inspect(target, name) { coroutineContext.ensureActive() }
            currentCoroutineContext().ensureActive()
            if (!preferences.edit().putString("file", target.name).putString("name", name).commit()) {
                throw IOException("Impossibile salvare la scelta della mappa.")
            }
            saved = true
            cleanupUnusedMapsLocked()
            map
        } finally {
            if (!saved && target.exists() && !target.delete()) {
                android.util.Log.w("OfflineMapRepository", "Copia incompleta da rimuovere al prossimo import.")
            }
        }
    }

    private fun inspect(file: File, name: String, checkActive: () -> Unit): OfflineMap {
        if (!file.isFile) throw FileNotFoundException("Mappa mancante. Seleziona nuovamente un file PMTiles.")
        return RandomAccessFile(file, "r").use { source ->
            val header = PmTilesHeader.read(source)
            PmTilesDirectoryValidator.validate(source, header, checkActive)
            source.seek(header.metadataOffset)
            val compressed = ByteArray(header.metadataLength)
            source.readFully(compressed)
            val metadataBytes = if (header.compression == 2 && compressed.isNotEmpty()) {
                GZIPInputStream(compressed.inputStream()).use { input ->
                    val bytes = ByteArray(PmTilesHeader.MAX_METADATA_BYTES + 1)
                    var used = 0
                    while (used < bytes.size) {
                        checkActive()
                        val count = input.read(bytes, used, bytes.size - used)
                        if (count < 0) break
                        used += count
                    }
                    bytes.copyOf(used)
                }
            } else compressed
            if (metadataBytes.size > PmTilesHeader.MAX_METADATA_BYTES) throw InvalidMapException("Metadati della mappa troppo grandi.")
            try {
                val metadata = if (metadataBytes.isEmpty()) JSONObject() else JSONObject(metadataBytes.toString(Charsets.UTF_8))
                val layers = metadata.optJSONArray("vector_layers")
                val layerIds = if (layers == null) emptySet() else (0 until layers.length()).map {
                    layers.getJSONObject(it).getString("id")
                }.toSet()
                if (header.tileType == 1 && !layerIds.containsAll(setOf("earth", "roads", "water")) &&
                    !layerIds.containsAll(setOf("transportation", "water"))
                ) throw InvalidMapException("Questa mappa vettoriale richiede uno stile diverso. Usa Protomaps o OpenMapTiles.")
                OfflineMap(file, name, header, layerIds, metadata.optString("attribution", ""))
            } catch (exception: JSONException) {
                throw InvalidMapException("Metadati PMTiles non validi: ${exception.message}")
            }
        }
    }
}
