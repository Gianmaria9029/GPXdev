package com.gpxnavigator.domain.model

data class SavedTrack(
    val id: String,
    val displayName: String,
    val trackName: String?,
    val importedAtMillis: Long,
    val sizeBytes: Long,
    val pointCount: Int?,
    val segmentCount: Int?,
    val totalDistanceMeters: Double?,
    val problem: String?,
    val activityMode: ActivityMode? = null,
    val customName: String? = null,
    val isFavorite: Boolean = false,
) {
    val title: String get() = customName?.takeIf { it.isNotBlank() }
        ?: trackName?.takeIf { it.isNotBlank() } ?: displayName
}

data class GpxCatalog(val tracks: List<SavedTrack>, val selectedTrackId: String?,
    val sortOrder: TrackSortOrder = TrackSortOrder.RECENT)
