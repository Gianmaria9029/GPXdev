package com.gpxnavigator.data.settings

import android.app.Application
import android.content.Context
import com.gpxnavigator.domain.model.MapAppearance
import com.gpxnavigator.domain.model.MapOrientation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class MapAppearancePreferencesTest {
    @Test fun defaultsToLightAndPersistsSeparatelyFromOtherMapPreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val stored = context.getSharedPreferences("navigation_map", Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        val orientation = MapOrientationPreferences(context)
        val zoom = MapZoomPreferences(context)
        orientation.save(MapOrientation.COURSE_UP)
        zoom.save(14.0)

        val appearance = MapAppearancePreferences(context)
        assertEquals(MapAppearance.LIGHT, appearance.load())
        appearance.save(MapAppearance.DARK)
        assertEquals(MapAppearance.DARK, MapAppearancePreferences(context).load())
        assertEquals(MapOrientation.COURSE_UP, orientation.load())
        assertEquals(14.0, zoom.load())

        stored.edit().putInt("appearance", 1).commit()
        assertEquals(MapAppearance.LIGHT, appearance.load())
    }
}
