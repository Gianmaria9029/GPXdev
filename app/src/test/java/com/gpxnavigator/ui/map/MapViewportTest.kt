package com.gpxnavigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {
    @Test fun cameraCenterStaysBetweenTheTopHudAndBottomControls() {
        val viewport = MapViewport(1080, 1800, ViewportPadding(top = 400.0, bottom = 280.0))
        val padding = viewport.cameraPadding()
        val center = (padding.top + viewport.height - padding.bottom) / 2
        assertEquals(400.0, padding.top, 0.0)
        assertEquals(280.0, padding.bottom, 0.0)
        assertTrue(center > 400 && center < 1520)
    }

    @Test fun landscapeUsesItsOwnMeasuredOverlaysInsteadOfThePortraitHud() {
        val portrait = MapViewport(1080, 1600, ViewportPadding(top = 400.0, bottom = 300.0))
        val landscape = MapViewport(1600, 700, ViewportPadding(top = 60.0, bottom = 144.0))
        assertEquals(400.0, portrait.cameraPadding().top, 0.0)
        assertEquals(60.0, landscape.cameraPadding().top, 0.0)
        assertEquals(144.0, landscape.cameraPadding().bottom, 0.0)
    }

    @Test fun courseUpPlacesLocationAt62PercentOfTheUnobstructedArea() {
        val viewport = MapViewport(1200, 700, ViewportPadding(top = 40.0, bottom = 90.0))
        val padding = viewport.cameraPadding(courseUp = true)
        val center = (padding.top + viewport.height - padding.bottom) / 2
        assertEquals(40 + (700 - 40 - 90) * 0.62, center, 0.001)
    }

    @Test fun veryLargeOverlaysCannotConsumeTheEntireViewport() {
        val viewport = MapViewport(320, 140, ViewportPadding(280.0, 400.0, 280.0, 300.0))
        val padding = viewport.cameraPadding(courseUp = true)
        assertTrue(viewport.width - padding.left - padding.right >= 96 - 0.001)
        assertTrue(viewport.height - padding.top - padding.bottom >= 96 - 0.001)
        assertTrue(listOf(padding.left, padding.top, padding.right, padding.bottom).all { it.isFinite() && it >= 0 })
    }

    @Test fun zeroAndTinyViewportsNeverProduceNegativeOrInvalidPadding() {
        for (size in listOf(0, 32, 95)) {
            val padding = MapViewport(size, size, ViewportPadding(200.0, 300.0, 400.0, 500.0))
                .cameraPadding(courseUp = true, margin = 16.0)
            assertEquals(ViewportPadding(), padding)
        }
    }

    @Test fun malformedInsetsAreDiscardedBeforeReachingTheNativeCamera() {
        val padding = MapViewport(500, 500, ViewportPadding(Double.NaN, Double.POSITIVE_INFINITY, -40.0, 0.0))
            .cameraPadding()
        assertEquals(ViewportPadding(), padding)
    }

    @Test fun routeOverviewKeepsAnExtraMarginAroundTheVisibleArea() {
        val viewport = MapViewport(600, 300, ViewportPadding(top = 32.0, bottom = 64.0))
        assertEquals(ViewportPadding(16.0, 48.0, 16.0, 80.0), viewport.cameraPadding(margin = 16.0))
    }

    @Test fun landscapeFollowKeepsPositionBesideTheRallyCardAndBelowTopControls() {
        for (density in listOf(1.0, 2.0, 3.0)) {
            for ((width, height, cue) in listOf(Triple(620, 312, 192), Triple(240, 232, 112))) {
                val viewport = MapViewport((width * density).toInt(), (height * density).toInt(),
                    ViewportPadding(left = (cue + 16) * density, top = 64 * density, bottom = 72 * density))
                for (courseUp in listOf(false, true)) {
                    val padding = viewport.cameraPadding(courseUp)
                    val x = (padding.left + viewport.width - padding.right) / 2
                    val y = (padding.top + viewport.height - padding.bottom) / 2
                    assertTrue(x > (cue + 8) * density)
                    assertTrue(y > 56 * density)
                    assertTrue(y < (height - 60) * density)
                }
            }
        }
    }
}
