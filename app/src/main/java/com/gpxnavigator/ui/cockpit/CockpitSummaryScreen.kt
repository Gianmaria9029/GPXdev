package com.gpxnavigator.ui.cockpit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxnavigator.R
import com.gpxnavigator.navigation.RecordingState
import com.gpxnavigator.ui.navigation.NavigationStatusBar
import com.gpxnavigator.ui.navigation.recordingElapsedText
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Uses only the frozen draft: no current position, ticking clock or synthetic final samples. */
@Composable
internal fun CockpitSummaryScreen(recording: RecordingState, busy: Boolean, error: String?, onSave: () -> Unit, onDiscard: () -> Unit) {
    NavigationStatusBar()
    BackHandler { /* A pending draft is kept until an explicit save or discard. */ }
    val missing = stringResource(R.string.metric_unavailable)
    val stats = recording.statistics
    val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    val date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())
    val start = recording.startedAtMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    val end = recording.endedAtMillis?.let(Instant::ofEpochMilli)
    val elapsed = recording.endedAtMillis?.let { (it - recording.startedAtMillis).coerceAtLeast(0) }
    val metrics = listOf(
        CockpitMetric(R.string.cockpit_maximum_speed, stats.maximumSpeedPoint?.speedMetersPerSecond
            ?.let { stringResource(R.string.cockpit_speed_number, it * 3.6f) } ?: missing, R.string.metric_speed_unit),
        CockpitMetric(R.string.cockpit_moving_average, stats.movingAverageSpeedMetersPerSecond
            ?.let { stringResource(R.string.metric_speed_number, it * 3.6) } ?: missing, R.string.metric_speed_unit),
        CockpitMetric(R.string.cockpit_distance, stringResource(R.string.metric_distance_number, recording.distanceMeters / 1_000), R.string.metric_distance_unit),
        CockpitMetric(R.string.cockpit_summary_duration, recordingElapsedText(elapsed) ?: missing, R.string.cockpit_duration_unit),
        CockpitMetric(R.string.cockpit_start_altitude, stats.startElevationMeters?.let { stringResource(R.string.cockpit_elevation_number,it) } ?: missing, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_end_altitude, stats.endElevationMeters?.let { stringResource(R.string.cockpit_elevation_number,it) } ?: missing, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_minimum_altitude, stats.minimumElevationMeters?.let { stringResource(R.string.cockpit_elevation_number,it) } ?: missing, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_maximum_altitude, stats.maximumElevationMeters?.let { stringResource(R.string.cockpit_elevation_number,it) } ?: missing, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_altitude_difference, stats.elevationDifferenceMeters?.let { stringResource(R.string.cockpit_altitude_difference_number,it) } ?: missing, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_started_at, start?.let(time::format) ?: missing, R.string.cockpit_started_at_unit, start?.let(date::format) ?: missing),
        CockpitMetric(R.string.cockpit_ended_at, end?.let(time::format) ?: missing, R.string.cockpit_started_at_unit, end?.let(date::format) ?: missing),
        CockpitMetric(R.string.cockpit_recorded_points, recording.pointCount.toString(), R.string.cockpit_points_unit),
    )
    val visibleMetrics = if (recording.pointCount == 0) metrics.filter { it.label in setOf(
        R.string.cockpit_summary_duration, R.string.cockpit_started_at, R.string.cockpit_ended_at, R.string.cockpit_recorded_points) } else metrics
    var showError by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Column(Modifier.fillMaxWidth().height(56.dp), verticalArrangement = Arrangement.Center) {
                BasicText(stringResource(R.string.cockpit_summary_title), Modifier.fillMaxWidth().height(32.dp), maxLines = 1,
                    style = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold),
                    autoSize = TextAutoSize.StepBased(12.sp,22.sp,.5.sp))
                BasicText(recording.title ?: stringResource(R.string.cockpit_title), Modifier.fillMaxWidth().height(20.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                    autoSize = TextAutoSize.StepBased(9.sp,13.sp,.5.sp))
            }
            val message = error ?: recording.error
            if (message != null) TextButton(onClick = { showError = true }, modifier = Modifier.fillMaxWidth()) {
                Text(message, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (recording.pointCount == 0) Text(stringResource(R.string.cockpit_no_points),
                Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelMedium)
            CockpitMetricsGrid(visibleMetrics, Modifier.weight(1f).fillMaxWidth(),
                landscapeColumns = if (recording.pointCount == 0) 4 else 6, highlightFirst = false)
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onDiscard, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(stringResource(R.string.cockpit_discard), maxLines = 1)
                }
                Button(onSave, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy && recording.pointCount > 0,
                    colors = navigatorButtonColors(), border = navigatorButtonBorder(), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(stringResource(R.string.cockpit_save), maxLines = 1)
                }
            }
        }
    }
    if (showError) AlertDialog(onDismissRequest = { showError = false }, text = { Text(error ?: recording.error.orEmpty()) },
        confirmButton = { TextButton(onClick = { showError = false }) { Text(stringResource(R.string.close_details)) } })
}
