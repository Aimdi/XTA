package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/**
 * The quick switcher of a window (Ctrl+P, Ctrl+K), asked for by its keys
 * ([DeskAction.QUICK_SWITCH]) and drawn by the screens' frame
 * ([QuickSwitchHost]), which leads in [nav]. Closed, it gives the keys back
 * to the page ([keysBack]).
 */
@Stable
class QuickSwitchRequest(val nav: NavHostController, private val keysBack: () -> Unit) {
    var open by mutableStateOf(false)
        private set

    fun show() {
        open = true
    }

    fun close() {
        if (!open) return
        open = false
        keysBack()
    }
}

/** The window's [QuickSwitchRequest]; null off the desktop. */
val LocalQuickSwitch = staticCompositionLocalOf<QuickSwitchRequest?> { null }
