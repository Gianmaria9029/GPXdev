package com.gpxnavigator.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GpxExportHost(modifier: Modifier = Modifier, viewModel: GpxExportViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml"),
        viewModel::export)
    LaunchedEffect(state.requestName) {
        state.requestName?.let { name ->
            viewModel.pickerLaunched()
            exporter.launch(name)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    SnackbarHost(snackbar, modifier)
}
