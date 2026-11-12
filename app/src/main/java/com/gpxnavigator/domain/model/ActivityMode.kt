package com.gpxnavigator.domain.model

enum class ActivityMode {
    NAVIGATION, FREE_RECORDING, COCKPIT;

    companion object {
        fun forTrack(trackId: String?): ActivityMode = if (trackId == null) FREE_RECORDING else NAVIGATION

        // Older drafts have no mode. Their original track association remains authoritative.
        fun restored(value: String?, trackId: String?): ActivityMode =
            entries.firstOrNull { it.name == value } ?: forTrack(trackId)
    }
}
