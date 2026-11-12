@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.data.navigation.PreferencesNavigationSessionStore
import com.gpxnavigator.data.navigation.SavedNavigationSession
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CockpitActivityTest : NavigationTestSupport() {
    private suspend fun fix(lon: Double, speed: Float, elevation: Double?) {
        now += 2_000
        val old = controller.recording.value.statistics.maximumSpeedPoint?.sequence ?: -1
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.0,lon,4f,speed,null,elevation,now * 1_000_000),true)
        withTimeout(5_000) { controller.recording.first { (it.statistics.maximumSpeedPoint?.sequence ?: -1) > old } }
    }

    @Test fun cockpitForcesItsOwnRecordingAndCannotTurnIntoAnotherActivity(): Unit = runBlocking {
        controller = newController(RecordingRepository(database))
        controller.attachService()
        controller.startSession(null, mode = ActivityMode.COCKPIT, recordingName = "Cockpit colli")
        val id = controller.recording.value.id
        assertNotNull(id)
        assertEquals(ActivityMode.COCKPIT,controller.session.value.mode)
        assertEquals(ActivityMode.COCKPIT,controller.recording.value.mode)
        assertEquals(ActivityMode.COCKPIT,store.saved!!.mode)
        assertNull(controller.session.value.trackId)
        for (target in listOf(ActivityMode.FREE_RECORDING,ActivityMode.NAVIGATION)) {
            try { controller.startSession(if(target == ActivityMode.NAVIGATION) trackId else null,true,mode = target);fail("Changed activity") }
            catch (_: IOException) { }
            assertEquals(id,controller.recording.value.id)
            assertEquals(ActivityMode.COCKPIT,controller.session.value.mode)
        }
    }

    @Test fun endedCockpitKeepsItsRecordedSummaryAcrossRecoveryAndSaving(): Unit = runBlocking {
        val recordings = RecordingRepository(database)
        controller = newController(recordings)
        controller.attachService()
        controller.startSession(null,true,"Giro dedicato",ActivityMode.COCKPIT)
        fix(11.0,10f,120.0)
        fix(11.0001,20f,200.0)
        controller.detachService()
        controller = newController(recordings)
        withTimeout(5_000) { controller.session.first { it.status == SessionStatus.INTERRUPTED } }
        assertEquals(ActivityMode.COCKPIT,controller.session.value.mode)
        controller.attachService()
        controller.startSession(null,true,mode = ActivityMode.COCKPIT)
        fix(11.001,30f,80.0)
        controller.stopSession()
        val summary = controller.recording.value
        assertTrue(summary.pending)
        assertEquals("Giro dedicato",summary.title)
        assertNotNull(summary.endedAtMillis)
        assertEquals(30f,summary.statistics.maximumSpeedPoint!!.speedMetersPerSecond)
        assertEquals(120.0,summary.statistics.startElevationMeters!!,0.0)
        assertEquals(80.0,summary.statistics.endElevationMeters!!,0.0)
        assertEquals(-40.0,summary.statistics.elevationDifferenceMeters!!,0.0)
        controller = newController(recordings)
        withTimeout(5_000) { controller.recording.first { it.pending } }
        assertEquals(summary.copy(active = false),controller.recording.value)
        now += 100_000
        assertEquals(summary.endedAtMillis,controller.recording.value.endedAtMillis)
        val saved = controller.saveRecording()
        val catalog = repository.observeCatalog().first()
        assertEquals(ActivityMode.COCKPIT,catalog.tracks.single { it.id == saved.id }.activityMode)
        assertEquals("Giro dedicato",catalog.tracks.single { it.id == saved.id }.title)
        assertNull(recordings.current())
    }

    @Test fun interruptedOtherActivityMustBeClosedBeforeStartingCockpit(): Unit = runBlocking {
        val recordings = RecordingRepository(database)
        controller = newController(recordings)
        controller.attachService();controller.startSession(null,true)
        val id = controller.recording.value.id
        controller.detachService()
        try { controller.startSession(null,true,mode = ActivityMode.COCKPIT);fail("Reused a free recording as Cockpit") }
        catch (_: IOException) { }
        assertEquals(id,controller.recording.value.id)
        controller.stopSession();controller.discardRecording()
        controller.attachService();controller.startSession(null,true,mode = ActivityMode.COCKPIT)
        assertNotEquals(id,controller.recording.value.id)
    }

    @Test fun persistedSessionModeAndLegacySessionInferenceAreCompatible(): Unit = runBlocking {
        val sessions = PreferencesNavigationSessionStore(app)
        sessions.clear()
        val saved = SavedNavigationSession(null,"Cockpit","recording-id",ActivityMode.COCKPIT)
        sessions.save(saved)
        assertEquals(saved,sessions.load())
        app.getSharedPreferences("navigation_session",0).edit().remove("activity_mode").commit()
        assertEquals(ActivityMode.FREE_RECORDING,sessions.load()!!.mode)
        sessions.clear()
    }
}
