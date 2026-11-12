package com.gpxnavigator.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpxnavigator.location.LocationStatus

@Composable
fun NavigationScreen(onBack: () -> Unit, viewModel: NavigationViewModel = viewModel()) {
    NavigationStatusBar()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.loadMap()
        viewModel.prepareMap()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.startLocation()
    }
    fun requestLocationPermission() {
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(viewModel) {
        if (!viewModel.hasLocationPermission()) requestLocationPermission()
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> viewModel.startLocation()
                Lifecycle.Event.ON_STOP -> viewModel.stopLocation()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.startLocation()
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.stopLocation()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadMap(uri)
    }
    NavigationContent(
        state = state,
        onBack = onBack,
        onStopNavigation = viewModel::stopNavigation,
        onChooseMap = { picker.launch(arrayOf("*/*")) },
        onReloadMap = { viewModel.loadMap() },
        onToggleFollow = { viewModel.setFollowingLocation(!state.isFollowingLocation) },
        onMapOrientation = viewModel::setMapOrientation,
        onMapAppearance = viewModel::setMapAppearance,
        onZoomIn = { viewModel.zoomBy(1) },
        onZoomOut = { viewModel.zoomBy(-1) },
        onUserZoomChanged = viewModel::selectZoom,
        onManualCameraMove = { viewModel.setFollowingLocation(false) },
        onRenderError = viewModel::reportRenderError,
        onRetryLocation = {
            if (viewModel.hasLocationPermission()) viewModel.retryLocation()
            else requestLocationPermission()
        },
        onLocationSettings = {
            val intent = if (state.location.status == LocationStatus.DISABLED)
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null))
            context.startActivity(intent)
        },
    )
}
