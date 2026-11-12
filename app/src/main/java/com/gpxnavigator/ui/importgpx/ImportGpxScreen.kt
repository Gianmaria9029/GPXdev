package com.gpxnavigator.ui.importgpx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.ui.cockpit.CockpitSetupScreen
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.components.ChangelogDialog
import com.gpxnavigator.ui.components.GpxExportViewModel
import com.gpxnavigator.ui.settings.SettingsScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class HomePage { HOME, TRACKS, DETAIL, RECORDING, COCKPIT, SETTINGS }

@Composable
fun ImportGpxScreen(onOpenNavigation: () -> Unit, theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit, themeSaveFailed: Boolean,
    viewModel: ImportGpxViewModel = viewModel(), exportViewModel: GpxExportViewModel = viewModel(),
    onOpenCockpit: () -> Unit = onOpenNavigation) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(HomePage.HOME) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingChangelog by rememberSaveable { mutableStateOf(false) }
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    var deletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalogScroll = rememberLazyListState()
    val catalogScope = rememberCoroutineScope()
    fun resetCatalogScroll() { catalogScope.launch { catalogScroll.scrollToItem(0) } }
    var pendingTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    var pendingRecording by rememberSaveable { mutableStateOf(false) }
    var pendingRecordingName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCockpit by rememberSaveable { mutableStateOf(false) }
    var recordingName by rememberSaveable { mutableStateOf("") }
    val suggestedRecordingName = stringResource(R.string.recording_setup_default_name,
        LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)))
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = androidx.compose.ui.platform.LocalContext.current
    fun openActivity(cockpit: Boolean) {
        page = HomePage.HOME
        detailId = null
        viewModel.closeDetails()
        if (cockpit) onOpenCockpit() else onOpenNavigation()
    }
    fun openMap() {
        if (state.session.mode == ActivityMode.COCKPIT && state.session.status != SessionStatus.IDLE)
            viewModel.reportActivityConflict()
        else openActivity(false)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val id = pendingTrackId
        val shouldRecord = pendingRecording
        val requestedName = pendingRecordingName
        val requested = awaitingPermission
        val cockpit = pendingCockpit
        awaitingPermission = false
        pendingTrackId = null
        pendingRecording = false
        pendingRecordingName = null
        pendingCockpit = false
        if (requested && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (viewModel.hasLocationPermission() && (!shouldRecord ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                if (viewModel.startNavigation(id, shouldRecord, requestedName,
                    if (cockpit) ActivityMode.COCKPIT else ActivityMode.forTrack(id))) openActivity(cockpit)
            } else viewModel.reportPermissionDenied(shouldRecord)
        }
    }
    fun startNavigation(id: String?, record: Boolean, name: String? = null, cockpit: Boolean = false) {
        val mode = if (cockpit) ActivityMode.COCKPIT else ActivityMode.forTrack(id)
        if (state.session.status != SessionStatus.IDLE && state.session.status != SessionStatus.RESTORING && state.session.mode != mode) {
            viewModel.reportActivityConflict()
            return
        }
        val permissions = buildList {
            if (!viewModel.hasLocationPermission() || (record &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isEmpty()) {
            if (viewModel.startNavigation(id, record, name, mode)) openActivity(cockpit)
        } else {
            pendingTrackId = id
            pendingRecording = record
            pendingRecordingName = name
            pendingCockpit = cockpit
            awaitingPermission = true
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
    val enabled = state.catalogLoaded && !state.loading && !state.working
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    fun back() {
        awaitingPermission = false
        pendingTrackId = null
        pendingRecording = false
        pendingRecordingName = null
        pendingCockpit = false
        page = if (page == HomePage.DETAIL) HomePage.TRACKS else HomePage.HOME
    }
    BackHandler(enabled = page != HomePage.HOME) { back() }
    LaunchedEffect(page, detailId) {
        val id = detailId
        if (page == HomePage.DETAIL && id != null) {
            if (detail.id != id) viewModel.openDetails(id)
        } else viewModel.closeDetails()
    }
    LaunchedEffect(state.tracks, state.catalogLoaded, state.working, page) {
        if (page == HomePage.DETAIL && state.catalogLoaded && !state.working &&
            state.tracks.none { it.id == detailId }) {
            page = HomePage.TRACKS
            detailId = null
        }
    }
    val footer: @Composable () -> Unit = { DashboardFooter(onChangelog = { showingChangelog = true }) }
    when (page) {
        HomePage.HOME -> DashboardContent(state, awaitingPermission, HomeActions(
            onTracks = { page = HomePage.TRACKS },
            onExplore = ::openMap,
            onFreeRecording = { recordingName = suggestedRecordingName; page = HomePage.RECORDING },
            onSettings = { page = HomePage.SETTINGS },
            onResume = { startNavigation(state.session.trackId, state.session.recordingId != null || state.recordGpx,
                cockpit = state.session.mode == ActivityMode.COCKPIT) },
            onDismissSession = viewModel::dismissInterrupted,
            onCockpit = {
                if (state.session.mode == ActivityMode.COCKPIT && state.session.isRunning) openActivity(true)
                else if (state.session.mode == ActivityMode.COCKPIT && state.session.status == SessionStatus.INTERRUPTED)
                    startNavigation(null, true, cockpit = true)
                else if (state.session.status != SessionStatus.IDLE) viewModel.reportActivityConflict()
                else {
                    recordingName = suggestedRecordingName
                    page = HomePage.COCKPIT
                }
            },
            onStop = {
                if (state.session.recordingId != null) viewModel.stopNavigation()
                else confirmingStop = true
            },
        ), footer)
        HomePage.TRACKS -> CatalogScreen(state, catalogScroll, ::back, { picker.launch(arrayOf("*/*")) },
            onTrack = { detailId = it; page = HomePage.DETAIL }, onRetry = viewModel::reload,
            onQuery = { viewModel.setQuery(it); resetCatalogScroll() },
            onFavoritesOnly = { viewModel.setFavoritesOnly(it); resetCatalogScroll() },
            onSort = { viewModel.setSortOrder(it); resetCatalogScroll() }, onFavorite = viewModel::setFavorite,
            onClearFilters = { viewModel.clearFilters(); resetCatalogScroll() })
        HomePage.DETAIL -> TrackDetailScreen(state.tracks.firstOrNull { it.id == detailId },
            detail.takeIf { it.id == detailId } ?: TrackDetailUiState(detailId, loading = true), state,
            awaitingPermission, onBack = ::back,
            onStart = { detailId?.let { startNavigation(it, state.recordGpx) } },
            onToggleRecording = viewModel::toggleRecording, onOpenNavigation = ::openMap,
            onExport = { detailId?.let(exportViewModel::request) }, onDelete = { deletionId = detailId },
            onRetry = { detailId?.let(viewModel::openDetails) }, onPreviewError = viewModel::reportPreviewError,
            onRename = { viewModel.clearError(); renamingId = detailId },
            onFavorite = { favorite -> detailId?.let { viewModel.setFavorite(it, favorite) } })
        HomePage.RECORDING -> FreeRecordingSetupScreen(recordingName, { recordingName = it }, ::back,
            onStart = { startNavigation(null, true, recordingName) },
            enabled = enabled && state.session.status == SessionStatus.IDLE && state.recording.id == null,
            awaitingPermission = awaitingPermission, error = state.session.error ?: state.error)
        HomePage.COCKPIT -> {
            CockpitSetupScreen(recordingName, { recordingName = it }, ::back,
                onStart = { startNavigation(null, true, recordingName, cockpit = true) },
                enabled = enabled && state.session.status == SessionStatus.IDLE && state.recording.id == null,
                awaitingPermission = awaitingPermission, error = state.session.error ?: state.error)
        }
        HomePage.SETTINGS -> SettingsScreen(theme, onThemeChange, themeSaveFailed, ::back, footer)
    }
    if (showingChangelog) ChangelogDialog(onDismiss = { showingChangelog = false })
    if (confirmingStop) AlertDialog(onDismissRequest = { confirmingStop = false },
        title = { Text(stringResource(R.string.stop_navigation_confirmation)) },
        confirmButton = { TextButton(onClick = { confirmingStop = false; viewModel.stopNavigation() }) { Text(stringResource(R.string.stop_navigation_yes)) } },
        dismissButton = { TextButton(onClick = { confirmingStop = false }) { Text(stringResource(R.string.stop_navigation_no)) } })
    val deleting = state.tracks.firstOrNull { it.id == deletionId }
    val renaming = state.tracks.firstOrNull { it.id == renamingId }
    if (renaming != null) RenameTrackDialog(renaming, state.working, canRenameTrack(state, renaming.id), state.error,
        onDismiss = { renamingId = null; viewModel.clearError() },
        onSave = { name -> viewModel.rename(renaming.id, name) { renamingId = null } })
    if (deleting != null) AlertDialog(onDismissRequest = { deletionId = null },
        title = { Text(stringResource(R.string.delete_track_title)) },
        text = { Text(stringResource(if (deleting.id == state.selectedTrackId)
            R.string.delete_selected_track_message else R.string.delete_track_message, deleting.title)) },
        confirmButton = {
            TextButton(onClick = { viewModel.delete(deleting.id); deletionId = null },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                enabled = enabled && (!state.session.isRunning || deleting.id != state.session.trackId)) {
                Text(stringResource(R.string.delete_track))
            }
        },
        dismissButton = { TextButton(onClick = { deletionId = null }) { Text(stringResource(R.string.cancel)) } },
    )
}
