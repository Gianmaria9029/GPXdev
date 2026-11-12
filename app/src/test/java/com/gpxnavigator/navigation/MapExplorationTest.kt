@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MapExplorationTest : NavigationTestSupport() {
    private suspend fun awaitTrack() {
        withTimeout(5_000) { controller.data.first { it.trackId == trackId && it.track != null } }
    }

    @Test fun mapExplorationDoesNotLoadTheImportedOrPreviouslySelectedGpx(): Unit = runBlocking {
        assertEquals(trackId, repository.loadSelected()!!.id)
        controller.prepareMap()
        assertNull(controller.data.value.track)
        assertNull(controller.data.value.trackId)
        assertNull(controller.data.value.progress)
        assertEquals(trackId, repository.loadSelected()!!.id)
        assertTrue(repository.loadSelected()!!.file.isFile)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
    }

    @Test fun stopThenExploreClearsTheOldRouteAndAllowsStartingItAgain(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        awaitTrack()
        controller.stopSession()
        assertNull(controller.data.value.track)
        assertNull(controller.data.value.trackId)
        assertNull(controller.data.value.progress)
        assertNull(controller.data.value.guidance.maneuver)
        assertNull(controller.data.value.maximumSpeedMetersPerSecond)
        controller.setScreenVisible(false)
        controller.prepareMap()
        controller.setScreenVisible(true)
        assertNull(controller.data.value.track)
        assertNull(controller.data.value.progress)
        assertEquals(trackId, repository.loadSelected()!!.id)
        controller.startSession(trackId)
        awaitTrack()
        assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
    }

    @Test fun returningToTheMapPreservesAnActiveNavigation(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        awaitTrack()
        val original = controller.data.value.track
        controller.setScreenVisible(false)
        controller.prepareMap()
        controller.setScreenVisible(true)
        assertSame(original, controller.data.value.track)
        assertEquals(trackId, controller.data.value.trackId)
        assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
        assertEquals(trackId, store.saved!!.trackId)
        assertTrue(provider.running)
    }

    @Test fun exploringAnInterruptedSessionDoesNotResumeOrDiscardIt(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        awaitTrack()
        controller.detachService()
        val saved = store.saved
        controller.prepareMap()
        assertNull(controller.data.value.track)
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertEquals(saved, store.saved)
        assertFalse(provider.running)
        controller.attachService()
        controller.startSession(trackId)
        awaitTrack()
        assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
    }

    @Test fun restartingTheAppDoesNotTurnAnArchiveSelectionIntoAMapRoute(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        awaitTrack()
        controller.stopSession()
        controller = newController()
        controller.prepareMap()
        assertNull(controller.data.value.track)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        assertEquals(trackId, repository.loadSelected()!!.id)
        assertNull(store.saved)
    }
}
