@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.cockpit

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class CockpitViewModelTest : NavigationTestSupport() {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Cockpit did not update",condition())
    }
    private fun model(store: ViewModelStore): CockpitViewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CockpitViewModel(app) as T
    })[CockpitViewModel::class.java]

    @Test fun cockpitHasATickingClockWithoutGpsAndVisibilityDoesNotRestartTheTrip() {
        controller = newController(RecordingRepository(database))
        runBlocking { controller.attachService();controller.startSession(null,true,mode = ActivityMode.COCKPIT) }
        val id = controller.recording.value.id
        val models = ViewModelStore()
        val cockpit = model(models)
        try {
            await { cockpit.state.value.elapsedMillis != null }
            val elapsed = cockpit.state.value.elapsedMillis!!
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertTrue(cockpit.state.value.elapsedMillis!! >= elapsed + 3_000)
            assertNull(cockpit.state.value.location.point)
            assertEquals(0,cockpit.state.value.recording.pointCount)
            cockpit.setVisible(true)
            cockpit.setVisible(false)
            assertTrue(provider.running)
            assertEquals(1,provider.starts)
            assertEquals(id,controller.recording.value.id)
            assertEquals(ActivityMode.COCKPIT,controller.session.value.mode)
            assertNull(controller.session.value.trackId)
            runBlocking { controller.stopSession() }
            await { cockpit.state.value.elapsedMillis == null }
            assertTrue(cockpit.state.value.recording.pending)
        } finally { models.clear() }
    }

    @Test fun liveValuesAreRestoredFromTheExistingDraftAndRetainedWithoutGps() {
        val recordings = RecordingRepository(database)
        controller = newController(recordings)
        runBlocking { controller.attachService();controller.startSession(null,true,mode = ActivityMode.COCKPIT) }
        now += 2_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.0,11.0,4f,20f,null,200.0,now * 1_000_000),true)
        await { controller.recording.value.pointCount == 1 }
        val before = controller.recording.value
        controller.detachService()
        controller = newController(recordings)
        val models = ViewModelStore()
        val cockpit = model(models)
        try {
            await { cockpit.state.value.session.status == SessionStatus.INTERRUPTED }
            assertEquals(before.id,cockpit.state.value.recording.id)
            assertEquals(before.statistics,cockpit.state.value.recording.statistics)
            runBlocking { controller.attachService();controller.startSession(null,true,mode = ActivityMode.COCKPIT) }
            await { cockpit.state.value.recording.active }
            provider.state.value = LocationState(LocationStatus.UNAVAILABLE)
            await { cockpit.state.value.location.status == LocationStatus.UNAVAILABLE }
            assertEquals(before.statistics,cockpit.state.value.recording.statistics)
            assertNull(cockpit.state.value.location.point)
            assertEquals(before.id,cockpit.state.value.recording.id)
        } finally { models.clear() }
    }
}
