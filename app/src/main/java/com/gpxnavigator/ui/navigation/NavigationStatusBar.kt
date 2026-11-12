package com.gpxnavigator.ui.navigation

import android.view.ViewTreeObserver
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun NavigationStatusBar() {
    val activity = LocalActivity.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, view, lifecycle) {
        if (activity == null) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val previousBehavior = controller.systemBarsBehavior
        val statusBars = WindowInsetsCompat.Type.statusBars()
        fun hideStatusBar() {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(statusBars)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> hideStatusBar()
                Lifecycle.Event.ON_STOP -> controller.show(statusBars)
                else -> Unit
            }
        }
        // Reapply after a dialog or another window gives focus back to navigation.
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) hideStatusBar()
        }
        lifecycle.addObserver(observer)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) hideStatusBar()
        onDispose {
            lifecycle.removeObserver(observer)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            controller.show(statusBars)
            controller.systemBarsBehavior = previousBehavior
        }
    }
}
