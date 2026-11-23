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

    /**
     * Opens [url] in the app as soon as its screens are up; one the app has
     * just sent to the browser is not taken back, unless the reader handed
     * it here themselves ([byReader]: dropped or pasted), which no link
     * router does.
     */
    fun hand(url: String, byReader: Boolean = false) {
        if (!byReader && SentOutside.cameBack(url)) return
        waiting.trySend(url)
    }

    /** The links handed here, each to one collector only. */
    internal val links: Flow<String> = waiting.receiveAsFlow()
}

/**
 * The links the app sent out to the desktop's browser lately. Where a link
 * router sends X links to the app (`xta-native %U`), one the app cannot
 * show and hands out would come straight back, and round again: it is
 * dropped instead, for [WINDOW_MS].
 */
internal object SentOutside {
    private const val WINDOW_MS = 10_000L
    private val sent = LinkedHashMap<String, Long>()

    @Synchronized
    fun sent(url: String, now: Long = System.currentTimeMillis()) {
        sent.entries.removeAll { now - it.value > WINDOW_MS }
        sent[url] = now
    }

    @Synchronized
    fun cameBack(url: String, now: Long = System.currentTimeMillis()): Boolean {
        val at = sent[url] ?: return false
        return (now - at <= WINDOW_MS).also { if (it) sent.remove(url) }
    }
}
