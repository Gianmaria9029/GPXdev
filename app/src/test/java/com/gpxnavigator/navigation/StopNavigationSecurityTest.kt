@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import android.content.ComponentName
import android.content.Intent
import com.gpxnavigator.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class StopNavigationSecurityTest : NavigationTestSupport() {
    @Test fun launcherIgnoresStopExtrasAtCreationAndOnNewIntent() {
        val untrusted = Intent(app, MainActivity::class.java).putExtra("finish_navigation", true)
        val activity = Robolectric.buildActivity(MainActivity::class.java, untrusted).create()
        try {
            assertNull(shadowOf(app).nextStartedService)
            activity.newIntent(Intent(untrusted))
            assertNull(shadowOf(app).nextStartedService)
        } finally { activity.destroy() }
    }

    @Test fun privateNotificationActivityStopsAndOpensTheSaveDecisionScreen() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, StopNavigationActivity::class.java), 0)
        assertFalse(info.exported)
        val activity = Robolectric.buildActivity(StopNavigationActivity::class.java).create()
        try {
            assertEquals(NavigationService.ACTION_STOP, shadowOf(app).nextStartedService.action)
            val open = shadowOf(app).nextStartedActivity
            assertEquals(MainActivity::class.java.name, open.component!!.className)
            assertTrue(open.getBooleanExtra(NavigationService.EXTRA_OPEN_NAVIGATION, false))
            assertTrue(activity.get().isFinishing)
        } finally { activity.destroy() }
    }
}
