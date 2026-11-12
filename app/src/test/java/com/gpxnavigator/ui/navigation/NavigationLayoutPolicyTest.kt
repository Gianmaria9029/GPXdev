package com.gpxnavigator.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLayoutPolicyTest {
    @Test fun compactLandscapePhonesUseTheSidebarBelowTheOld600DpThreshold() {
        for (width in listOf(400f, 480f, 568f)) {
            val layout = navigationLayout(width, 280f)
            assertTrue(layout.landscape)
            assertTrue(layout.sidebarWidthDp in 160f..180f)
        }
    }

    @Test fun landscapeFollowControlFitsBesideTheSidebarWithMargins() {
        val controlsWidth = 48 + 2 * 8
        for (width in listOf(400f, 480f, 568f, 600f, 800f, 1280f)) {
            val layout = navigationLayout(width, 280f)
            assertTrue(width - layout.sidebarWidthDp >= controlsWidth)
        }
    }

    @Test fun portraitTabletsAndPhonesKeepTheirBottomMetrics() {
        for ((width, height) in listOf(360f to 760f, 600f to 960f, 800f to 1280f)) {
            val layout = navigationLayout(width, height)
            assertFalse(layout.landscape)
            assertEquals(0f, layout.sidebarWidthDp, 0f)
        }
    }

    @Test fun theSidebarHasAnUpperBoundOnLargeScreens() {
        val layout = navigationLayout(1280f, 720f)
        assertTrue(layout.landscape)
        assertTrue(layout.sidebarWidthDp <= 180f)
    }

    @Test fun rallyCardLeavesRoomForTopControlsSecondaryTurnAndMapBesideIt() {
        for ((width, height) in listOf(240f to 232f, 420f to 240f, 620f to 312f, 1100f to 672f)) {
            val cue = landscapeManeuverSize(width, height)
            assertTrue(cue in 96f..192f)
            assertTrue("No map lane at $width", width - cue >= 112f)
            assertTrue("Controls overlap cue at $height", height - cue >= 120f)
        }
    }
}
