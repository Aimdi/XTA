package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.aimdi.xtanative.ui.home.LocalDesktopActions

/**
 * No system Back on the desktop: the going-back keys (Esc, Alt+←, the
 * mouse's back button) run [onBack] while [enabled] and the screen is the
 * one shown ([com.aimdi.xtanative.ui.home.DesktopActions.addBack]).
 */
@Composable
internal actual fun BackHandling(enabled: Boolean, byKeys: Boolean, onBack: () -> Unit) {
    val actions = LocalDesktopActions.current ?: return
    if (!byKeys) return
    val latest by rememberUpdatedState(onBack)
    val owner = remember { Any() }
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val on = enabled && state.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(actions, on) {
        if (on) actions.addBack(owner) { latest() }
        onDispose { actions.removeBack(owner) }
    }
}
