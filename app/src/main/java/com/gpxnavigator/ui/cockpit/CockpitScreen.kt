package com.gpxnavigator.ui.cockpit

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.navigation.NavigationStatusBar

@Composable
fun CockpitScreen(onBack: () -> Unit, viewModel: CockpitViewModel = viewModel()) {
    NavigationStatusBar()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.session.status, state.session.mode) {
        if ((state.session.isRunning && state.session.mode != ActivityMode.COCKPIT) ||
            state.session.status == SessionStatus.INTERRUPTED) onBack()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.setVisible(true)
    }
    val retry: () -> Unit = {
        if (viewModel.hasLocationPermission()) viewModel.retryLocation()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.setVisible(true)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }
    CockpitContent(state, onBack, viewModel::stop, retry) {
        context.startActivity(if (state.location.status == LocationStatus.DISABLED)
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}
