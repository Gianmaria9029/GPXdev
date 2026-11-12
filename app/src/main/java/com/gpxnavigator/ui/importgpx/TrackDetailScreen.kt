package com.gpxnavigator.ui.importgpx

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.gpxnavigator.ui.theme.navigatorCardColors
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.domain.model.SavedTrack
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.map.MapView
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TrackDetailScreen(saved: SavedTrack?, detail: TrackDetailUiState, state: ImportGpxUiState,
    awaitingPermission: Boolean, onBack: () -> Unit, onStart: () -> Unit, onToggleRecording: () -> Unit,
    onOpenNavigation: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onRetry: () -> Unit, onPreviewError: (String) -> Unit, onRename: () -> Unit, onFavorite: (Boolean) -> Unit) {
    var menu by rememberSaveable(saved?.id) { mutableStateOf(false) }
    val available = state.catalogLoaded && !state.loading && !state.working && !awaitingPermission
    val ownSession = state.session.isRunning && state.session.trackId == saved?.id && saved != null
    val canStart = available && saved != null && saved.problem == null && detail.summary != null &&
        !detail.loading && detail.error == null && state.session.status == SessionStatus.IDLE && state.recording.id == null
    val blocked = when {
        state.session.isRunning && !ownSession -> R.string.detail_session_blocked
        state.session.status == SessionStatus.INTERRUPTED -> R.string.detail_interrupted_blocked
        state.recording.id != null && !ownSession -> R.string.detail_pending_blocked
        else -> null
    }
    val actions: @Composable () -> Unit = {
        Surface(shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ownSession) {
                    Surface(onClick = onToggleRecording, enabled = canStart, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.semantics {
                            role = Role.Checkbox
                            toggleableState = if (state.recordGpx) ToggleableState.On else ToggleableState.Off
                        },
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Checkbox(checked = state.recordGpx, onCheckedChange = null, enabled = canStart,
                                modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(stringResource(R.string.detail_record), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.detail_record_hint), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                blocked?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                (state.error ?: state.session.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = if (ownSession) onOpenNavigation else onStart, enabled = canStart || (ownSession && available),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp),
                    colors = navigatorButtonColors(), border = navigatorButtonBorder()) {
                    Text(stringResource(if (awaitingPermission) R.string.recording_setup_permissions else if (ownSession)
                        R.string.home_return_map else R.string.start_navigation), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(saved?.title ?: stringResource(R.string.detail_missing), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            if (saved?.activityMode == ActivityMode.COCKPIT)
                Text(stringResource(R.string.cockpit_saved_origin), style = MaterialTheme.typography.labelMedium)
            if (detail.loading) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.detail_loading))
            }
            (detail.error ?: saved?.problem)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (detail.error != null) TextButton(onClick = onRetry, enabled = available) { Text(stringResource(R.string.detail_retry)) }
            detail.summary?.let { summary ->
                DetailSection(stringResource(R.string.detail_distance)) {
                    Text(stringResource(R.string.metric_distance_value, summary.distanceMeters / 1_000),
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    if (summary.segmentCount > 1) Text(stringResource(R.string.detail_segments_note))
                }
                TrackPreview(detail, onPreviewError)
                DetailSection(stringResource(R.string.detail_elevation)) {
                    DetailValue(R.string.detail_minimum, elevation(summary.minimumElevation))
                    DetailValue(R.string.detail_maximum, elevation(summary.maximumElevation))
                    DetailValue(R.string.detail_ascent, elevation(summary.ascentMeters))
                    DetailValue(R.string.detail_descent, elevation(summary.descentMeters))
                    Text(stringResource(if (summary.elevationIncomplete) R.string.detail_partial_elevation else R.string.detail_elevation_note),
                        style = MaterialTheme.typography.bodySmall)
                }
                DetailSection(stringResource(R.string.detail_start_finish)) {
                    Endpoint(R.string.detail_start, summary.start)
                    HorizontalDivider()
                    Endpoint(R.string.detail_finish, summary.finish)
                }
                DetailSection(stringResource(R.string.detail_times)) {
                    DetailValue(R.string.detail_start_time, dateTime(summary.start?.timestamp))
                    DetailValue(R.string.detail_finish_time, dateTime(summary.finish?.timestamp))
                    val duration = summary.recordedDuration?.seconds
                    DetailValue(R.string.detail_duration, duration?.let {
                        stringResource(R.string.detail_duration_value, it / 3_600, (it / 60) % 60, it % 60)
                    } ?: stringResource(R.string.detail_unavailable))
                    Text(stringResource(R.string.detail_time_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (saved != null) DetailSection(stringResource(R.string.detail_file)) {
                DetailValue(R.string.detail_file_name, saved.displayName)
                DetailValue(R.string.detail_imported, dateTime(Instant.ofEpochMilli(saved.importedAtMillis)))
                DetailValue(R.string.detail_file_size, Formatter.formatFileSize(LocalContext.current, saved.sizeBytes))
                DetailValue(R.string.detail_points, (detail.summary?.pointCount ?: saved.pointCount)?.toString()
                    ?: stringResource(R.string.detail_unavailable))
                DetailValue(R.string.detail_segments, detail.summary?.segmentCount?.toString() ?: stringResource(R.string.detail_unavailable))
            }
            Text(stringResource(R.string.detail_gps_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val pinned = maxHeight.value >= 480f * scale
        // Wide/short screens use a dedicated action pane, keeping the map and departure reachable.
        val twoPanes = maxWidth.value >= 760f * scale && maxHeight.value >= 300f * scale
        Column(Modifier.fillMaxSize()) {
            PageHeader(stringResource(R.string.detail_title), onBack) {
                if (saved != null) FavoriteButton(saved, available, onFavorite)
                Box {
                    IconButton(onClick = { menu = true }, enabled = saved != null && available, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_more), stringResource(R.string.detail_actions))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.library_rename)) },
                            enabled = saved != null && canRenameTrack(state, saved.id),
                            onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_gpx)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_export), null) },
                            onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_track), color = MaterialTheme.colorScheme.error) },
                            enabled = !state.session.isRunning || state.session.trackId != saved?.id,
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
            when {
                twoPanes -> Row(Modifier.weight(1f)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) { content() }
                    Column(Modifier.width((300f * scale).dp).fillMaxHeight().verticalScroll(rememberScrollState())) { actions() }
                }
                pinned -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) { content() }
                    actions()
                }
                else -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Box(Modifier.padding(20.dp)) { content() }
                    actions()
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = navigatorCardColors()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun DetailValue(label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Endpoint(label: Int, point: TrackPoint?) {
    DetailValue(label, point?.let { stringResource(R.string.detail_coordinates, it.latitude, it.longitude) }
        ?: stringResource(R.string.detail_unavailable))
    Text(elevation(point?.elevation), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun elevation(value: Double?): String = value?.let { stringResource(R.string.metric_elevation_value, it) }
    ?: stringResource(R.string.detail_unavailable)

@Composable
private fun dateTime(value: Instant?): String = value?.atZone(ZoneId.systemDefault())
    ?.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss", Locale.ITALIAN)) ?: stringResource(R.string.detail_unavailable)

@Composable
private fun TrackPreview(detail: TrackDetailUiState, onError: (String) -> Unit) {
    val description = stringResource(R.string.detail_overview)
    val map = detail.map
    DetailSection(description) {
        if (map != null && detail.mapError == null) {
            key(map.file.absolutePath, detail.id) {
                MapView(map, detail.track, null, false, 0L, {}, onError,
                    Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = description })
            }
        } else {
            SketchPreview(detail.sketch)
            Text(stringResource(R.string.detail_schematic), style = MaterialTheme.typography.labelMedium)
            Text(detail.mapError ?: stringResource(R.string.detail_map_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(if (map != null && detail.mapError == null) R.string.detail_overview_legend
            else R.string.detail_sketch_legend), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SketchPreview(sketch: TrackSketch) {
    val palette = LocalNavigatorPalette.current
    val color = Color(palette.map.track.toColorInt())
    val startColor = palette.gpsActive
    val finishColor = palette.danger
    val description = stringResource(R.string.detail_overview)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(24.dp).semantics { contentDescription = description }) {
            val side = minOf(size.width, size.height)
            fun position(point: SketchPoint) = Offset((size.width - side) / 2 + point.x * side,
                (size.height - side) / 2 + point.y * side)
            sketch.segments.forEach { points ->
                if (points.size == 1) drawCircle(color, 4.dp.toPx(), position(points.first()))
                else drawPath(Path().apply {
                    points.forEachIndexed { index, point ->
                        val p = position(point)
                        if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            sketch.segments.firstOrNull()?.firstOrNull()?.let { drawCircle(startColor, 7.dp.toPx(), position(it)) }
            sketch.segments.lastOrNull()?.lastOrNull()?.let { drawCircle(finishColor, 4.dp.toPx(), position(it)) }
        }
    }
}
