package com.gpxnavigator.ui.map

import android.app.Application
import android.graphics.Color
import com.gpxnavigator.domain.model.MapAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationDirectionImageTest {
    @Test fun arrowUsesTheMarkerFillAndOutlineAtScreenDensity() {
        for (appearance in MapAppearance.entries) {
            val palette = navigationMapPalette(appearance)
            val bitmap = locationDirectionImage(2f, palette)
            try {
                assertEquals(72, bitmap.width)
                assertEquals(320, bitmap.density)
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
                assertEquals(Color.parseColor(palette.location), bitmap.getPixel(36, 32))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue(pixels.contains(Color.parseColor(palette.locationOutline)))
            } finally {
                bitmap.recycle()
            }
        }
    }
}
