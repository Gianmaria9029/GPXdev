package com.gpxnavigator.ui.navigation

import android.app.NotificationManager
import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.domain.model.MapAppearance
import com.gpxnavigator.domain.model.MapOrientation
import com.gpxnavigator.R
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.OffTrackState
import com.gpxnavigator.navigation.OffTrackStatus
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.map.MapView
import com.gpxnavigator.ui.map.ViewportPadding
import com.gpxnavigator.ui.map.navigationMapPalette
import com.gpxnavigator.ui.theme.LocalAppTheme
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors
import com.gpxnavigator.ui.components.StopNavigationButton
import com.gpxnavigator.ui.components.RecordingIndicator

@Composable
internal fun NavigationContent(
    state: NavigationUiState,
    onBack: () -> Unit,
    onChooseMap: () -> Unit,
    onReloadMap: () -> Unit,
    onToggleFollow: () -> Unit,
    onMapOrientation: (MapOrientation) -> Unit,
    onMapAppearance: (MapAppearance) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onUserZoomChanged: (Double) -> Unit,
    onManualCameraMove: () -> Unit,
    onRenderError: (String) -> Unit,
    onRetryLocation: () -> Unit,
    onLocationSettings: () -> Unit,
    onStopNavigation: () -> Boolean,
) {
    var showingDetails by rememberSaveable { mutableStateOf(false) }
    var confirmingStop by rememberSaveable(state.session.trackId) { mutableStateOf(false) }
    val requestStop: () -> Unit = {
        if (state.session.recordingId != null) onStopNavigation()
        else if (state.session.isRunning) confirmingStop = true
    }
    val notifications = LocalContext.current.getSystemService(NotificationManager::class.java)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = navigationLayout(maxWidth.value, maxHeight.value)
        val metricsMaxHeight = maxHeight * 0.3f
        Column(Modifier.fillMaxSize()) {
            NavigationToolbar(state, layout.landscape, onBack, onMapOrientation, onMapAppearance, requestStop,
                onDetails = { showingDetails = true })
            if (!layout.landscape) {
                PortraitStatus(state, requestStop, { showingDetails = true },
                    notificationsDisabled = state.session.isRunning && !notifications.areNotificationsEnabled())
            } else NavigationAlerts(state, { showingDetails = true },
                state.session.isRunning && !notifications.areNotificationsEnabled())
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    NavigationMap(state, onChooseMap, onToggleFollow, onZoomIn, onZoomOut, onUserZoomChanged,
                        onManualCameraMove, onRenderError, { showingDetails = true },
                        Modifier.fillMaxWidth().weight(1f), landscape = layout.landscape)
                    if (!layout.landscape) MetricsPanel(state, modifier = Modifier.fillMaxWidth()
                        .heightIn(max = metricsMaxHeight))
                }
                if (layout.landscape) LandscapePanel(state,
                    modifier = Modifier.width(layout.sidebarWidthDp.dp).fillMaxHeight())
            }
        }
    }
    if (showingDetails) {
        NavigationDetails(state, onChooseMap, onReloadMap, onRetryLocation,
            onLocationSettings, onDismiss = { showingDetails = false })
    }
    if (confirmingStop && state.session.isRunning && state.session.recordingId == null) {
        AlertDialog(
            onDismissRequest = { confirmingStop = false },
            title = { Text(stringResource(R.string.stop_navigation_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStop = false
                    if (onStopNavigation()) onBack()
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stop_navigation_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStop = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stop_navigation_no))
                }
            },
        )
    }
}

@Composable
private fun PortraitStatus(state: NavigationUiState, onStop: () -> Unit, onDetails: () -> Unit,
    notificationsDisabled: Boolean) {
    Surface(tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(when (state.session.status) {
                SessionStatus.ACTIVE -> if (state.session.trackId == null) R.string.recording_active_status else R.string.navigation_active
                SessionStatus.STARTING -> R.string.navigation_starting
                SessionStatus.INTERRUPTED -> R.string.resume_from_home
                else -> R.string.explore_mode
            }), modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium)
            if (state.session.isRunning) StopNavigationButton(onStop)
        }
    }
    NavigationAlerts(state, onDetails, notificationsDisabled)
}

@Composable
private fun NavigationAlerts(state: NavigationUiState, onDetails: () -> Unit, notificationsDisabled: Boolean) {
    if (notificationsDisabled) Surface(onClick = onDetails,
        color = LocalNavigatorPalette.current.warning, contentColor = Color.White) {
        Text(stringResource(R.string.notifications_disabled_short),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium)
    }
    val error = state.error ?: state.trackError ?: state.session.error ?: state.recording.error
    if (error != null) Surface(onClick = onDetails, color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Text(error, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NavigationToolbar(state: NavigationUiState, landscape: Boolean, onBack: () -> Unit,
    onOrientation: (MapOrientation) -> Unit, onAppearance: (MapAppearance) -> Unit,
    onStop: () -> Unit, onDetails: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_home), stringResource(R.string.home_button))
            }
            Text(state.track?.name ?: state.session.title ?: stringResource(R.string.navigation_title),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            GpsStatus(state.location, onDetails, Modifier.padding(horizontal = 4.dp).widthIn(max = 128.dp))
            if (state.recording.active) RecordingIndicator()
            MapViewSelector(state.mapOrientation, onOrientation,
                state.mapAppearance.takeIf { state.map?.header?.isVector == true }, onAppearance)
            if (landscape && state.session.isRunning) StopNavigationButton(onStop, compact = true)
        }
    }
}

@Composable
private fun NavigationMap(
    state: NavigationUiState, onChooseMap: () -> Unit,
    onToggleFollow: () -> Unit, onZoomIn: () -> Unit, onZoomOut: () -> Unit,
    onUserZoomChanged: (Double) -> Unit, onManualCameraMove: () -> Unit,
    onRenderError: (String) -> Unit, onDetails: () -> Unit, modifier: Modifier, landscape: Boolean,
) {
    val selectedMap = state.map
    val density = LocalDensity.current
    var hudHeight by remember { mutableIntStateOf(0) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    var attributionHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val cueVisible = landscape && !state.isFreeRecording && state.session.status == SessionStatus.ACTIVE && state.track != null
        val portraitHudVisible = !landscape && !state.isFreeRecording && state.track != null
        val cueSize = landscapeManeuverSize(maxWidth.value, maxHeight.value).dp
        val attribution = remember(selectedMap?.attribution) {
            selectedMap?.attribution?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }.orEmpty()
        }
        val overlays = ViewportPadding(
            // Reserve the left edge for the rally card so follow can use the full height beside it.
            left = if (cueVisible) with(density) { (cueSize + 16.dp).toPx() }.toDouble() else 0.0,
            top = if (landscape) controlsHeight + with(density) { 16.dp.toPx() }.toDouble()
                else (if (portraitHudVisible) hudHeight else 0) + with(density) { 20.dp.toPx() }.toDouble(),
            bottom = if (landscape) (if (attribution.isNotBlank()) attributionHeight else 0) +
                with(density) { 12.dp.toPx() }.toDouble()
                else controlsHeight + with(density) { 16.dp.toPx() }.toDouble())
        when {
            state.loading -> Column(Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading_map), modifier = Modifier.padding(16.dp))
            }
            selectedMap == null -> Column(Modifier.align(Alignment.Center)
                .verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.no_map), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onChooseMap, colors = navigatorButtonColors(), border = navigatorButtonBorder()) {
                    Text(stringResource(R.string.choose_map))
                }
            }
            else -> {
                val mapPalette = if (selectedMap.header.isVector) navigationMapPalette(state.mapAppearance)
                    else LocalNavigatorPalette.current.map
                key(selectedMap.file.absolutePath) {
                    MapView(selectedMap, state.track, state.location.point,
                        state.isFollowingLocation, state.centerRequestId,
                        onManualCameraMove, onRenderError, Modifier.fillMaxSize(), state.mapOrientation,
                        userZoom = state.userZoom, zoomStepCount = state.zoomStepCount,
                        onUserZoomChanged = onUserZoomChanged, overlayInsets = overlays,
                        trackOverlayPosition = state.trackOverlayPosition,
                        recordingId = state.recording.id, recordingChunks = state.recording.chunks,
                        colors = mapPalette)
                }
                if (portraitHudVisible) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp)
                    .onSizeChanged { hudHeight = it.height },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.session.status == SessionStatus.ACTIVE) {
                        ManeuverBox(state.guidance)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OffTrackNotice(state.offTrack, onDetails)
                    }
                }
                if (cueVisible) ManeuverBox(state.guidance,
                    Modifier.align(Alignment.BottomStart).padding(8.dp), prominent = true, boxSize = cueSize)
                MapControls(state.isFollowingLocation, landscape,
                    onZoomIn, onZoomOut, onToggleFollow,
                    Modifier.align(if (landscape) Alignment.TopEnd else Alignment.BottomEnd).padding(8.dp)
                        .onSizeChanged { controlsHeight = it.height })
                if (attribution.isNotBlank()) {
                    val attributionMaxWidth = if (cueVisible) minOf(160.dp, maxWidth - cueSize - 24.dp) else 160.dp
                    Surface(modifier = Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomStart)
                        .padding(6.dp).widthIn(max = attributionMaxWidth).onSizeChanged { attributionHeight = it.height },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp)) {
                        Text(attribution, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapViewSelector(orientation: MapOrientation, onOrientation: (MapOrientation) -> Unit,
    appearance: MapAppearance?, onAppearance: (MapAppearance) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalNavigatorPalette.current
    val orientationLabel = stringResource(if (orientation == MapOrientation.NORTH_UP)
        R.string.map_north_up else R.string.map_course_up)
    val description = if (appearance == null) stringResource(R.string.map_view_description, orientationLabel)
        else stringResource(R.string.map_view_appearance_description, orientationLabel,
            stringResource(appearance.labelResource))
    Box {
        OutlinedButton(onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LocalNavigatorPalette.current.selectionBorder),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.viewBackground,
                contentColor = palette.onView)) {
            Text(stringResource(R.string.map_view_button), maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_dropdown), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapOrientation.entries.forEach { mode ->
                MapViewMenuItem(stringResource(if (mode == MapOrientation.NORTH_UP)
                    R.string.map_north_short else R.string.map_course_up), orientation == mode) {
                    expanded = false
                    onOrientation(mode)
                }
            }
            if (appearance != null) {
                HorizontalDivider()
                MapAppearance.entries.forEach { mode ->
                    MapViewMenuItem(stringResource(mode.labelResource), appearance == mode, useMapAccent = true) {
                        expanded = false
                        onAppearance(mode)
                    }
                }
            }
        }
    }
}

private val MapAppearance.labelResource: Int get() = when (this) {
    MapAppearance.LIGHT -> R.string.map_appearance_light
    MapAppearance.DARK -> R.string.map_appearance_dark
}

@Composable
private fun MapViewMenuItem(label: String, active: Boolean, useMapAccent: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val softTheme = LocalAppTheme.current == AppTheme.SOFT
    val highlightedMapAppearance = active && useMapAccent
    val background = when {
        !active -> Color.Transparent
        !useMapAccent -> scheme.primaryContainer
        softTheme -> scheme.secondaryContainer
        else -> scheme.secondary
    }
    val accentText = if (softTheme) scheme.onSecondaryContainer else scheme.onSecondary
    val outline = if (highlightedMapAppearance && softTheme)
        Modifier.border(1.dp, LocalNavigatorPalette.current.selectionBorder) else Modifier
    DropdownMenuItem(text = { Text(label, color = if (highlightedMapAppearance) accentText else Color.Unspecified) },
        trailingIcon = { if (active) Text("✓", color = if (useMapAccent) accentText else scheme.primary) },
        modifier = Modifier.background(background).then(outline)
            .semantics { selected = active },
        onClick = onClick)
}

@Composable
private fun OffTrackNotice(state: OffTrackState, onDetails: () -> Unit) {
    if (state.status != OffTrackStatus.OFF_TRACK && state.status != OffTrackStatus.CHECKING) return
    val confirmed = state.status == OffTrackStatus.OFF_TRACK
    val palette = LocalNavigatorPalette.current
    Surface(onClick = onDetails, modifier = Modifier.widthIn(max = 280.dp),
        color = if (confirmed) MaterialTheme.colorScheme.errorContainer
            else palette.warning,
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(stringResource(offTrackMessage(state.status)),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            state.distanceMeters?.let { distance ->
                Text(distanceFromTrackText(distance), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun distanceFromTrackText(distance: Double): String =
    if (distance < 1_000) stringResource(R.string.off_track_distance_meters, distance)
    else stringResource(R.string.off_track_distance_kilometers, distance / 1_000)

private fun offTrackMessage(status: OffTrackStatus): Int = when (status) {
    OffTrackStatus.UNAVAILABLE -> R.string.off_track_unavailable
    OffTrackStatus.ON_TRACK -> R.string.off_track_on_route
    OffTrackStatus.CHECKING -> R.string.off_track_checking
    OffTrackStatus.OFF_TRACK -> R.string.off_track_alert
}

@Composable
private fun GpsStatus(location: LocationState, onClick: () -> Unit, modifier: Modifier) {
    val palette = LocalNavigatorPalette.current
    val point = location.point
    val active = point != null
    val message = if (active && point?.accuracyMeters != null)
        stringResource(R.string.gps_status_accuracy, point.accuracyMeters)
    else if (active) stringResource(R.string.gps_status_active)
    else stringResource(locationMessage(location.status))
    val statusColor = when {
        active -> palette.gpsActive
        location.status == LocationStatus.DENIED || location.status == LocationStatus.DISABLED ||
            location.status == LocationStatus.ERROR -> palette.danger
        else -> palette.warning
    }
    val compactMessage = when {
        active -> message
        else -> stringResource(when (location.status) {
            LocationStatus.DISABLED -> R.string.gps_short_disabled
            LocationStatus.DENIED -> R.string.gps_short_denied
            LocationStatus.ERROR -> R.string.gps_short_error
            LocationStatus.STOPPED -> R.string.gps_short_stopped
            LocationStatus.UNAVAILABLE -> R.string.gps_short_unavailable
            else -> R.string.gps_short_waiting
        })
    }
    Surface(onClick = onClick, modifier = modifier, color = statusColor, contentColor = Color.White,
        shape = RoundedCornerShape(8.dp)) {
        Text(compactMessage, Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetricsPanel(state: NavigationUiState, modifier: Modifier) {
    if (state.isFreeRecording) {
        FreeRecordingMetrics(state, modifier)
        return
    }
    val progress = state.progress
    val unavailable = stringResource(R.string.metric_unavailable)
    val distances = listOf(
        stringResource(R.string.metric_total_label) to progress?.totalDistanceMeters,
        stringResource(R.string.metric_travelled_label) to progress?.travelledDistanceMeters,
        stringResource(R.string.metric_remaining_label) to progress?.remainingDistanceMeters,
    )
    val speed = state.location.point?.speedMetersPerSecond
    val elevation = state.location.point?.elevationMeters
    Surface(modifier, tonalElevation = 2.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                distances.forEach { (label, value) ->
                    MetricCell(label, value?.let { stringResource(R.string.metric_distance_value, it / 1_000) }
                        ?: unavailable, Modifier.weight(1f))
                }
            }
            if (state.session.status == SessionStatus.ACTIVE) {
                Text(arrivalEstimateText(LocalContext.current, state.arrival),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(stringResource(R.string.metric_speed_label),
                    speed?.let { stringResource(R.string.metric_speed_value, it * 3.6f) } ?: unavailable,
                    Modifier.weight(1f))
                MetricCell(stringResource(R.string.metric_elevation_label),
                    elevation?.let { stringResource(R.string.metric_elevation_value, it) } ?: unavailable,
                    Modifier.weight(1f))
            }
            if (progress != null && state.location.point != null && progress.travelledDistanceMeters == null) {
                Text(stringResource(R.string.progress_not_available), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FreeRecordingMetrics(state: NavigationUiState, modifier: Modifier) {
    val unavailable = stringResource(R.string.metric_unavailable)
    Surface(modifier, tonalElevation = 2.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(stringResource(R.string.metric_travelled_tile_label),
                    stringResource(R.string.metric_distance_value, state.recording.distanceMeters / 1_000), Modifier.weight(1f))
                MetricCell(stringResource(R.string.metric_recording_time_label),
                    recordingElapsedText(state.recordingElapsedMillis) ?: unavailable, Modifier.weight(1f))
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(stringResource(R.string.metric_speed_label),
                    state.location.point?.speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0 }
                        ?.let { stringResource(R.string.metric_speed_value, it * 3.6f) } ?: unavailable, Modifier.weight(1f))
                MetricCell(stringResource(R.string.metric_elevation_label),
                    state.location.point?.elevationMeters?.takeIf { it.isFinite() }
                        ?.let { stringResource(R.string.metric_elevation_value, it) } ?: unavailable, Modifier.weight(1f))
            }
            Text(stringResource(R.string.metric_maximum_speed, state.maximumSpeedMetersPerSecond?.takeIf { it.isFinite() && it >= 0 }
                ?.let { stringResource(R.string.metric_speed_number, it * 3.6f) } ?: unavailable),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NavigationDetails(
    state: NavigationUiState, onChooseMap: () -> Unit, onReloadMap: () -> Unit,
    onRetryLocation: () -> Unit, onLocationSettings: () -> Unit, onDismiss: () -> Unit,
) {
    val notifications = LocalContext.current.getSystemService(NotificationManager::class.java)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.navigation_details_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.map?.name ?: stringResource(R.string.no_map))
                if (state.session.isRunning && !notifications.areNotificationsEnabled()) {
                    Text(stringResource(R.string.notifications_disabled), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onDismiss(); onChooseMap() }, enabled = !state.loading,
                    colors = navigatorButtonColors(), border = navigatorButtonBorder(),
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_map)) }
                TextButton(onClick = { onDismiss(); onReloadMap() }, enabled = !state.loading) {
                    Text(stringResource(R.string.reload_map))
                }
                HorizontalDivider()
                Text(state.track?.name ?: stringResource(R.string.no_track))
                Text(stringResource(when (state.session.status) {
                    SessionStatus.ACTIVE -> if (state.session.trackId == null) R.string.recording_active_status else R.string.navigation_active
                    SessionStatus.STARTING -> R.string.navigation_starting
                    SessionStatus.INTERRUPTED -> R.string.resume_from_home
                    else -> R.string.explore_mode
                }))
                if (state.session.status == SessionStatus.ACTIVE) {
                    Text(arrivalEstimateText(LocalContext.current, state.arrival))
                    state.arrival.referenceSpeedMetersPerSecond?.let { speed ->
                        Text(stringResource(R.string.arrival_reference_speed, speed * 3.6),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.track != null) {
                    val unavailable = stringResource(R.string.metric_unavailable)
                    MetricCell(stringResource(R.string.metric_total_label),
                        state.progress?.totalDistanceMeters?.let { stringResource(R.string.metric_distance_value, it / 1_000) }
                            ?: unavailable)
                    MetricCell(stringResource(R.string.metric_travelled_label),
                        state.progress?.travelledDistanceMeters?.let { stringResource(R.string.metric_distance_value, it / 1_000) }
                            ?: unavailable)
                    if (state.progress != null && state.location.point != null &&
                        state.progress.travelledDistanceMeters == null) {
                        Text(stringResource(R.string.progress_not_available), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(offTrackMessage(state.offTrack.status)))
                    state.offTrack.distanceMeters?.let { Text(distanceFromTrackText(it)) }
                }
                HorizontalDivider()
                Text(stringResource(locationMessage(state.location.status)))
                val point = state.location.point
                if (point != null) {
                    Text(stringResource(R.string.location_coordinates, point.latitude, point.longitude))
                    if (!state.location.precise) Text(stringResource(R.string.location_approximate))
                    point.accuracyMeters?.let { Text(stringResource(R.string.location_accuracy, it)) }
                    point.bearingDegrees?.let { Text(stringResource(R.string.location_movement_bearing, it)) }
                    MetricCell(stringResource(R.string.metric_elevation_label),
                        point.elevationMeters?.let { stringResource(R.string.metric_elevation_value, it) }
                            ?: stringResource(R.string.metric_unavailable))
                } else {
                    TextButton(onClick = onRetryLocation) { Text(stringResource(R.string.retry_location)) }
                    if (state.location.status == LocationStatus.DENIED || state.location.status == LocationStatus.DISABLED) {
                        TextButton(onClick = onLocationSettings) { Text(stringResource(R.string.location_settings)) }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.trackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_details)) } },
    )
}

private fun locationMessage(status: LocationStatus): Int = when (status) {
    LocationStatus.STOPPED -> R.string.location_stopped
    LocationStatus.WAITING -> R.string.location_waiting
    LocationStatus.UNAVAILABLE -> R.string.location_unavailable
    LocationStatus.DISABLED -> R.string.location_disabled
    LocationStatus.DENIED -> R.string.location_denied
    LocationStatus.ERROR -> R.string.location_error
    LocationStatus.ACTIVE -> R.string.gps_status_active
}
