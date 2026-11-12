@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.importgpx

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gpxnavigator.domain.model.TrackSortOrder
import com.gpxnavigator.navigation.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class TrackLibraryViewModelTest : NavigationTestSupport() {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Library update timed out", condition())
    }

    @Test fun queryAndFavoriteFilterRestoreAndSortPersistsSeparately(): Unit = runBlocking {
        val other = repository.import(fixture("m10-single-point.gpx")).imported.id!!
        repository.setFavorite(trackId, true)
        val handle = SavedStateHandle()
        val first = ImportGpxViewModel(app, handle)
        try {
            await { first.state.value.catalogLoaded }
            first.setQuery("  FIRENZE ")
            first.setFavoritesOnly(true)
            first.setSortOrder(TrackSortOrder.NAME)
            await { first.state.value.sortOrder == TrackSortOrder.NAME && !first.state.value.working }
            assertEquals(listOf(trackId), first.state.value.visibleTracks.map { it.id })
            assertEquals(other, first.state.value.selectedTrackId)
            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val second = ImportGpxViewModel(app, restored)
            try {
                await { second.state.value.catalogLoaded }
                assertEquals("  FIRENZE ", second.state.value.query)
                assertTrue(second.state.value.favoritesOnly)
                assertEquals(TrackSortOrder.NAME, second.state.value.sortOrder)
                assertEquals(listOf(trackId), second.state.value.visibleTracks.map { it.id })
                second.clearFilters()
                assertEquals(2, second.state.value.visibleTracks.size)
                assertEquals(TrackSortOrder.NAME, second.state.value.sortOrder)
            } finally { second.viewModelScope.cancel() }
        } finally { first.viewModelScope.cancel() }
    }

    @Test fun favoriteChangesAndRenameUpdateCurrentResultsWithoutChangingSelection() {
        val model = ImportGpxViewModel(app, SavedStateHandle())
        try {
            await { model.state.value.catalogLoaded }
            model.setQuery("nuovo")
            model.setFavoritesOnly(true)
            assertTrue(model.state.value.visibleTracks.isEmpty())
            model.setFavorite(trackId, true)
            await { model.state.value.tracks.single().isFavorite && !model.state.value.working }
            var renamed = false
            model.rename(trackId, "Nuovo giro") { renamed = true }
            await { renamed && model.state.value.visibleTracks.size == 1 }
            assertEquals("Nuovo giro", model.state.value.visibleTracks.single().title)
            assertEquals(trackId, model.state.value.selectedTrackId)
            model.setFavorite(trackId, false)
            await { model.state.value.visibleTracks.isEmpty() }
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun activeAndInterruptedSessionsRejectRenameAndLeaveExistingTitle(): Unit = runBlocking {
        for (interrupted in listOf(false, true)) {
            if (interrupted) {
                controller.stopSession()
                store.saved = com.gpxnavigator.data.navigation.SavedNavigationSession(trackId, "Titolo sessione")
                controller = newController()
            } else {
                controller.attachService()
                controller.startSession(trackId)
            }
            val model = ImportGpxViewModel(app, SavedStateHandle())
            try {
                await { model.state.value.catalogLoaded && model.state.value.session.status != SessionStatus.RESTORING }
                assertFalse(canRenameTrack(model.state.value, trackId))
                var success = false
                model.rename(trackId, "Non consentito") { success = true }
                await { model.state.value.error != null }
                assertFalse(success)
                assertNull(model.state.value.tracks.single().customName)
            } finally { model.viewModelScope.cancel() }
        }
    }
}
