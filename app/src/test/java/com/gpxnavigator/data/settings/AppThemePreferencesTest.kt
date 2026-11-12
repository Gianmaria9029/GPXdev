package com.gpxnavigator.data.settings

import android.app.Application
import android.content.Context
import com.gpxnavigator.domain.model.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AppThemePreferencesTest {
    private lateinit var context: Application

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun firstLaunchUsesSoft() = runBlocking {
        assertEquals(AppTheme.SOFT, AppThemePreferences(context).load())
    }

    @Test fun selectedThemeSurvivesStoreRecreationAndCanBeChangedBack() = runBlocking {
        AppThemePreferences(context).save(AppTheme.CYBER)
        assertEquals(AppTheme.CYBER, AppThemePreferences(context).load())
        AppThemePreferences(context).save(AppTheme.SOFT)
        assertEquals(AppTheme.SOFT, AppThemePreferences(context).load())
    }

    @Test fun previouslySavedStandardSelectionLoadsAsSoft() = runBlocking {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("theme", "standard").commit()
        assertEquals(AppTheme.SOFT, AppThemePreferences(context).load())
    }

    @Test fun unknownOrMalformedPreferenceFallsBackToSoft() = runBlocking {
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        preferences.edit().putString("theme", "unavailable-theme").commit()
        assertEquals(AppTheme.SOFT, AppThemePreferences(context).load())
        preferences.edit().putInt("theme", 42).commit()
        assertEquals(AppTheme.SOFT, AppThemePreferences(context).load())
    }
}
