package com.gpxnavigator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.ui.cockpit.CockpitSummaryScreen
import kotlinx.coroutines.delay

private const val SAVED_MESSAGE_DURATION_MILLIS = 5_000L

@Composable
fun RecordingDecision(onDashboard: () -> Unit, modifier: Modifier = Modifier,
    viewModel: RecordingViewModel = viewModel(), exportViewModel: GpxExportViewModel = viewModel()) {
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.recording_saved)
    val exportText = stringResource(R.string.export_gpx)
    LaunchedEffect(state.dashboardRequest) {
        if (state.dashboardRequest > 0) {
            onDashboard()
            viewModel.dashboardShown()
        }
    }
    LaunchedEffect(state.saved) {
        state.saved?.let { saved ->
            if (snackbar.showSnackbar("$savedMessage ${saved.displayName}", exportText,
                    duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed)
                saved.id?.let(exportViewModel::request)
        }
    }
    LaunchedEffect(state.error, recording.pending) {
        if (!recording.pending) state.error?.let { snackbar.showSnackbar(it) }
    }
    val currentSnackbar = snackbar.currentSnackbarData
    LaunchedEffect(currentSnackbar) {
        if (currentSnackbar?.visuals?.duration == SnackbarDuration.Indefinite) {
            delay(SAVED_MESSAGE_DURATION_MILLIS)
            currentSnackbar.dismiss()
        }
    }
    SnackbarHost(snackbar, modifier)
    if (recording.pending && recording.mode == ActivityMode.COCKPIT) {
        CockpitSummaryScreen(recording, state.busy, state.error,
            onSave = { viewModel.finish(true) }, onDiscard = { viewModel.finish(false) })
    } else if (recording.pending) AlertDialog(onDismissRequest = {},
        title = { Text(stringResource(R.string.save_recording_question)) },
        text = { Column {
            Text(stringResource(if (recording.pointCount == 0) R.string.recording_no_points else R.string.save_recording_description))
            state.error?.let { Text(it) }
            recording.error?.let { Text(it) }
        } },
        confirmButton = { TextButton(onClick = { viewModel.finish(true) },
            enabled = !state.busy && recording.pointCount > 0) { Text(stringResource(R.string.yes_save)) } },
        dismissButton = { TextButton(onClick = { viewModel.finish(false) }, enabled = !state.busy) {
            Text(stringResource(R.string.no_discard))
        } })
}
