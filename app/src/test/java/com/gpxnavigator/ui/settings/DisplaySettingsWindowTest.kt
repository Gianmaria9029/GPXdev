package com.gpxnavigator.ui.settings

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.gpxnavigator.domain.model.DisplaySettings
import com.gpxnavigator.domain.model.ScreenOrientation
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
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
class DisplaySettingsWindowTest {
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    private fun awake(activity: ComponentActivity) = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    @Test fun screenStaysOnWhileVisibleEvenBeforeSettingsLoadAndIsReleasedWhenHidden() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val settings = MutableStateFlow(DisplaySettingsUiState())
        activity.get().bindDisplaySettings(settings)
        try {
            settle()
            assertTrue(awake(activity.get()))
            settings.value = DisplaySettingsUiState(DisplaySettings()); settle()
            assertTrue(awake(activity.get()))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(15))
            assertTrue(awake(activity.get()))
            activity.pause().stop(); settle()
            assertFalse(awake(activity.get()))
            activity.start().resume(); settle()
            assertTrue(awake(activity.get()))
        } finally { activity.pause().stop().destroy(); settle() }
        assertFalse(awake(activity.get()))
    }

    @Test fun orientationCanBeLockedAndReturnedToPhoneRotation() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val settings = MutableStateFlow(DisplaySettingsUiState(DisplaySettings()))
        activity.get().bindDisplaySettings(settings)
        try {
            settle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.get().requestedOrientation)
            settings.value = DisplaySettingsUiState(DisplaySettings(orientation = ScreenOrientation.LANDSCAPE)); settle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.get().requestedOrientation)
            settings.value = DisplaySettingsUiState(DisplaySettings(orientation = ScreenOrientation.PORTRAIT)); settle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, activity.get().requestedOrientation)
            settings.value = DisplaySettingsUiState(DisplaySettings()); settle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.get().requestedOrientation)
        } finally { activity.pause().stop().destroy(); settle() }
    }
}
