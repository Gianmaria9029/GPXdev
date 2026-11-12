@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.gpx.GpxParser
import com.gpxnavigator.data.gpx.LocalGpxRepository
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RecordingIntegrationTest : NavigationTestSupport() {
    private lateinit var recordings: RecordingRepository
    @Before fun enableRecordings() {
        recordings = RecordingRepository(database)
        controller = newController(recordings)
    }

    private suspend fun fix(lat: Double = 43.7696, lon: Double = 11.2558) {
        now += 3_000
        val before = controller.recording.value.pointCount
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(lat, lon, 5f, 2f, null, 50.0, now * 1_000_000), true)
        withTimeout(5_000) { controller.recording.first { it.pointCount > before } }
    }

    @Test fun followsOriginalGpxWhileSavingActualDetourOnlyAfterYes(): Unit = runBlocking {
        val original = repository.loadSelected()!!.file.readBytes()
        controller.attachService()
        controller.startSession(trackId, true)
        fix()
        fix(43.7700, 11.2560)
        fix(43.7706, 11.2568)
        assertTrue(controller.recording.value.active)
        assertEquals(trackId, repository.observeCatalog().first().selectedTrackId)
        assertEquals(1, repository.observeCatalog().first().tracks.size)
        controller.setScreenVisible(false)
        assertTrue(provider.running)
        controller.stopSession()
        assertFalse(provider.running)
        assertTrue(controller.recording.value.pending)
        assertEquals(1, repository.observeCatalog().first().tracks.size)
        val saved = controller.saveRecording()
        assertTrue(saved.displayName.matches(Regex("Giro_\\d{4}-\\d{2}-\\d{2}_001.gpx")))
        val parsed = saved.file.inputStream().use { GpxParser().parse(it) }
        assertEquals(3, parsed.segments.single().points.size)
        assertEquals(43.7706, parsed.segments.single().points.last().latitude, 0.0)
        assertNotNull(parsed.segments.single().points.last().timestamp)
        assertEquals(2, repository.observeCatalog().first().tracks.size)
        assertArrayEquals(original, repository.observeCatalog().first().tracks.first { it.id == trackId }
            .let { repository.select(it.id); repository.loadSelected()!!.file.readBytes() })
        assertNull(recordings.current())
    }

    @Test fun freeRecordingHasNoItineraryAndNoDiscardsWithoutCreatingGpx(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        withTimeout(5_000) { controller.data.first { it.location.status == LocationStatus.ACTIVE } }
        assertNull(controller.data.value.track)
        assertNull(controller.data.value.progress)
        assertNull(controller.data.value.guidance.maneuver)
        controller.stopSession()
        controller.discardRecording()
        assertEquals(1, repository.observeCatalog().first().tracks.size)
        assertNull(recordings.current())
        assertNull(database.recordings().counter())
    }

    @Test fun processRecoveryPreservesPointsAndResumeBeginsANewSegment(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        controller.detachService()
        controller = newController(recordings)
        withTimeout(5_000) { controller.session.first { it.status == SessionStatus.INTERRUPTED } }
        assertEquals(1, controller.recording.value.pointCount)
        assertFalse(provider.running)
        controller.attachService()
        controller.startSession(null, true)
        fix(43.771, 11.257)
        controller.stopSession()
        val saved = controller.saveRecording()
        val parsed = saved.file.inputStream().use { GpxParser().parse(it) }
        assertEquals(2, parsed.segments.size)
        assertEquals(0.0, controller.recording.value.distanceMeters, 0.0)
    }

    @Test fun pendingDecisionSurvivesReopeningAndSequenceSurvivesDeletion(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        controller.stopSession()
        controller = newController(recordings)
        withTimeout(5_000) { controller.recording.first { it.pending } }
        val first = controller.saveRecording()
        repository.delete(first.id!!)
        controller.attachService()
        controller.startSession(null, true)
        fix()
        controller.stopSession()
        val second = controller.saveRecording()
        assertTrue(second.displayName.endsWith("_002.gpx"))
    }

    @Test fun failedFinalSaveKeepsDraftAndDoesNotConsumeTheSequence(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        controller.stopSession()
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_recorded_track BEFORE INSERT ON tracks
            BEGIN SELECT RAISE(ABORT, 'Test failure'); END
        """) }
        try { controller.saveRecording(); fail("Save should fail") } catch (_: IOException) { }
        assertTrue(controller.recording.value.pending)
        assertNotNull(recordings.current())
        assertNull(database.recordings().counter())
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_recorded_track") }
        assertTrue(controller.saveRecording().displayName.endsWith("_001.gpx"))
    }

    @Test fun emptyRecordingCanBeDiscardedAndCannotReplaceExistingGpx(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        controller.stopSession()
        try { controller.saveRecording(); fail("Empty GPX accepted") } catch (_: IOException) { }
        assertEquals(1, repository.observeCatalog().first().tracks.size)
        controller.discardRecording()
        assertNull(recordings.current())
    }

    @Test fun stopFlushesTheLastAvailableFixBeforeAskingForSave(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        now += 3_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.7696, 11.2558, 5f, 2f, null, null, now * 1_000_000), true)
        controller.stopSession()
        assertEquals(1, controller.recording.value.pointCount)
        assertTrue(controller.recording.value.pending)
    }

    @Test fun stationaryTelemetryAndStopTimeSurviveRecoveryAndDelayedSave(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        val startedAt = recordings.current()!!.startedAtMillis
        for (index in 0..3) {
            now += 2_000
            provider.state.value = LocationState(LocationStatus.ACTIVE,
                LocationPoint(43.0, 11.0, 5f, if (index == 0) 2f else 0f,
                    null, 100.0, now * 1_000_000), true)
            withTimeout(5_000) {
                while (recordings.points(recordings.current()!!.id).size < index + 1) delay(10)
            }
        }
        assertEquals(1, controller.recording.value.pointCount)
        val id = recordings.current()!!.id
        assertEquals(listOf(2f, 0f, 0f, 0f), recordings.points(id).map { it.speedMetersPerSecond })
        val endedAt = 1_700_000_000_000L + now
        controller.stopSession()
        assertEquals(endedAt, recordings.current()!!.endedAtMillis)
        now += 3_600_000
        controller = newController(recordings)
        withTimeout(5_000) { controller.recording.first { it.pending } }
        val saved = controller.saveRecording()
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(saved.file)
        val ns = com.gpxnavigator.data.gpx.GpxWriter.EXTENSION_NAMESPACE
        assertEquals(java.time.Instant.ofEpochMilli(startedAt).toString(), doc.getElementsByTagNameNS(ns, "startedAt").item(0).textContent)
        assertEquals(java.time.Instant.ofEpochMilli(endedAt).toString(), doc.getElementsByTagNameNS(ns, "endedAt").item(0).textContent)
        assertEquals(1, GpxParser().parse(saved.file.inputStream()).segments.single().points.size)
        assertEquals(0, doc.getElementsByTagNameNS(ns, "movingAverageSpeedMetersPerSecond").length)
    }

    @Test fun resumedRecordingKeepsEarlierMaximumAndDoesNotAverageAcrossTheInterruption(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        fix(43.7700, 11.2560)
        val first = recordings.points(recordings.current()!!.id).first()
        controller.detachService()
        controller = newController(recordings)
        withTimeout(5_000) { controller.session.first { it.status == SessionStatus.INTERRUPTED } }
        controller.attachService()
        controller.startSession(null, true)
        fix(43.7710, 11.2570)
        controller.stopSession()
        val saved = controller.saveRecording()
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(saved.file)
        val ns = com.gpxnavigator.data.gpx.GpxWriter.EXTENSION_NAMESPACE
        val maximum = doc.getElementsByTagNameNS(ns, "maximumSpeed").item(0) as org.w3c.dom.Element
        assertEquals(java.time.Instant.ofEpochMilli(first.timestampMillis).toString(), maximum.getAttribute("time"))
        assertEquals("2.0", doc.getElementsByTagNameNS(ns, "movingAverageSpeedMetersPerSecond").item(0).textContent)
        assertEquals(2, GpxParser().parse(saved.file.inputStream()).segments.size)
    }

    @Test fun repeatingStopDoesNotAppendPreviewLocationsOrChangeTheOriginalEnd(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        controller.stopSession()
        val original = recordings.current()!!
        val points = recordings.points(original.id)
        now += 60_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(44.0, 12.0, 5f, 10f, null, 150.0, now * 1_000_000), true)
        controller.stopSession()
        assertEquals(original.endedAtMillis, recordings.current()!!.endedAtMillis)
        assertEquals(points, recordings.points(original.id))
    }

    @Test fun richGpxExportsUnchangedAndCanBeImportedAndNavigatedAgain(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        fix(43.7700, 11.2560)
        controller.stopSession()
        val saved = controller.saveRecording()
        val exported = temporary.newFile("giro-completo.gpx")
        repository.export(saved.id!!, android.net.Uri.fromFile(exported))
        assertArrayEquals(saved.file.readBytes(), exported.readBytes())
        val uri = android.net.Uri.parse("content://navigation-test/giro-completo.gpx")
        org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { exported.inputStream() }
        val imported = repository.import(uri)
        assertEquals(2, imported.track.segments.single().points.size)
        assertArrayEquals(exported.readBytes(), imported.imported.file.readBytes())
        controller.startSession(imported.imported.id, false)
        withTimeout(5_000) { controller.data.first { it.trackId == imported.imported.id } }
        assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
        assertEquals(2, controller.data.value.track!!.segments.single().points.size)
        assertNull(controller.session.value.recordingId)
    }

    @Test fun chosenNameSurvivesRecoveryAndIsUsedInTheCatalogAndGpx(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true, "  Giro del lago & caffè.GPX  ")
        assertEquals("Giro del lago & caffè", recordings.current()!!.trackName)
        assertEquals("Giro del lago & caffè", controller.session.value.title)
        fix()
        controller.detachService()
        controller = newController(recordings)
        withTimeout(5_000) { controller.session.first { it.status == SessionStatus.INTERRUPTED } }
        assertEquals("Giro del lago & caffè", controller.session.value.title)
        controller.attachService()
        controller.startSession(null, true, "Nome da ignorare nella ripresa")
        assertEquals("Giro del lago & caffè", controller.session.value.title)
        fix(43.7700, 11.2560)
        controller.stopSession()
        controller = newController(recordings)
        withTimeout(5_000) { controller.recording.first { it.pending } }
        val saved = controller.saveRecording()
        assertEquals("Giro del lago & caffè.gpx", saved.displayName)
        val parsed = saved.file.inputStream().use { GpxParser().parse(it) }
        assertEquals("Giro del lago & caffè", parsed.name)
        assertEquals(2, parsed.segments.size)
        assertEquals("Giro del lago & caffè", repository.observeCatalog().first().tracks.first { it.id == saved.id }.title)
    }

    @Test fun navigationRecordingKeepsAutomaticNamesAndDuplicateChosenNamesDoNotOverwriteFiles(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId, true, "Nome riservato alla registrazione libera")
        assertNull(recordings.current()!!.trackName)
        fix()
        controller.stopSession()
        assertTrue(controller.saveRecording().displayName.endsWith("_001.gpx"))
        val savedIds = mutableSetOf<String>()
        repeat(2) {
            controller.startSession(null, true, "Giro del lago")
            fix()
            controller.stopSession()
            val saved = controller.saveRecording()
            assertEquals("Giro del lago.gpx", saved.displayName)
            assertTrue(saved.file.isFile)
            savedIds += saved.id!!
        }
        assertEquals(2, savedIds.size)
        assertEquals(2, repository.observeCatalog().first().tracks.count { it.title == "Giro del lago" })
    }

    @Test fun invalidNameDoesNotCreateADraftOrStartTheGps(): Unit = runBlocking {
        controller.attachService()
        try {
            controller.startSession(null, true, "Giro/lago")
            fail("Invalid name accepted")
        } catch (_: IOException) { }
        assertNull(recordings.current())
        assertFalse(provider.running)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
    }

    @Test fun pointWriteFailureKeepsPreviousPointsAvailableForFinalSave(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix()
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_recorded_point BEFORE INSERT ON recording_points
            BEGIN SELECT RAISE(ABORT, 'Test point failure'); END
        """) }
        now += 3_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.77, 11.256, 5f, 2f, null, null, now * 1_000_000), true)
        withTimeout(5_000) { controller.recording.first { it.error != null } }
        assertFalse(controller.recording.value.active)
        controller.stopSession()
        val saved = controller.saveRecording()
        assertEquals(1, saved.file.inputStream().use { GpxParser().parse(it) }.segments.single().points.size)
    }

    @Test fun migrationFromVersionOnePreservesCatalogAndSelection(): Unit = runBlocking {
        val path = File(temporary.root, "old.db")
        val schemaPath = File("schemas/com.gpxnavigator.data.database.NavigatorDatabase/1.json")
        val schema = JSONObject(schemaPath.readText()).getJSONObject("database")
        withContext(Dispatchers.IO) {
            val old = SQLiteDatabase.openOrCreateDatabase(path, null)
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO tracks VALUES ('old', 'old.gpx', 'Originale.gpx', 'Originale', 1, 100, 3, 1, 50.0, NULL)")
            old.execSQL("INSERT INTO catalog_settings VALUES (0, 'old', 1)")
            old.version = 1
            old.close()
        }
        val migrated = Room.databaseBuilder(app, NavigatorDatabase::class.java, path.absolutePath).build()
        try {
            val catalog = LocalGpxRepository(app, migrated, temporary.newFolder("migrated-gpx")).observeCatalog().first()
            assertEquals("old", catalog.selectedTrackId)
            assertEquals("Originale", catalog.tracks.single().title)
            assertNull(migrated.recordings().current())
        } finally { migrated.close() }
    }
}
