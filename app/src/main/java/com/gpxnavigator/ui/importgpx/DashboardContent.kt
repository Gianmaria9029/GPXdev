package com.gpxnavigator.ui.importgpx

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpxnavigator.ui.theme.navigatorCardColors
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.theme.LocalAppTheme
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors

internal data class HomeActions(
    val onTracks: () -> Unit,
    val onExplore: () -> Unit,
    val onFreeRecording: () -> Unit,
    val onSettings: () -> Unit,
    val onResume: () -> Unit,
    val onDismissSession: () -> Unit,
    val onCockpit: () -> Unit = {},
    val onStop: () -> Unit = {},
)

@Composable
internal fun DashboardContent(state: ImportGpxUiState, awaitingPermission: Boolean,
    actions: HomeActions, footer: @Composable () -> Unit) {
    val palette = LocalNavigatorPalette.current
    val ready = state.catalogLoaded && !state.loading && !state.working && !awaitingPermission
    val canStart = ready && state.session.status == SessionStatus.IDLE && state.recording.id == null
    val cockpitActivity = state.session.mode == ActivityMode.COCKPIT &&
        (state.session.isRunning || state.session.status == SessionStatus.INTERRUPTED)
    val introduction: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(if (LocalAppTheme.current == AppTheme.SOFT)
                R.drawable.gpxdev_logo_soft else R.drawable.gpxdev_logo_cyber),
                stringResource(R.string.dashboard_logo_description), contentScale = ContentScale.Crop,
                modifier = Modifier.width(180.dp).height(64.dp))
            Text(stringResource(R.string.home_welcome), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.home_intro), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val choices: @Composable (Boolean) -> Unit = { compact ->
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            ActivityCard(R.drawable.ic_recording_route, R.string.home_follow, R.string.home_follow_hint,
                palette.action, palette.onAction, actions.onTracks, enabled = !state.working, compact = compact)
            ActivityCard(R.drawable.ic_recording_time, R.string.home_record, R.string.home_record_hint,
                palette.recordAction, palette.onRecordAction, actions.onFreeRecording, enabled = canStart, compact = compact)
            ActivityCard(R.drawable.ic_cockpit, R.string.cockpit_title, R.string.cockpit_home_hint,
                palette.followActive, palette.onFollowActive, actions.onCockpit,
                enabled = ready && !state.recording.pending && (canStart || cockpitActivity), compact = compact)
            ActivityCard(R.drawable.ic_explore_map, R.string.explore_map, R.string.home_explore_hint,
                MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
                actions.onExplore, enabled = !state.working && !cockpitActivity, compact = compact)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPanes = dashboardLayout(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale).twoPanes
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!twoPanes) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = actions.onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(22.dp))
                        Text(stringResource(R.string.settings_title), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (!twoPanes && (state.session.isRunning || state.session.status == SessionStatus.INTERRUPTED)) item {
                ActiveSessionCard(state, ready, actions)
            }
            if (state.loading || state.working || state.error != null || state.session.error != null)
                item { CatalogFeedback(state) }
            if (twoPanes) item {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.session.isRunning || state.session.status == SessionStatus.INTERRUPTED)
                            ActiveSessionCard(state, ready, actions) else introduction()
                        TextButton(onClick = actions.onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(22.dp))
                            Text(stringResource(R.string.settings_title), Modifier.padding(start = 8.dp))
                        }
                    }
                    Box(Modifier.weight(1.15f)) { choices(true) }
                }
            } else {
                item { introduction() }
                item { choices(false) }
            }
            item { footer() }
        }
    }
}

@Composable
private fun ActivityCard(icon: Int, title: Int, description: Int, accent: Color, onAccent: Color,
    onClick: () -> Unit, enabled: Boolean, compact: Boolean) {
    OutlinedCard(onClick = onClick, enabled = enabled, colors = navigatorCardColors(), shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 72.dp else 104.dp)) {
        Row(Modifier.padding(if (compact) 10.dp else 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
            Surface(shape = CircleShape,
                color = if (enabled) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (enabled) onAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) {
                Icon(painterResource(icon), null, Modifier.padding(if (compact) 10.dp else 12.dp).size(if (compact) 24.dp else 26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(title), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ActiveSessionCard(state: ImportGpxUiState, ready: Boolean, actions: HomeActions) {
    val interrupted = state.session.status == SessionStatus.INTERRUPTED
    val cockpit = state.session.mode == ActivityMode.COCKPIT
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (cockpit) {
                if (interrupted) R.string.cockpit_interrupted else R.string.cockpit_running
            } else if (interrupted) R.string.home_interrupted else if (state.session.trackId == null)
                R.string.home_recording_active else R.string.home_navigation_active),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(state.session.title ?: stringResource(R.string.home_record), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = if (interrupted) actions.onResume else if (cockpit) actions.onCockpit else actions.onExplore,
                enabled = ready || (!interrupted && !state.working), colors = navigatorButtonColors(), border = navigatorButtonBorder(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(if (cockpit) R.string.cockpit_return else if (interrupted) R.string.resume_navigation else R.string.home_return_map))
            }
            if (interrupted) TextButton(onClick = actions.onDismissSession, enabled = ready) {
                Text(stringResource(if (cockpit) R.string.activity_stop else R.string.discard_session))
            }
            else TextButton(onClick = actions.onStop, enabled = !state.working) {
                Text(stringResource(R.string.activity_stop))
            }
        }
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back_button))
            }
            Text(title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}

@Composable
internal fun CatalogFeedback(state: ImportGpxUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.loading || state.working) Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.catalog_working))
        }
        (state.error ?: state.session.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
