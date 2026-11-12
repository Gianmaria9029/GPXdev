package com.gpxnavigator.ui.navigation

import java.util.Locale

/** Anchors to the saved start once, then uses monotonic time despite wall-clock adjustments. */
internal class RecordingElapsedClock {
    private var id: String? = null
    private var startedAt: Long = 0
    private var anchorElapsed: Long = 0
    private var initialDuration: Long = 0

    fun elapsedMillis(recordingId: String, startedAtMillis: Long, wallMillis: Long, elapsedMillis: Long): Long? {
        if (startedAtMillis <= 0) return null
        if (id != recordingId || startedAt != startedAtMillis || elapsedMillis < anchorElapsed) {
            id = recordingId
            startedAt = startedAtMillis
            anchorElapsed = elapsedMillis
            initialDuration = (wallMillis - startedAtMillis).coerceAtLeast(0)
        }
        return initialDuration + (elapsedMillis - anchorElapsed).coerceAtLeast(0)
    }

    fun clear() { id = null }
}

internal fun recordingElapsedText(milliseconds: Long?): String? {
    val seconds = milliseconds?.takeIf { it >= 0 }?.div(1_000) ?: return null
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, (seconds / 60) % 60, seconds % 60)
}
