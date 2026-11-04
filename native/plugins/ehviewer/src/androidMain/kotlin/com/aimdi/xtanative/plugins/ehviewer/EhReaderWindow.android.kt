package com.aimdi.xtanative.plugins.ehviewer

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** The reader's view keeps the screen on, and hides the status and navigation bars until a swipe brings them back. */
@Composable
internal actual fun ReaderWindow(keepOn: Boolean, barsHidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepOn) {
        val before = view.keepScreenOn
        if (keepOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }
    DisposableEffect(view, barsHidden) {
        val restore = if (barsHidden) hideBars(view) else null
        onDispose { restore?.invoke() }
    }
}

/** Hides the system bars; the returned call shows them again. */
@Suppress("DEPRECATION")
private fun hideBars(view: View): () -> Unit {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = view.windowInsetsController ?: return {}
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsets.Type.systemBars())
        return { controller.show(WindowInsets.Type.systemBars()) }
    }
    val before = view.systemUiVisibility
    view.systemUiVisibility = before or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    return { view.systemUiVisibility = before }
}
