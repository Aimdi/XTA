package com.aimdi.xtanative.ui.chrome

import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Settings › Theme › Hide the status bar: [window]'s status bar (time,
 * battery, signal) hidden while [hidden], a swipe from the top edge showing
 * it a moment; the navigation bar stays. While it is hidden the screens are
 * kept clear of a camera cutout at the top, which the status bar covered
 * before and the screens' own bars no longer leave room for.
 */
@Composable
fun StatusBarFrame(window: Window, hidden: Boolean, content: @Composable () -> Unit) {
    LaunchedEffect(window, hidden) { StatusBar.show(window, !hidden) }
    val clear = if (hidden) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)) else Modifier
    Box(clear) { content() }
}

/** The status bar of a window, shown or hidden. */
object StatusBar {
    fun show(window: Window, shown: Boolean) {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (shown) bars.show(WindowInsetsCompat.Type.statusBars()) else bars.hide(WindowInsetsCompat.Type.statusBars())
    }
}
