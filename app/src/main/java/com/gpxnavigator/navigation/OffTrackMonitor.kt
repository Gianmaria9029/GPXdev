package com.gpxnavigator.navigation

private const val BASE_RETURN_DISTANCE_METERS = 25.0
private const val CONFIRMATION_MILLIS = 5_000L
private const val MIN_CONFIRMATION_FIXES = 3
internal const val MAX_OFF_TRACK_FIX_AGE_MILLIS = 10_000L
private const val MAX_FIX_GAP_MILLIS = 10_000L

enum class OffTrackStatus { UNAVAILABLE, ON_TRACK, CHECKING, OFF_TRACK }

data class OffTrackState(
    val status: OffTrackStatus = OffTrackStatus.UNAVAILABLE,
    val distanceMeters: Double? = null,
)

class OffTrackMonitor {
    private var state = OffTrackState()
    private var lastFixMillis: Long? = null
    private var candidateStartMillis: Long? = null
    private var candidateFixes = 0

    fun reset(): OffTrackState {
        state = OffTrackState()
        lastFixMillis = null
        clearCandidate()
        return state
    }

    fun update(distanceMeters: Double?, accuracyMeters: Float?, fixMillis: Long,
        nowMillis: Long): OffTrackState {
        if (distanceMeters == null || !distanceMeters.isFinite() || distanceMeters < 0 ||
            accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters !in 0f..MAX_TRACK_ACCURACY_METERS ||
            fixMillis < 0 || fixMillis > nowMillis || nowMillis - fixMillis > MAX_OFF_TRACK_FIX_AGE_MILLIS) {
            return reset()
        }
        val previousFix = lastFixMillis
        // UI recompositions and out-of-order fixes must not count as new GPS evidence.
        if (previousFix != null && fixMillis <= previousFix) return state
        if (previousFix != null && fixMillis - previousFix > MAX_FIX_GAP_MILLIS) reset()
        lastFixMillis = fixMillis

        val exitDistance = trackToleranceMeters(accuracyMeters)
        val returnDistance = maxOf(BASE_RETURN_DISTANCE_METERS, accuracyMeters.toDouble() * 1.5)
        val status = when {
            // Separate exit/return distances avoid flickering around one threshold.
            state.status == OffTrackStatus.OFF_TRACK && distanceMeters > returnDistance ->
                OffTrackStatus.OFF_TRACK
            distanceMeters <= exitDistance -> {
                clearCandidate()
                OffTrackStatus.ON_TRACK
            }
            else -> {
                val start = candidateStartMillis ?: fixMillis.also { candidateStartMillis = it }
                candidateFixes++
                if (candidateFixes >= MIN_CONFIRMATION_FIXES && fixMillis - start >= CONFIRMATION_MILLIS)
                    OffTrackStatus.OFF_TRACK
                else OffTrackStatus.CHECKING
            }
        }
        state = OffTrackState(status, distanceMeters)
        return state
    }

    private fun clearCandidate() {
        candidateStartMillis = null
        candidateFixes = 0
    }
}
