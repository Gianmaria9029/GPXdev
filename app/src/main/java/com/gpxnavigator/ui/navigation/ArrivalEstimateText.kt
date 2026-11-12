package com.gpxnavigator.ui.navigation

import android.content.Context
import android.text.format.DateFormat
import com.gpxnavigator.R
import com.gpxnavigator.navigation.ArrivalEstimate
import com.gpxnavigator.navigation.ArrivalStatus
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

internal fun arrivalEstimateText(context: Context, estimate: ArrivalEstimate): String {
    if (estimate.status != ArrivalStatus.READY) return context.getString(when (estimate.status) {
        ArrivalStatus.INACTIVE -> R.string.arrival_inactive
        ArrivalStatus.PAUSED -> R.string.arrival_paused
        ArrivalStatus.WAITING_FOR_GPS -> R.string.arrival_waiting_gps
        ArrivalStatus.MATCHING_TRACK -> R.string.arrival_matching
        ArrivalStatus.OFF_TRACK -> R.string.arrival_off_track
        ArrivalStatus.REVERSE_DIRECTION -> R.string.arrival_reverse
        ArrivalStatus.DISCONTINUOUS_TRACK -> R.string.arrival_discontinuous
        ArrivalStatus.SEGMENT_END -> R.string.arrival_segment_end
        ArrivalStatus.ARRIVED -> R.string.guidance_track_end
        else -> R.string.arrival_calculating
    })
    val seconds = estimate.remainingSeconds ?: return context.getString(R.string.arrival_calculating)
    val arrivalText = readyArrivalClock(context, estimate) ?: return context.getString(R.string.arrival_calculating)
    val minutes = seconds / 60 + if (seconds % 60 > 0) 1 else 0
    val duration = when {
        seconds < 60 -> context.getString(R.string.arrival_less_than_minute)
        minutes < 60 -> context.getString(R.string.arrival_minutes, minutes)
        else -> context.getString(R.string.arrival_hours_minutes, minutes / 60, minutes % 60)
    }
    return context.getString(R.string.arrival_ready, duration, arrivalText)
}

internal fun arrivalClockText(context: Context, estimate: ArrivalEstimate): String {
    if (estimate.status == ArrivalStatus.READY) {
        return readyArrivalClock(context, estimate) ?: context.getString(R.string.arrival_clock_calculating)
    }
    return context.getString(when (estimate.status) {
        ArrivalStatus.INACTIVE -> R.string.metric_unavailable
        ArrivalStatus.PAUSED -> R.string.arrival_clock_paused
        ArrivalStatus.WAITING_FOR_GPS -> R.string.arrival_clock_waiting_gps
        ArrivalStatus.MATCHING_TRACK -> R.string.arrival_clock_matching
        ArrivalStatus.OFF_TRACK -> R.string.arrival_clock_off_track
        ArrivalStatus.REVERSE_DIRECTION -> R.string.arrival_clock_reverse
        ArrivalStatus.DISCONTINUOUS_TRACK -> R.string.arrival_clock_discontinuous
        ArrivalStatus.SEGMENT_END -> R.string.arrival_clock_segment_end
        ArrivalStatus.ARRIVED -> R.string.guidance_track_end
        else -> R.string.arrival_clock_calculating
    })
}

private fun readyArrivalClock(context: Context, estimate: ArrivalEstimate): String? {
    if (estimate.remainingSeconds == null) return null
    val arrival = estimate.arrivalTimeMillis ?: return null
    val observed = estimate.observedTimeMillis ?: return null
    val clockTime = DateFormat.getTimeFormat(context).format(Date(arrival))
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(observed).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(arrival).atZone(zone).toLocalDate())
    return if (days > 0) context.getString(R.string.arrival_next_day, clockTime, days) else clockTime
}
