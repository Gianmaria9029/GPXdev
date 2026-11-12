@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.navigation

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gpxnavigator.data.navigation.RecordingRepository
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class RecordingTimerViewModelTest : NavigationTestSupport() {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Recording timer did not update", condition())
    }

    @Test fun timerTicksWithoutGpsAndStopsWhenRecordingEnds() {
        app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
        controller = newController(RecordingRepository(database))
        runBlocking { controller.attachService(); controller.startSession(null, true) }
        val models = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NavigationViewModel(app) as T
        }
        val model = ViewModelProvider(models, factory)[NavigationViewModel::class.java]
        try {
            await { model.state.value.recordingElapsedMillis != null }
            val first = model.state.value.recordingElapsedMillis!!
            assertEquals(0, model.state.value.recording.pointCount)
            assertNull(model.state.value.location.point)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertTrue(model.state.value.recordingElapsedMillis!! >= first + 3_000)
            assertEquals(0, model.state.value.recording.pointCount)
            runBlocking { controller.stopSession() }
            await { model.state.value.recordingElapsedMillis == null }
            assertFalse(model.state.value.isFreeRecording)
        } finally { models.clear() }
    }
}
