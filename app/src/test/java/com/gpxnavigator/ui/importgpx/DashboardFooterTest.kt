package com.gpxnavigator.ui.importgpx

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class DashboardFooterTest {
    @Test fun developerCreditOpensTheHttpsAddressInAnExternalBrowser() {
        val intent = developerWebsiteIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://gianmaria.dev", intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(Intent.ACTION_MAIN, intent.selector?.action)
        assertTrue(intent.selector?.hasCategory(Intent.CATEGORY_APP_BROWSER) == true)
    }
}
