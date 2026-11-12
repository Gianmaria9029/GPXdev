package com.gpxnavigator.data.settings

import android.app.Application
import android.content.Context
import com.gpxnavigator.domain.model.DisplaySettings
import com.gpxnavigator.domain.model.ScreenOrientation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class DisplaySettingsPreferencesTest {
    private lateinit var app: Application

    @Before fun reset() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun firstLaunchUsesAutomaticRotation() = runBlocking {
        assertEquals(DisplaySettings(ScreenOrientation.AUTOMATIC), DisplaySettingsPreferences(app).load())
    }

    @Test fun choicesSurviveRecreationAndCanBeReverted() = runBlocking {
        for (orientation in ScreenOrientation.entries) {
            val chosen = DisplaySettings(orientation)
            DisplaySettingsPreferences(app).save(chosen)
            assertEquals(chosen, DisplaySettingsPreferences(app).load())
        }
        DisplaySettingsPreferences(app).save(DisplaySettings())
        assertEquals(DisplaySettings(), DisplaySettingsPreferences(app).load())
    }

    @Test fun malformedStoredValuesFallBackWithoutCrashing() = runBlocking {
        app.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit()
            .putString("keep_screen_on", "wrong type").putInt("orientation", 42).commit()
        assertEquals(DisplaySettings(), DisplaySettingsPreferences(app).load())
        app.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("keep_screen_on", true).putString("orientation", "unknown").commit()
        assertEquals(DisplaySettings(ScreenOrientation.AUTOMATIC), DisplaySettingsPreferences(app).load())
    }

    @Test fun legacyScreenPreferenceIsIgnoredAndOrientationIsPreserved() = runBlocking {
        for (legacyValue in listOf(false, true)) {
            app.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("keep_screen_on", legacyValue).putString("orientation", "landscape").commit()
            assertEquals(DisplaySettings(ScreenOrientation.LANDSCAPE), DisplaySettingsPreferences(app).load())
        }
    }
}
