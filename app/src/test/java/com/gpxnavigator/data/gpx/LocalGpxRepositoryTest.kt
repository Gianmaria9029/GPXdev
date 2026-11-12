@file:Suppress("DEPRECATION")

package com.gpxnavigator.data.gpx

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.gpxnavigator.data.database.NavigatorDatabase
import java.io.File
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
// Robolectric 4.17's native SQLite loader fails on this Windows host; sqlite4java executes real SQL.
@Suppress("DEPRECATION")
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LocalGpxRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Application
    private lateinit var database: NavigatorDatabase
    private lateinit var directory: File
    private lateinit var repository: LocalGpxRepository

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("imported_gpx", Context.MODE_PRIVATE).edit().clear().commit()
        directory = temporary.newFolder("gpx")
        openDatabase()
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, NavigatorDatabase::class.java,
            File(temporary.root, "catalog.db").absolutePath).build()
        repository = LocalGpxRepository(context, database, directory)
    }

    @After fun tearDown() { database.close() }

    private fun fixture(name: String): Uri {
        val bytes = javaClass.getResourceAsStream("/$name")!!.use { it.readBytes() }
        val uri = Uri.parse("content://gpx-test/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private suspend fun catalog() = withTimeout(10_000) { repository.observeCatalog().first() }

    private suspend fun expectIoError(action: suspend () -> Unit) {
        try {
            action()
            fail("Operazione non valida accettata")
        } catch (_: IOException) {
            // File/catalog errors must reach the UI as controlled I/O errors.
        }
    }

    @Test fun importingTwoTracksKeepsFilesAndCachesMetadata(): Unit = runBlocking {
        val first = repository.import(fixture("short-track.gpx"))
        val second = repository.import(fixture("m10-single-point.gpx"))
        val result = catalog()
        assertEquals(2, result.tracks.size)
        assertTrue(first.imported.file.isFile)
        assertTrue(second.imported.file.isFile)
        val single = result.tracks.single { it.displayName == "m10-single-point.gpx" }
        assertEquals(1, single.pointCount)
        assertEquals(0.0, single.totalDistanceMeters!!, 0.0)
        assertEquals(single.id, result.selectedTrackId)
        assertEquals(second.imported.file, repository.loadSelected()!!.file)
    }

    @Test fun renamedFavoriteSurvivesReopeningWithoutChangingOriginalGpx(): Unit = runBlocking {
        val imported = repository.import(fixture("short-track.gpx")).imported
        val before = catalog().tracks.single()
        val bytes = imported.file.readBytes()
        repository.rename(before.id, "  Giro d’autunno.gpx  ")
        repository.setFavorite(before.id, true)
        database.close()
        openDatabase()
        val after = catalog().tracks.single()
        assertEquals(before.copy(customName = "Giro d’autunno", isFavorite = true), after)
        assertEquals(before.id, catalog().selectedTrackId)
        assertArrayEquals(bytes, imported.file.readBytes())
        assertEquals("Giro d’autunno.gpx", repository.loadForExport(before.id).displayName)
        assertEquals("Giro d’autunno", repository.loadTrack(repository.pinNavigationTrack(before.id)).name)
        val output = temporary.newFile("exported.gpx")
        repository.export(before.id, Uri.fromFile(output))
        assertArrayEquals(bytes, output.readBytes())
        assertEquals(before.trackName, output.inputStream().use { GpxParser().parse(it) }.name)
        repository.releaseNavigationTrack(before.id)
    }

    @Test fun sortPreferenceSurvivesAnEmptyCatalogAndReopening(): Unit = runBlocking {
        assertTrue(catalog().tracks.isEmpty())
        repository.setSortOrder(com.gpxnavigator.domain.model.TrackSortOrder.DISTANCE_DESC)
        database.close()
        openDatabase()
        assertEquals(com.gpxnavigator.domain.model.TrackSortOrder.DISTANCE_DESC, catalog().sortOrder)
        repository.import(fixture("short-track.gpx"))
        assertEquals(com.gpxnavigator.domain.model.TrackSortOrder.DISTANCE_DESC, catalog().sortOrder)
    }

    @Test fun activeTrackCannotBeRenamedButCanBeMarkedFavorite(): Unit = runBlocking {
        val id = repository.import(fixture("short-track.gpx")).imported.id!!
        repository.pinNavigationTrack(id)
        expectIoError { repository.rename(id, "Altro nome") }
        repository.setFavorite(id, true)
        assertTrue(catalog().tracks.single().isFavorite)
        assertNull(catalog().tracks.single().customName)
        val other = repository.import(fixture("m10-single-point.gpx")).imported.id!!
        repository.rename(other, "Altra traccia")
        assertEquals(id, catalog().selectedTrackId)
        repository.releaseNavigationTrack(id)
        repository.rename(id, "Ora consentito")
        assertEquals("Ora consentito", catalog().tracks.single { it.id == id }.title)
    }

    @Test fun invalidNamesAndMissingIdsDoNotChangeTheCatalog(): Unit = runBlocking {
        val id = repository.import(fixture("short-track.gpx")).imported.id!!
        val before = catalog()
        for (name in listOf(" ", "a".repeat(81), "../percorso", "a\nb"))
            expectIoError { repository.rename(id, name) }
        expectIoError { repository.rename("missing", "Nuovo nome") }
        expectIoError { repository.setFavorite("missing", true) }
        assertEquals(before, catalog())
    }

    @Test fun selectionAndCatalogSurviveDatabaseReopening(): Unit = runBlocking {
        repository.import(fixture("short-track.gpx"))
        val firstId = catalog().selectedTrackId!!
        repository.import(fixture("m10-single-point.gpx"))
        repository.select(firstId)
        database.close()
        openDatabase()
        assertEquals(2, catalog().tracks.size)
        assertEquals(firstId, catalog().selectedTrackId)
        assertEquals("short-track.gpx", repository.loadSelected()!!.displayName)
    }

    @Test fun deletingUnselectedTrackKeepsTheActiveTrack(): Unit = runBlocking {
        val first = repository.import(fixture("short-track.gpx"))
        val firstId = catalog().selectedTrackId!!
        val second = repository.import(fixture("m10-single-point.gpx"))
        val secondId = catalog().selectedTrackId!!
        repository.delete(firstId)
        assertEquals(1, catalog().tracks.size)
        assertEquals(secondId, catalog().selectedTrackId)
        assertFalse(first.imported.file.exists())
        assertTrue(second.imported.file.exists())
    }

    @Test fun deletingActiveTrackClearsSelectionButKeepsOthers(): Unit = runBlocking {
        val first = repository.import(fixture("short-track.gpx"))
        val second = repository.import(fixture("m10-single-point.gpx"))
        repository.delete(catalog().selectedTrackId!!)
        assertEquals(1, catalog().tracks.size)
        assertNull(catalog().selectedTrackId)
        assertNull(repository.loadSelected())
        assertTrue(first.imported.file.exists())
        assertFalse(second.imported.file.exists())
    }

    @Test fun invalidImportPreservesCatalogSelectionAndFiles(): Unit = runBlocking {
        val loaded = repository.import(fixture("short-track.gpx"))
        val before = catalog()
        expectIoError { repository.import(fixture("m10-invalid.gpx")) }
        expectIoError { repository.import(fixture("m10-empty.gpx")) }
        assertEquals(before, catalog())
        assertTrue(loaded.imported.file.exists())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun legacySelectedTrackIsMigratedExactlyOnce(): Unit = runBlocking {
        val legacyFile = File(directory, "legacy.gpx")
        javaClass.getResourceAsStream("/short-track.gpx")!!.use { input ->
            legacyFile.outputStream().use { input.copyTo(it) }
        }
        context.getSharedPreferences("imported_gpx", Context.MODE_PRIVATE).edit()
            .putString("file", legacyFile.name).putString("name", "Vecchia traccia.gpx").commit()
        val migrated = catalog()
        assertEquals(1, migrated.tracks.size)
        assertEquals("Vecchia traccia.gpx", migrated.tracks.single().displayName)
        assertEquals(3, migrated.tracks.single().pointCount)
        assertEquals(migrated.tracks.single().id, migrated.selectedTrackId)
        repository = LocalGpxRepository(context, database, directory)
        assertEquals(migrated, catalog())
        assertTrue(legacyFile.exists())
    }

    @Test fun missingLegacyFileCanBeRemovedWithoutResurrection(): Unit = runBlocking {
        context.getSharedPreferences("imported_gpx", Context.MODE_PRIVATE).edit()
            .putString("file", "missing.gpx").putString("name", "Mancante.gpx").commit()
        val entry = catalog().tracks.single()
        assertNotNull(entry.problem)
        assertNull(entry.totalDistanceMeters)
        expectIoError { repository.loadSelected() }
        repository.delete(entry.id)
        repository = LocalGpxRepository(context, database, directory)
        assertTrue(catalog().tracks.isEmpty())
        assertNull(repository.loadSelected())
    }

    @Test fun interruptedDeletionRestoresAFileStillInTheCatalog(): Unit = runBlocking {
        val loaded = repository.import(fixture("short-track.gpx"))
        val id = catalog().selectedTrackId
        val trash = File(directory, "${loaded.imported.file.name}.trash")
        assertTrue(loaded.imported.file.renameTo(trash))
        repository = LocalGpxRepository(context, database, directory)
        assertEquals(id, catalog().selectedTrackId)
        assertTrue(loaded.imported.file.exists())
        assertFalse(trash.exists())
        assertNotNull(repository.loadSelected())
    }

    @Test fun committedDeletionCleansUpItsRemainingTrashFile(): Unit = runBlocking {
        val loaded = repository.import(fixture("short-track.gpx"))
        val id = catalog().selectedTrackId!!
        val trash = File(directory, "${loaded.imported.file.name}.trash")
        assertTrue(loaded.imported.file.renameTo(trash))
        database.tracks().delete(id)
        repository = LocalGpxRepository(context, database, directory)
        assertTrue(catalog().tracks.isEmpty())
        assertNull(catalog().selectedTrackId)
        assertFalse(trash.exists())
    }

    @Test fun selectingAMissingFileDoesNotReplaceCurrentSelection(): Unit = runBlocking {
        repository.import(fixture("short-track.gpx"))
        val firstId = catalog().selectedTrackId!!
        val second = repository.import(fixture("m10-single-point.gpx"))
        val secondId = catalog().selectedTrackId!!
        repository.select(firstId)
        assertTrue(second.imported.file.delete())
        expectIoError { repository.select(secondId) }
        assertEquals(firstId, catalog().selectedTrackId)
        repository.delete(secondId)
        assertEquals(1, catalog().tracks.size)
    }

    @Test fun identicalOriginalNamesDoNotOverwriteExistingFiles(): Unit = runBlocking {
        val uri = fixture("short-track.gpx")
        val first = repository.import(uri)
        val second = repository.import(uri)
        assertEquals(2, catalog().tracks.size)
        assertNotEquals(first.imported.file, second.imported.file)
        assertTrue(first.imported.file.exists())
        assertTrue(second.imported.file.exists())
    }

    @Test fun concurrentImportsAreSerializedWithoutLosingEntries(): Unit = runBlocking {
        val uri = fixture("short-track.gpx")
        val first = async { repository.import(uri) }
        val second = async { repository.import(uri) }
        first.await()
        second.await()
        assertEquals(2, catalog().tracks.size)
        assertNotNull(catalog().selectedTrackId)
    }

    @Test fun databaseFailureDuringImportRollsBackTheNewFile(): Unit = runBlocking {
        repository.import(fixture("short-track.gpx"))
        val before = catalog()
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER reject_import BEFORE INSERT ON tracks
                BEGIN SELECT RAISE(ABORT, 'Test import failure'); END
            """)
        }
        expectIoError { repository.import(fixture("m10-single-point.gpx")) }
        assertEquals(before, catalog())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun databaseFailureDuringDeletionRestoresTheFile(): Unit = runBlocking {
        val loaded = repository.import(fixture("short-track.gpx"))
        val before = catalog()
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER reject_deletion BEFORE DELETE ON tracks
                BEGIN SELECT RAISE(ABORT, 'Test deletion failure'); END
            """)
        }
        expectIoError { repository.delete(before.selectedTrackId!!) }
        assertEquals(before, catalog())
        assertTrue(loaded.imported.file.exists())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun liveCatalogEmitsWhenOnlyTheSelectionChanges(): Unit = runBlocking {
        repository.import(fixture("short-track.gpx"))
        val firstId = catalog().selectedTrackId!!
        repository.import(fixture("m10-single-point.gpx"))
        val changes = Channel<com.gpxnavigator.domain.model.GpxCatalog>(Channel.UNLIMITED)
        val observer = launch { repository.observeCatalog().collect { changes.send(it) } }
        try {
            assertNotEquals(firstId, withTimeout(10_000) { changes.receive() }.selectedTrackId)
            repository.select(firstId)
            val selected = withTimeout(10_000) {
                var snapshot = changes.receive()
                while (snapshot.selectedTrackId != firstId) snapshot = changes.receive()
                snapshot
            }
            assertEquals(firstId, selected.selectedTrackId)
        } finally {
            observer.cancel()
            changes.close()
        }
    }

    @Test fun activeNavigationProtectsSelectionAndFileButAllowsImports(): Unit = runBlocking {
        val active = repository.import(fixture("short-track.gpx")).imported
        repository.pinNavigationTrack(active.id!!)
        val other = repository.import(fixture("m10-single-point.gpx")).imported
        assertEquals(active.id, catalog().selectedTrackId)
        expectIoError { repository.select(other.id!!) }
        expectIoError { repository.delete(active.id) }
        assertTrue(active.file.isFile)
        repository.delete(other.id!!)
        assertEquals(active.id, catalog().selectedTrackId)
        repository.releaseNavigationTrack(active.id)
        repository.delete(active.id)
        assertTrue(catalog().tracks.isEmpty())
    }

    @Test fun releasingAnOlderPinDoesNotUnpinTheCurrentTrack(): Unit = runBlocking {
        val first = repository.import(fixture("short-track.gpx")).imported
        val second = repository.import(fixture("m10-single-point.gpx")).imported
        repository.pinNavigationTrack(first.id!!)
        expectIoError { repository.pinNavigationTrack(second.id!!) }
        repository.releaseNavigationTrack(first.id)
        repository.pinNavigationTrack(second.id!!)
        repository.releaseNavigationTrack(first.id)
        expectIoError { repository.delete(second.id) }
        repository.releaseNavigationTrack(second.id)
        repository.select(first.id)
        repository.delete(second.id)
    }

    @Test fun rejectedImportLeavesCatalogAndFilesUnchanged(): Unit = runBlocking {
        val original = repository.import(fixture("short-track.gpx")).imported
        val uri = Uri.parse("content://gpx-test/oversized-name.gpx")
        val xml = "<gpx><trk><name>${"x".repeat(513)}</name><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>"
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { xml.byteInputStream() }
        expectIoError { repository.import(uri) }
        assertEquals(listOf(original.id), catalog().tracks.map { it.id })
        assertEquals(listOf(original.file.name), directory.listFiles()!!.map { it.name })
    }

    @Test fun excessiveCopyIsStoppedWithoutTrustingProviderSize(): Unit = runBlocking {
        val uri = Uri.parse("content://gpx-test/endless.gpx")
        var read = 0L
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            object : java.io.InputStream() {
                override fun read(): Int { read++; return 32 }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    buffer.fill(32.toByte(), offset, offset + length)
                    read += length
                    return length
                }
            }
        }
        expectIoError { repository.import(uri) }
        assertTrue(read <= GpxLimits().maxBytes + 1)
        assertTrue(catalog().tracks.isEmpty())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun exportSurvivesRepositoryRecreationAndPreservesExactBytes(): Unit = runBlocking {
        val track = repository.import(fixture("short-track.gpx")).imported
        database.close()
        openDatabase()
        val before = catalog()
        val target = temporary.newFile("export.gpx")
        repository.export(track.id!!, Uri.fromFile(target))
        org.junit.Assert.assertArrayEquals(track.file.readBytes(), target.readBytes())
        assertEquals(before, catalog())
    }
}
