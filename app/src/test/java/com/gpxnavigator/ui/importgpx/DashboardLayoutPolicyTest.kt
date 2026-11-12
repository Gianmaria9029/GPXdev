package com.gpxnavigator.ui.importgpx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutPolicyTest {
    @Test fun landscapeUsesTwoPanesWhenBothTextColumnsFit() {
        for (width in listOf(736f, 800f, 1024f)) assertTrue(dashboardLayout(width, 360f, 1f).twoPanes)
    }
    @Test fun narrowOrShortWindowsKeepOneScrollableColumn() {
        assertFalse(dashboardLayout(640f, 360f, 1f).twoPanes)
        assertFalse(dashboardLayout(800f, 280f, 1f).twoPanes)
    }
    @Test fun fontScaleIsRespectedWithoutShrinkingText() {
        assertFalse(dashboardLayout(800f, 360f, 1.3f).twoPanes)
        assertFalse(dashboardLayout(1200f, 640f, 2f).twoPanes)
        assertTrue(dashboardLayout(1400f, 640f, 2f).twoPanes)
    }
    @Test fun portraitUsesOneColumn() {
        assertFalse(dashboardLayout(400f, 800f, 1f).twoPanes)
        assertFalse(dashboardLayout(900f, 1200f, 1f).twoPanes)
        assertFalse(dashboardLayout(800f, 800f, 1f).twoPanes)
    }
}
