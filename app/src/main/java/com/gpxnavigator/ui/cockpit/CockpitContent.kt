package com.gpxnavigator.ui.cockpit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.recordingSpeed
import com.gpxnavigator.ui.components.RecordingIndicator
import com.gpxnavigator.ui.components.StopNavigationButton
import com.gpxnavigator.ui.navigation.recordingElapsedText
import com.gpxnavigator.ui.theme.LocalAppTheme
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class CockpitMetric(val label: Int, val value: String, val unit: Int, val unitText: String? = null)

@Composable
internal fun CockpitContent(state: CockpitUiState, onBack: () -> Unit,
    onStop: () -> Boolean, onRetry: () -> Unit, onLocationSettings: () -> Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    val unavailable = stringResource(R.string.metric_unavailable)
    val point = state.location.point.takeIf { state.location.status == LocationStatus.ACTIVE }
    val recording = state.recording
    val statistics = recording.statistics
    val hasRecording = recording.id != null
    val maximum = statistics.maximumSpeedPoint?.speedMetersPerSecond
    val metrics = listOf(
        CockpitMetric(R.string.cockpit_current_speed, recordingSpeed(point?.speedMetersPerSecond)
            ?.let { stringResource(R.string.cockpit_speed_number, it * 3.6f) } ?: unavailable, R.string.metric_speed_unit),
        CockpitMetric(R.string.cockpit_maximum_speed, recordingSpeed(maximum)
            ?.let { stringResource(R.string.cockpit_speed_number, it * 3.6f) } ?: unavailable, R.string.metric_speed_unit),
        CockpitMetric(R.string.cockpit_moving_average, statistics.movingAverageSpeedMetersPerSecond
            ?.let { stringResource(R.string.metric_speed_number, it * 3.6) } ?: unavailable, R.string.metric_speed_unit),
        CockpitMetric(R.string.cockpit_distance, if (hasRecording)
            stringResource(R.string.metric_distance_number, recording.distanceMeters / 1_000) else unavailable, R.string.metric_distance_unit),
        CockpitMetric(R.string.metric_recording_time_label, recordingElapsedText(state.elapsedMillis) ?: unavailable, R.string.cockpit_duration_unit),
        CockpitMetric(R.string.cockpit_altitude, point?.elevationMeters?.takeIf { it.isFinite() }
            ?.let { stringResource(R.string.cockpit_elevation_number, it) } ?: unavailable, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_minimum_altitude, statistics.minimumElevationMeters
            ?.let { stringResource(R.string.cockpit_elevation_number, it) } ?: unavailable, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_maximum_altitude, statistics.maximumElevationMeters
            ?.let { stringResource(R.string.cockpit_elevation_number, it) } ?: unavailable, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_altitude_difference, statistics.elevationDifferenceMeters
            ?.let { stringResource(R.string.cockpit_altitude_difference_number, it) } ?: unavailable, R.string.metric_elevation_unit),
        CockpitMetric(R.string.cockpit_started_at, if (hasRecording && recording.startedAtMillis > 0)
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(recording.startedAtMillis))
            else unavailable, R.string.cockpit_started_at_unit),
    )
    val gpsLabel = when (state.location.status) {
        LocationStatus.ACTIVE -> point?.accuracyMeters?.takeIf { it.isFinite() }
            ?.let { stringResource(R.string.gps_status_accuracy, it) } ?: stringResource(R.string.gps_status_active)
        LocationStatus.DISABLED -> stringResource(R.string.gps_short_disabled)
        LocationStatus.DENIED -> stringResource(R.string.gps_short_denied)
        LocationStatus.ERROR -> stringResource(R.string.gps_short_error)
        LocationStatus.STOPPED -> stringResource(R.string.gps_short_stopped)
        LocationStatus.UNAVAILABLE -> stringResource(R.string.gps_short_unavailable)
        LocationStatus.WAITING -> stringResource(R.string.gps_short_waiting)
    }
    val error = state.session.error ?: recording.error
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.cockpit_title), Modifier.fillMaxWidth().height(28.dp), maxLines = 1,
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                    autoSize = TextAutoSize.StepBased(12.sp, 22.sp, .5.sp))
            }
            TextButton(onClick = { details = true }) {
                Text(gpsLabel, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
            if (recording.active) RecordingIndicator()
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(error ?: if (state.session.isRunning) state.session.title ?: stringResource(R.string.cockpit_no_recording)
                    else stringResource(R.string.cockpit_no_activity), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CockpitMetricsGrid(metrics, Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onBack, Modifier.height(48.dp)) {
                Icon(painterResource(R.drawable.ic_home), null, Modifier.size(20.dp))
                Text(stringResource(R.string.home_button), Modifier.padding(start = 8.dp))
            }
            if (state.session.isRunning) StopNavigationButton({ onStop() }, Modifier.height(48.dp))
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false },
        title = { Text(stringResource(R.string.cockpit_gps_details)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(gpsLabel)
            if (state.location.status == LocationStatus.ACTIVE && !state.location.precise)
                Text(stringResource(R.string.location_approximate))
            error?.let { Text(it) }
        } },
        confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.close_details)) } },
        dismissButton = { Row {
            TextButton(onClick = { onRetry(); details = false }) { Text(stringResource(R.string.retry_location)) }
            if (state.location.status == LocationStatus.DISABLED || state.location.status == LocationStatus.DENIED)
                TextButton(onClick = onLocationSettings) { Text(stringResource(R.string.location_settings)) }
        } })
}

@Composable
internal fun CockpitMetricsGrid(metrics: List<CockpitMetric>, modifier: Modifier = Modifier, landscapeColumns: Int = 5,
    highlightFirst: Boolean = true) {
    BoxWithConstraints(modifier.padding(bottom = 4.dp)) {
        val columns = if (maxWidth > maxHeight) landscapeColumns else 2
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { metric ->
                        CockpitTile(metric, highlightFirst && metric === metrics.first(), Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitTile(metric: CockpitMetric, prominent: Boolean, modifier: Modifier) {
    val palette = LocalNavigatorPalette.current
    val cyber = LocalAppTheme.current == AppTheme.CYBER
    val label = stringResource(metric.label)
    val unit = metric.unitText ?: stringResource(metric.unit)
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(modifier.semantics { testTag = "cockpit-metric-${metric.label}"; contentDescription = "$label: ${metric.value} $unit" },
        color = if (prominent) palette.maneuverBackground else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (prominent && cyber) palette.action else palette.instrumentBorder)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            val maximumFont = if (prominent) 62f else if (metric.unit == R.string.cockpit_duration_unit) 30f else 40f
            val labelHeight = (maxHeight * .30f).coerceIn(32.dp, 36.dp)
            Column(Modifier.fillMaxSize()) {
                BasicText(label, Modifier.fillMaxWidth().height(labelHeight),
                    style = TextStyle(color = ink, fontWeight = FontWeight.Medium), maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = .5.sp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    BasicText(metric.value, Modifier.fillMaxWidth(), maxLines = 1, softWrap = false,
                        style = TextStyle(color = if (prominent && cyber) palette.followActive else ink,
                            fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = (-.5).sp),
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = maximumFont.sp, stepSize = .5.sp))
                }
                BasicText(unit, Modifier.fillMaxWidth().height(18.dp), maxLines = 1,
                    style = TextStyle(color = ink),
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp, stepSize = .5.sp))
            }
        }
    }
}
