package com.gpxnavigator.data.settings

import android.app.Application
import android.content.Context
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
class MapZoomPreferencesTest {
    private lateinit var context: Application
    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("navigation_map", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun fractionalZoomPersistsWithoutChangingOrientation() = runBlocking {
        val preferences = context.getSharedPreferences("navigation_map", Context.MODE_PRIVATE)
        preferences.edit().putString("orientation", "course").commit()
        assertNull(MapZoomPreferences(context).load())
        MapZoomPreferences(context).save(12.375)
        assertEquals(12.375, MapZoomPreferences(context).load()!!, 0.0001)
        assertEquals("course", preferences.getString("orientation", null))
    }

    @Test fun malformedAndOutOfRangeSavedValuesAreIgnored() = runBlocking {
        val preferences = context.getSharedPreferences("navigation_map", Context.MODE_PRIVATE)
        preferences.edit().putString("user_zoom", "bad-value").commit()
        assertNull(MapZoomPreferences(context).load())
        for (zoom in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 25f)) {
            preferences.edit().putFloat("user_zoom", zoom).commit()
            assertNull(MapZoomPreferences(context).load())
        }
    }
}
