@file:Suppress("DEPRECATION")

package com.gpxnavigator.qa

import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.data.navigation.SavedNavigationSession
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
import com.gpxnavigator.navigation.SessionStatus
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class QaSessionRecoveryTest : NavigationTestSupport() {
    private lateinit var recordings: RecordingRepository

    @Before fun enableRecording() {
        recordings = RecordingRepository(database)
        controller = newController(recordings)
    }

    private suspend fun interruptedPendingRecording(): String {
        controller.attachService()
        controller.startSession(null, true, "Recovery QA")
        now += 3_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.0, 11.0, 5f, 2f, null, null, now * 1_000_000L), true)
        withTimeout(5_000) { controller.recording.first { it.pointCount == 1 } }
        store.failClear = true
        controller.stopSession()
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertTrue(controller.recording.value.pending)
        return controller.recording.value.id!!
    }

    private suspend fun assertIdleAfterReopening() {
        controller.detachService()
        controller = newController(recordings)
        withTimeout(5_000) { controller.session.first { it.status != SessionStatus.RESTORING } }
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
    }

    @Test fun savingAfterClearRecoversLeavesNoPhantomSession(): Unit = runBlocking {
        val id = interruptedPendingRecording()
        store.failClear = false
        val saved = controller.saveRecording()
        assertEquals(id, saved.id)
        assertEquals(1, repository.loadTrack(saved).segments.single().points.size)
        assertNull(recordings.current())
        assertNull(store.saved)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        assertIdleAfterReopening()
    }

    @Test fun failedClearBeforeDiscardKeepsTheDraftForRetry(): Unit = runBlocking {
        val id = interruptedPendingRecording()
        try { controller.discardRecording(); fail("Discard must report persistence failure") }
        catch (_: IOException) { }
        assertEquals(id, recordings.current()!!.id)
        assertEquals(1, recordings.points(id).size)
        assertTrue(controller.recording.value.pending)
        store.failClear = false
        controller.discardRecording()
        assertNull(recordings.current())
        assertNull(store.saved)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        assertIdleAfterReopening()
    }

    @Test fun failedClearBeforeSaveKeepsTheDraftAndAvoidsDuplicateCatalogEntries(): Unit = runBlocking {
        val id = interruptedPendingRecording()
        try { controller.saveRecording(); fail("Save must report persistence failure") }
        catch (_: IOException) { }
        assertEquals(id, recordings.current()!!.id)
        assertEquals(1, repository.observeCatalog().first().tracks.size)
        store.failClear = false
        controller.saveRecording()
        assertEquals(2, repository.observeCatalog().first().tracks.size)
        assertNull(store.saved)
        assertIdleAfterReopening()
    }

    @Test fun staleSavedRecordingWithoutADraftIsRemovedOnRestoration(): Unit = runBlocking {
        for (source in listOf(null, trackId)) {
            store.saved = SavedNavigationSession(source, "Finished QA", "missing-recording")
            assertIdleAfterReopening()
            assertNull(store.saved)
        }
    }

    @Test fun orphanClearFailureIsReportedWithoutOfferingAnUnusableResume(): Unit = runBlocking {
        store.saved = SavedNavigationSession(null, "Finished QA", "missing-recording")
        store.failClear = true
        assertIdleAfterReopening()
        assertNotNull(controller.session.value.error)
        store.failClear = false
        assertIdleAfterReopening()
        assertNull(store.saved)
    }
}
