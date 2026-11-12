package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TrackLibraryTest {
    private fun track(id: String, title: String, distance: Double? = 100.0, favorite: Boolean = false,
        imported: Long = 1, filename: String = "$id.gpx") =
        SavedTrack(id, filename, title, imported, 100, 2, 1, distance, null, isFavorite = favorite)

    @Test fun searchIgnoresCaseAccentsAndExtraSpacesAndIncludesOriginalFilename() {
        val tracks = listOf(track("a", "Città delle Dolomiti", filename = "estate_2026.gpx"), track("b", "Lago"))
        for (query in listOf(" CITTA   dolomiti ", "città", "ESTATE_2026", "dolomiti estate"))
            assertEquals(listOf("a"), libraryTracks(tracks, query, false, TrackSortOrder.RECENT).map { it.id })
        assertTrue(libraryTracks(tracks, "lago dolomiti", false, TrackSortOrder.RECENT).isEmpty())
    }

    @Test fun searchUsesRenamedTitleAndStillFindsOriginalFile() {
        val renamed = track("a", "Titolo GPX", filename = "originale.gpx").copy(customName = "Giro del sabato")
        for (query in listOf("sabato", "originale"))
            assertEquals(listOf(renamed), libraryTracks(listOf(renamed), query, false, TrackSortOrder.NAME))
    }

    @Test fun favoritesSearchAndDistanceSortCombineWithoutChangingSelectionOrInputOrder() {
        val tracks = listOf(track("a", "Toscana mare", 300.0, true), track("b", "Toscana colline", 100.0, true),
            track("c", "Toscana lago", 50.0), track("d", "Dolomiti", 25.0, true))
        assertEquals(listOf("b", "a"), libraryTracks(tracks, "Toscana", true, TrackSortOrder.DISTANCE_ASC).map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), tracks.map { it.id })
    }

    @Test fun recentMeansImportDateAndTiesAreStable() {
        val tracks = listOf(track("b", "Alpi", imported = 10), track("c", "Lago", imported = 20),
            track("a", "Monti", imported = 10))
        assertEquals(listOf("c", "a", "b"), libraryTracks(tracks, "", false, TrackSortOrder.RECENT).map { it.id })
    }

    @Test fun alphabeticalSortIgnoresCaseAndAccentsAndUsesIdForTies() {
        val tracks = listOf(track("b", "Àlpi"), track("c", "Zeta"), track("a", "alpi"))
        assertEquals(listOf("a", "b", "c"), libraryTracks(tracks, "", false, TrackSortOrder.NAME).map { it.id })
    }

    @Test fun unknownDistancesStayLastInBothDirections() {
        val tracks = listOf(track("missing", "A", null), track("long", "B", 900.0), track("zero", "C", 0.0),
            track("nan", "D", Double.NaN), track("negative", "E", -5.0), track("infinite", "F", Double.POSITIVE_INFINITY))
        assertEquals(listOf("zero", "long", "missing", "nan", "negative", "infinite"),
            libraryTracks(tracks, "", false, TrackSortOrder.DISTANCE_ASC).map { it.id })
        assertEquals(listOf("long", "zero", "missing", "nan", "negative", "infinite"),
            libraryTracks(tracks, "", false, TrackSortOrder.DISTANCE_DESC).map { it.id })
    }

    @Test fun renameValidationRejectsBlankOverlongAndUnsafeNames() {
        for (name in listOf("", "  ", ".gpx")) assertEquals(TrackNameError.EMPTY, trackNameError(name))
        assertEquals(TrackNameError.TOO_LONG, trackNameError("a".repeat(81)))
        for (name in listOf("a/b", "a\\b", "a\nb", "a:b", "a\u0000b"))
            assertEquals(TrackNameError.INVALID_CHARACTERS, trackNameError(name))
        assertNull(trackNameError("Giro d’autunno — città"))
        assertNull(trackNameError("a".repeat(80)))
    }

    @Test fun unknownSortPreferenceUsesRecent() {
        assertEquals(TrackSortOrder.RECENT, TrackSortOrder.restored(null))
        assertEquals(TrackSortOrder.RECENT, TrackSortOrder.restored("obsolete"))
    }
}
