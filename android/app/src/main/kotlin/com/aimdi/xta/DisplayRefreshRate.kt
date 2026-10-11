package com.aimdi.xta

import android.app.Activity
import android.os.Build
import android.view.Display

// Many phones keep an app at 60 Hz unless its window asks for a faster mode,
// and Flutter does not ask. Prefer the fastest mode at the current resolution.
internal fun Activity.preferFastestRefreshRate() {
    val display = currentDisplay() ?: return
    val current = display.mode
    val fastest = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate } ?: return
    val attributes = window.attributes
    if (attributes.preferredDisplayModeId == fastest.modeId) return
    attributes.preferredDisplayModeId = fastest.modeId
    window.attributes = attributes
}

private fun Activity.currentDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    }
