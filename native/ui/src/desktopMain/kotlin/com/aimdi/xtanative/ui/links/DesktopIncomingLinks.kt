package com.aimdi.xtanative.ui.links

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The links handed to the desktop app from outside: `xta-native <url>` (the
 * menu entry's %U, a link router, a terminal), at its start or to the copy
 * already running. They wait here until the screens are up to open them, each
 * opened once, as Android's ACTION_VIEW intents are.
 */
object DesktopIncomingLinks {
    /** How many links wait for the screens at most; more are dropped, as nobody clicks that fast. */
    private const val WAITING = 16

    private val waiting = Channel<String>(WAITING, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Opens [url] in the app as soon as its screens are up. */
    fun hand(url: String) {
        waiting.trySend(url)
    }

    /** The links handed here, each to one collector only. */
    internal val links: Flow<String> = waiting.receiveAsFlow()
}
