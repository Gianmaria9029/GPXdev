@file:Suppress("DEPRECATION")

package com.gpxnavigator.qa

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.database.RecordingEntity
import com.gpxnavigator.data.database.RecordingPointEntity
import com.gpxnavigator.data.gpx.GpxLimits
import com.gpxnavigator.data.gpx.GpxParseException
import com.gpxnavigator.data.gpx.LocalGpxRepository
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class QaDataRecordingLimitsTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: NavigatorDatabase
    private lateinit var repository: LocalGpxRepository

    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("imported_gpx", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.databaseBuilder(context, NavigatorDatabase::class.java,
            File(temporary.root, "qa-recording.db").absolutePath).build()
        repository = LocalGpxRepository(context, database, temporary.newFolder("gpx"))
    }

    @After fun close() { database.close() }

    private suspend fun pendingRecording(segments: Int) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.recordings()
            dao.insert(RecordingEntity("qa-recording", 1_700_000_000_000, null, "Synthetic QA",
                pending = true, endedAtMillis = 1_700_100_000_000))
            repeat(segments) { index ->
                dao.insert(RecordingPointEntity("qa-recording", index, index, 43.0, 11.0,
                    null, 1_700_000_000_000 + index * 20_000L, index * 20_000L, 5f))
            }
        }
    }

    @Test fun savedRecordingAtImportSegmentLimitRemainsLoadable(): Unit = runBlocking {
        val count = GpxLimits().maxSegments
        pendingRecording(count)
        val saved = repository.saveRecording("qa-recording")
        assertTrue(saved.file.isFile)
        assertNull(database.recordings().current())
        assertEquals(count, repository.loadTrack(saved).segments.size)
    }

    @Test fun savedRecordingOneSegmentBeyondImportLimitMustRemainLoadable(): Unit = runBlocking {
        val count = GpxLimits().maxSegments + 1
        pendingRecording(count)
        val saved = repository.saveRecording("qa-recording")
        assertTrue("The app has reported a successful save", saved.file.isFile)
        assertNull("The original draft is removed on successful save", database.recordings().current())
        val catalog = repository.observeCatalog().first()
        assertEquals(count, catalog.tracks.single().segmentCount)
        assertEquals(saved.id, catalog.selectedTrackId)
        // A successfully saved in-app recording must be readable by the navigation path.
        assertEquals(count, repository.loadTrack(repository.loadSelected()!!).segments.size)
        database.close()
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), NavigatorDatabase::class.java,
            File(temporary.root, "qa-recording.db").absolutePath).build()
        repository = LocalGpxRepository(RuntimeEnvironment.getApplication(), database, saved.file.parentFile!!)
        assertEquals(count, repository.loadTrack(repository.loadSelected()!!).segments.size)
        assertEquals(count, repository.loadTrack(repository.pinNavigationTrack(saved.id!!)).segments.size)
    }

    private suspend fun pendingLongRecording(count: Int, telemetry: Boolean = false) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.recordings().insert(RecordingEntity("qa-long-recording", 1_700_000_000_000,
                    null, "Long synthetic QA", pending = true, endedAtMillis = 1_700_300_000_000))
                // Bulk SQL keeps this long-recording fixture cheap; all data stays in the temporary DB.
                database.openHelper.writableDatabase.execSQL("""
                    WITH RECURSIVE qa_sequence(value) AS (
                        VALUES(0) UNION ALL SELECT value + 1 FROM qa_sequence WHERE value < ${count - 1}
                    )
                    INSERT INTO recording_points(recordingId, sequence, segment, latitude, longitude,
                        elevation, timestampMillis, elapsedMillis, accuracyMeters, speedMetersPerSecond, isTrackPoint)
                    SELECT 'qa-long-recording', value, 0, 43.0,
                        11.0 + CASE WHEN value % 2000 <= 1000 THEN value % 2000
                            ELSE 2000 - value % 2000 END * 0.0001, ${if (telemetry) "100.1234" else "NULL"},
                        1700000000000 + value * 2000, value * 2000, 5.0,
                        ${if (telemetry) "5.0" else "NULL"}, 1 FROM qa_sequence
                """.trimIndent())
            }
        }
    }

    @Test fun savedRecordingOnePointBeyondImportLimitMustRemainLoadable(): Unit = runBlocking {
        val count = GpxLimits().maxPoints + 1
        pendingLongRecording(count)
        val saved = repository.saveRecording("qa-long-recording")
        assertTrue(saved.file.isFile)
        assertNull(database.recordings().current())
        assertEquals(count, repository.observeCatalog().first().tracks.single().pointCount)
        assertEquals(count, repository.loadTrack(saved).segments.single().points.size)
    }

    @Test fun savedRecordingBeyondImportByteLimitRemainsLoadable(): Unit = runBlocking {
        val count = 130_001
        pendingLongRecording(count, telemetry = true)
        val saved = repository.saveRecording("qa-long-recording")
        assertTrue("The fixture must exercise the byte budget as well as the point count",
            saved.file.length() > GpxLimits().maxBytes)
        assertNull(database.recordings().current())
        assertEquals(count, repository.loadTrack(repository.loadSelected()!!).segments.single().points.size)
    }

    @Test fun externalCopyCannotBorrowCatalogLimitsAndImportRemainsBounded(): Unit = runBlocking {
        pendingRecording(GpxLimits().maxSegments + 1)
        val saved = repository.saveRecording("qa-recording")
        val external = temporary.newFile("external.gpx")
        saved.file.copyTo(external, overwrite = true)
        try {
            repository.loadTrack(saved.copy(file = external, sizeBytes = Long.MAX_VALUE))
            fail("An external file must not borrow the saved recording's larger parser budgets")
        } catch (_: GpxParseException) { }
        try {
            repository.import(Uri.fromFile(external))
            fail("External imports must still enforce the segment budget")
        } catch (_: GpxParseException) { }
        assertEquals(listOf(saved.id), repository.observeCatalog().first().tracks.map { it.id })
        assertEquals(GpxLimits().maxSegments + 1, repository.loadTrack(saved).segments.size)
    }

    @Test fun changedPrivateFileCannotExceedItsValidatedSegmentCount(): Unit = runBlocking {
        pendingRecording(GpxLimits().maxSegments + 1)
        val saved = repository.saveRecording("qa-recording")
        saved.file.writeText(saved.file.readText().replace("</trk>",
            "<trkseg><trkpt lat=\"43\" lon=\"11\"/></trkseg></trk>"))
        try {
            repository.loadTrack(saved)
            fail("Reloading must retain a segment budget even for catalog files")
        } catch (_: GpxParseException) { }
    }
}
