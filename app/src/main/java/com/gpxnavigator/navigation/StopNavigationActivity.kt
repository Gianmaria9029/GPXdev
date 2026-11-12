package com.gpxnavigator.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.gpxnavigator.MainActivity

/** Only the app's immutable notification PendingIntent can enter this non-exported activity. */
class StopNavigationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NavigationService.requestStop(this)
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(NavigationService.EXTRA_OPEN_NAVIGATION, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
