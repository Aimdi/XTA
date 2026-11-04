package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController

/**
 * What the screen on top lets the desktop's keys do (XTA's DesktopRefreshKeys
 * and DesktopPager): F5 and Ctrl+R refresh it, ← and → turn its pages, and
 * going back first closes what it has open. One screen holds them at a time,
 * the one resumed; see [DesktopShortcuts].
 */
class DesktopActions {
    private var owner: Any? = null
    private var refreshing: (() -> Unit)? = null
    private var turning: ((Int) -> Unit)? = null
    private var open: (() -> Boolean)? = null
    private var closing: (() -> Unit)? = null

    internal fun take(owner: Any, refresh: () -> Unit, turn: (Int) -> Unit, isOpen: () -> Boolean, close: () -> Unit) {
        this.owner = owner
        refreshing = refresh
        turning = turn
        open = isOpen
        closing = close
    }

    internal fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        refreshing = null
        turning = null
        open = null
        closing = null
    }

    /** False when no screen answers. */
    fun refresh(): Boolean = refreshing?.let { it(); true } ?: false

    fun turn(by: Int): Boolean = turning?.let { it(by); true } ?: false

    fun somethingOpen(): Boolean = open?.invoke() ?: false

    fun close() {
        closing?.invoke()
    }
}

/** The desktop's actions; null off the desktop, where only touch and Android's back exist. */
val LocalDesktopActions = staticCompositionLocalOf<DesktopActions?> { null }

/**
 * Going back the way a PC does it (XTA's DesktopNavigation): the mouse's back
 * button, Alt+← and Esc go back as Android's back gesture does, the first Esc
 * leaving a text field being typed in, and the screen on top answers
 * [DesktopActions]. Off the desktop it is [content] alone.
 */
@Composable
expect fun DesktopNavigation(nav: NavHostController, content: @Composable () -> Unit)

/**
 * Lets the screen answer the desktop's keys while it is the one shown: [refresh]
 * for F5 and Ctrl+R, [turn] for ← and →, and [isOpen] with [close] for what
 * going back closes first. Nothing off the desktop.
 */
@Composable
internal fun DesktopShortcuts(refresh: () -> Unit, turn: (Int) -> Unit, isOpen: () -> Boolean, close: () -> Unit) {
    val actions = LocalDesktopActions.current ?: return
    val latestRefresh by rememberUpdatedState(refresh)
    val latestTurn by rememberUpdatedState(turn)
    val latestIsOpen by rememberUpdatedState(isOpen)
    val latestClose by rememberUpdatedState(close)
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        actions.take(owner, { latestRefresh() }, { latestTurn(it) }, { latestIsOpen() }, { latestClose() })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.release(owner) }
    DisposableEffect(actions) { onDispose { actions.release(owner) } }
}
