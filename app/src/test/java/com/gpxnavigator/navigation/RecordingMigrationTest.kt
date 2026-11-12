@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.gpx.LocalGpxRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RecordingMigrationTest : NavigationTestSupport() {
    @Test fun versionTwoMigrationPreservesCatalogDraftsPointsAndCounter(): Unit = runBlocking {
        for (version in listOf(2, 3, 4, 5)) for (pending in listOf(0, 1)) {
            val path = File(temporary.root, "old-v$version-$pending.db")
            val schema = JSONObject(File("schemas/com.gpxnavigator.data.database.NavigatorDatabase/$version.json").readText())
                .getJSONObject("database")
            withContext(Dispatchers.IO) {
                SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                    val entities = schema.getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        entity.optJSONArray("indices")?.let { indices ->
                            for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql")
                                .replace("\${TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                    old.execSQL("INSERT INTO tracks (id, fileName, displayName, trackName, importedAtMillis, sizeBytes, pointCount, segmentCount, totalDistanceMeters, problem) VALUES ('old', 'old.gpx', 'Originale.gpx', 'Originale', 1, 100, 3, 1, 50.0, NULL)")
                    old.execSQL("INSERT INTO catalog_settings VALUES (0, 'old', 1)")
                    old.execSQL("INSERT INTO recordings (id, startedAtMillis, sourceTrackId, title, pending) VALUES ('draft', 1700000000000, 'old', 'Giro aperto', $pending)")
                    old.execSQL("INSERT INTO recording_points (recordingId, sequence, segment, latitude, longitude, elevation, timestampMillis, elapsedMillis, accuracyMeters) VALUES ('draft', 0, 0, 43.0, 11.0, 120.0, 1700000001000, 1000, 5.0)")
                    if (version >= 3) {
                        old.execSQL("UPDATE recording_points SET speedMetersPerSecond = 12.5")
                        if (pending == 1) old.execSQL("UPDATE recordings SET endedAtMillis = 1700000005000")
                    }
                    old.execSQL("INSERT INTO recording_counter VALUES (0, 7)")
                    if (version == 5) {
                        old.execSQL("UPDATE tracks SET activityMode = 'COCKPIT'")
                        old.execSQL("UPDATE recordings SET activityMode = 'NAVIGATION', trackName = 'Nome bozza'")
                    }
                    old.version = version
                }
            }
            val migrated = Room.databaseBuilder(app, NavigatorDatabase::class.java, path.absolutePath).build()
            try {
                val catalog = LocalGpxRepository(app, migrated, temporary.newFolder("v$version-gpx-$pending")).observeCatalog().first()
                assertEquals("old", catalog.selectedTrackId)
                assertEquals("Originale", catalog.tracks.single().title)
                val session = migrated.recordings().current()!!
                assertEquals(pending == 1, session.pending)
                assertEquals(if (version >= 3 && pending == 1) 1_700_000_005_000L else null, session.endedAtMillis)
                assertEquals(if (version == 5) "Nome bozza" else null, session.trackName)
                assertEquals(if (version == 5) "NAVIGATION" else null, session.activityMode)
                assertEquals(if (version == 5) com.gpxnavigator.domain.model.ActivityMode.COCKPIT else null, catalog.tracks.single().activityMode)
                assertFalse(catalog.tracks.single().isFavorite)
                assertNull(catalog.tracks.single().customName)
                assertEquals(com.gpxnavigator.domain.model.TrackSortOrder.RECENT, catalog.sortOrder)
                val point = migrated.recordings().points(session.id).single()
                assertEquals(120.0, point.elevation!!, 0.0)
                assertTrue(point.isTrackPoint)
                assertEquals(if (version >= 3) 12.5f else null, point.speedMetersPerSecond)
                assertEquals(7L, migrated.recordings().counter())
            } finally { migrated.close() }
        }
    }
}
