package com.gpxnavigator.domain.model

import java.text.Normalizer
import java.util.Locale

enum class TrackSortOrder { RECENT, NAME, DISTANCE_ASC, DISTANCE_DESC;
    companion object {
        fun restored(value: String?) = entries.firstOrNull { it.name == value } ?: RECENT
    }
}

private val accents = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")

private fun searchable(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(accents, "").lowercase(Locale.ROOT).trim().replace(whitespace, " ")

/** Searches catalog metadata only; never opens GPX files. Unknown distances always sort last. */
fun libraryTracks(tracks: List<SavedTrack>, query: String, favoritesOnly: Boolean,
    order: TrackSortOrder): List<SavedTrack> {
    val terms = searchable(query).split(' ').filter { it.isNotEmpty() }
    val filtered = tracks.filter { track ->
        (!favoritesOnly || track.isFavorite) && (terms.isEmpty() ||
            searchable("${track.title} ${track.displayName}").let { text -> terms.all { it in text } })
    }
    val byName = compareBy<SavedTrack> { searchable(it.title) }.thenBy { it.id }
    fun distance(track: SavedTrack) = track.totalDistanceMeters?.takeIf { it.isFinite() && it >= 0 }
    val comparator = when (order) {
        TrackSortOrder.RECENT -> compareByDescending<SavedTrack> { it.importedAtMillis }.thenBy { it.id }
        TrackSortOrder.NAME -> byName
        TrackSortOrder.DISTANCE_ASC -> compareBy<SavedTrack> { distance(it) == null }
            .thenBy { distance(it) ?: 0.0 }.then(byName)
        TrackSortOrder.DISTANCE_DESC -> compareBy<SavedTrack> { distance(it) == null }
            .thenByDescending { distance(it) ?: 0.0 }.then(byName)
    }
    return filtered.sortedWith(comparator)
}

enum class TrackNameError { EMPTY, TOO_LONG, INVALID_CHARACTERS }

fun trackNameError(value: String): TrackNameError? = when {
    normalizedRecordingName(value) == null -> TrackNameError.EMPTY
    recordingNameError(value) == RecordingNameError.TOO_LONG -> TrackNameError.TOO_LONG
    recordingNameError(value) == RecordingNameError.INVALID_CHARACTERS -> TrackNameError.INVALID_CHARACTERS
    else -> null
}
