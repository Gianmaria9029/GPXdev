package com.gpxnavigator

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.gpxnavigator.navigation.NavigationService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gpxnavigator.ui.NavigatorApp
import androidx.lifecycle.ViewModelProvider
import com.gpxnavigator.ui.settings.DisplaySettingsViewModel
import com.gpxnavigator.ui.settings.bindDisplaySettings

class MainActivity : ComponentActivity() {
    private var openNavigationRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNavigationRequest = savedInstanceState?.getInt("navigation_request") ?: 0
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        consumeNavigationIntent(intent)
        val displaySettings = ViewModelProvider(this)[DisplaySettingsViewModel::class.java]
        bindDisplaySettings(displaySettings.state)
        setContent { NavigatorApp(openNavigationRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("navigation_request", openNavigationRequest)
        super.onSaveInstanceState(outState)
    }

    private fun consumeNavigationIntent(intent: Intent) {
        if (intent.getBooleanExtra(NavigationService.EXTRA_OPEN_NAVIGATION, false)) {
            openNavigationRequest++
            intent.removeExtra(NavigationService.EXTRA_OPEN_NAVIGATION)
        }
    }
}
