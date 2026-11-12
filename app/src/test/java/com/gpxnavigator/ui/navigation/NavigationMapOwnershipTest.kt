@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.navigation

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
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
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationMapOwnershipTest : NavigationTestSupport() {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Map ownership handoff timed out", condition())
    }

    @Test fun viewModelHandoffWaitsForRendererReleaseAndKeepsTheSelectedMap(): Unit = runBlocking {
        app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
        val maps = app.offlineMapRepository
        val uri = fixture("firenze.pmtiles")
        val first = maps.importMap(uri)
        val models = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NavigationViewModel(app) as T
        }
        val model = ViewModelProvider(models, factory)[NavigationViewModel::class.java]
        var renderer: java.io.Closeable? = null
        try {
            await { !model.state.value.loading && model.state.value.map?.file == first.file }
            val second = maps.importMap(uri)
            assertTrue(first.file.isFile)
            renderer = maps.retainMap(first)
            model.loadMap()
            await { !model.state.value.loading && model.state.value.map?.file == second.file }
            maps.cleanupUnusedMaps()
            assertTrue(first.file.isFile)
            models.clear()
            renderer.close()
            maps.cleanupUnusedMaps()
            assertFalse(first.file.exists())
            assertEquals(second.file, maps.loadSelected()!!.file)
        } finally {
            models.clear()
            renderer?.close()
            maps.cleanupUnusedMaps()
        }
    }
}
