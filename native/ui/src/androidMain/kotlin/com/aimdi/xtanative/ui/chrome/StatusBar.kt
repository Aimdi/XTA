package com.aimdi.xtanative.ui.chrome

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Settings › Theme › Hide the status bar: [window]'s status bar (time,
 * battery, signal) hidden while [hidden], a swipe from the top edge showing
 * it a moment; the navigation bar stays. The screens then take the space it
 * had, up to the top edge, the strip beside a camera cutout included.
 */
@Composable
fun StatusBarFrame(window: Window, hidden: Boolean, content: @Composable () -> Unit) {
    LaunchedEffect(window, hidden) { StatusBar.show(window, !hidden) }
    content()
}

/** The status bar of a window, shown or hidden. */
object StatusBar {
    fun show(window: Window, shown: Boolean) {
        // Drawn up to the top edge beside a camera cutout, where the status bar was.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (shown) bars.show(WindowInsetsCompat.Type.statusBars()) else bars.hide(WindowInsetsCompat.Type.statusBars())
    }
}
