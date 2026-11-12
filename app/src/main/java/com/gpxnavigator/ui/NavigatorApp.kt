package com.gpxnavigator.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.ui.components.RecordingDecision
import com.gpxnavigator.ui.components.GpxExportHost
import com.gpxnavigator.ui.components.RecordingViewModel
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.navigation.SessionStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpxnavigator.ui.importgpx.ImportGpxScreen
import com.gpxnavigator.ui.navigation.NavigationScreen
import com.gpxnavigator.ui.cockpit.CockpitScreen
import com.gpxnavigator.ui.theme.NavigatorTheme
import com.gpxnavigator.ui.theme.ThemeViewModel
import kotlinx.coroutines.delay

private const val STARTUP_LOGO_DURATION_MILLIS = 2_000L
private enum class AppPage { HOME, MAP, COCKPIT }

@Composable
fun NavigatorApp(openNavigationRequest: Int = 0, themeViewModel: ThemeViewModel = viewModel()) {
    val appearance by themeViewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(AppPage.HOME) }
    var handledRequest by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }

    val theme = appearance.theme ?: return
    // Keep the original deadline across Activity recreation (for example, rotation).
    val startupDeadline by rememberSaveable {
        mutableLongStateOf(SystemClock.elapsedRealtime() + STARTUP_LOGO_DURATION_MILLIS)
    }
    var startupFinished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((startupDeadline - SystemClock.elapsedRealtime()).coerceIn(0L, STARTUP_LOGO_DURATION_MILLIS))
        startupFinished = true
    }

    BackHandler(enabled = page != AppPage.HOME && startupFinished) {
        page = AppPage.HOME
    }

    NavigatorTheme(theme) {
        Surface(modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!startupFinished) {
                    Image(
                        painterResource(if (theme == AppTheme.SOFT)
                            R.drawable.gpxdev_logo_soft else R.drawable.gpxdev_logo_cyber),
                        contentDescription = stringResource(R.string.dashboard_logo_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                            .sizeIn(maxWidth = 320.dp, maxHeight = 160.dp),
                    )
                } else {
                    val recordingViewModel: RecordingViewModel = viewModel()
                    val session by recordingViewModel.session.collectAsStateWithLifecycle()
                    val recording by recordingViewModel.recording.collectAsStateWithLifecycle()
                    LaunchedEffect(openNavigationRequest, session.status, session.mode, recording.mode) {
                        if (openNavigationRequest > handledRequest && session.status != SessionStatus.RESTORING) {
                            page = if (session.mode == ActivityMode.COCKPIT || (recording.pending && recording.mode == ActivityMode.COCKPIT))
                                AppPage.COCKPIT else AppPage.MAP
                            handledRequest = openNavigationRequest
                        }
                    }
                    val openMap = { page = AppPage.MAP }
                    val openCockpit = { page = AppPage.COCKPIT }
                    val pendingCockpit = recording.pending && recording.mode == ActivityMode.COCKPIT
                    if (!pendingCockpit) when (page) {
                        AppPage.MAP -> if (session.mode == ActivityMode.COCKPIT && session.isRunning)
                            CockpitScreen(onBack = { page = AppPage.HOME })
                            else NavigationScreen(onBack = { page = AppPage.HOME })
                        AppPage.COCKPIT -> CockpitScreen(onBack = { page = AppPage.HOME })
                        AppPage.HOME -> ImportGpxScreen(onOpenNavigation = openMap,
                            theme = theme, onThemeChange = themeViewModel::select,
                            themeSaveFailed = appearance.saveFailed, onOpenCockpit = openCockpit)
                    }
                    RecordingDecision(onDashboard = { page = AppPage.HOME }, Modifier.align(Alignment.BottomCenter), viewModel = recordingViewModel)
                    GpxExportHost(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
