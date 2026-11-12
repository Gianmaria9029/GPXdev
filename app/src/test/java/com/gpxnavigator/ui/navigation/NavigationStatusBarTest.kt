package com.gpxnavigator.ui.navigation

import android.app.Application
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Suppress("DEPRECATION")
class NavigationStatusBarTest {
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    private fun hidden(activity: ComponentActivity, flag: Int) =
        activity.window.decorView.systemUiVisibility and flag != 0

    @Test fun navigationHidesStatusBarInBothOrientationsAndLeavingRestoresIt() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val orientation = mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
        val showingNavigation = mutableStateOf(false)
        try {
            activity.get().setContent {
                val configuration = Configuration(LocalConfiguration.current).apply { this.orientation = orientation.value }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    if (showingNavigation.value) NavigationStatusBar()
                    Box(Modifier.fillMaxSize())
                }
            }
            settle()
            assertFalse(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            showingNavigation.value = true; settle()
            assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            orientation.value = Configuration.ORIENTATION_LANDSCAPE; settle()
            assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY))
            assertFalse(hidden(activity.get(), View.SYSTEM_UI_FLAG_HIDE_NAVIGATION))
            orientation.value = Configuration.ORIENTATION_PORTRAIT; settle()
            assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            orientation.value = Configuration.ORIENTATION_LANDSCAPE; settle()
            assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            showingNavigation.value = false; settle()
            assertFalse(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            assertFalse(hidden(activity.get(), View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY))
            showingNavigation.value = true
            orientation.value = Configuration.ORIENTATION_PORTRAIT; settle()
            assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            showingNavigation.value = false; settle()
            assertFalse(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
        } finally { activity.pause().stop().destroy(); settle() }
    }

    @Test fun returningFromBackgroundOrDialogReappliesStatusBarHidingInBothOrientations() {
        for (orientation in listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                activity.get().setContent {
                    val configuration = Configuration(LocalConfiguration.current).apply {
                        this.orientation = orientation
                    }
                    CompositionLocalProvider(LocalConfiguration provides configuration) {
                        NavigationStatusBar()
                        Box(Modifier.fillMaxSize())
                    }
                }
                settle()
                assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
                activity.pause().stop(); settle()
                assertFalse(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
                activity.start().resume(); settle()
                assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
                val decor = activity.get().window.decorView
                decor.systemUiVisibility = decor.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
                activity.windowFocusChanged(true)
                settle()
                assertTrue(hidden(activity.get(), View.SYSTEM_UI_FLAG_FULLSCREEN))
            } finally { activity.pause().stop().destroy(); settle() }
        }
    }
}
