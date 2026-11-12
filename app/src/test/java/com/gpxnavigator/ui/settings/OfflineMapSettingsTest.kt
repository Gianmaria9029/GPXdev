@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.navigation.NavigationViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class OfflineMapSettingsTest : NavigationTestSupport() {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Map settings operation did not complete", condition())
    }

    @Test fun settingsImportReplacesTheMapAndRefreshesTheExistingNavigationModelWithoutStoppingTheTrip() {
        app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
        val maps = app.offlineMapRepository
        val first = runBlocking { maps.importMap(fixture("firenze.pmtiles")) }
        runBlocking { controller.attachService(); controller.startSession(trackId) }
        val models = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                (if (modelClass == NavigationViewModel::class.java) NavigationViewModel(app) else OfflineMapSettingsViewModel(app)) as T
        }
        val provider = ViewModelProvider(models, factory)
        val navigation = provider[NavigationViewModel::class.java]
        val settings = provider[OfflineMapSettingsViewModel::class.java]
        var renderer: java.io.Closeable? = null
        try {
            await { !navigation.state.value.loading && navigation.state.value.map?.file == first.file }
            renderer = maps.retainMap(first)
            settings.reload()
            await { !settings.state.value.busy }
            assertEquals(first.name, settings.state.value.map!!.name)
            assertEquals(first.file.length(), settings.state.value.map!!.sizeBytes)
            settings.importMap(fixture("firenze.pmtiles"))
            await { !settings.state.value.busy && settings.state.value.imported }
            val second = runBlocking { maps.loadSelected()!! }
            assertNotEquals(first.file, second.file)
            assertTrue(first.file.isFile)
            navigation.loadMap()
            navigation.prepareMap()
            await { !navigation.state.value.loading && navigation.state.value.map?.file == second.file }
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertEquals(trackId, controller.session.value.trackId)
            assertTrue(first.file.isFile)
            renderer.close(); renderer = null
            runBlocking { maps.cleanupUnusedMaps() }
            assertFalse(first.file.exists())
            assertTrue(second.file.isFile)
        } finally {
            models.clear()
            renderer?.close()
        }
    }

    @Test fun invalidReplacementKeepsTheSelectedMapAndReportsAnError() {
        app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
        val maps = app.offlineMapRepository
        val first = runBlocking { maps.importMap(fixture("firenze.pmtiles")) }
        val models = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OfflineMapSettingsViewModel(app) as T
        }
        val settings = ViewModelProvider(models, factory)[OfflineMapSettingsViewModel::class.java]
        try {
            settings.reload(); await { !settings.state.value.busy }
            val invalid = temporary.newFile("invalid.pmtiles").apply { writeText("Invalid PMTiles") }
            settings.importMap(Uri.fromFile(invalid))
            await { !settings.state.value.busy && settings.state.value.error != null }
            assertFalse(settings.state.value.imported)
            assertEquals(first.name, settings.state.value.map!!.name)
            assertEquals(first.file, runBlocking { maps.loadSelected()!!.file })
            assertTrue(first.file.isFile)
        } finally { models.clear() }
    }
}
