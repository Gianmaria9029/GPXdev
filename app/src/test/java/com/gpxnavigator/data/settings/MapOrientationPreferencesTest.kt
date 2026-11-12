package com.gpxnavigator.data.settings

import android.app.Application
import android.content.Context
import com.gpxnavigator.domain.model.MapOrientation
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
class MapOrientationPreferencesTest {
    private lateinit var context: Application
    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("navigation_map", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultIsNorthAndSelectionPersistsAcrossInstances() = runBlocking {
        assertEquals(MapOrientation.NORTH_UP, MapOrientationPreferences(context).load())
        MapOrientationPreferences(context).save(MapOrientation.COURSE_UP)
        assertEquals(MapOrientation.COURSE_UP, MapOrientationPreferences(context).load())
        MapOrientationPreferences(context).save(MapOrientation.NORTH_UP)
        assertEquals(MapOrientation.NORTH_UP, MapOrientationPreferences(context).load())
    }

    @Test fun invalidStoredSettingFallsBackToNorth() = runBlocking {
        context.getSharedPreferences("navigation_map", Context.MODE_PRIVATE).edit().putInt("orientation", 4).commit()
        assertEquals(MapOrientation.NORTH_UP, MapOrientationPreferences(context).load())
    }
}
