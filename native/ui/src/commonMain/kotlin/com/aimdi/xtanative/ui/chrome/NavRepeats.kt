package com.aimdi.xtanative.ui.chrome

import androidx.navigation.NavHostController
import java.util.WeakHashMap

/**
 * A screen opened twice by one double-tap: the second tap lands on the
 * screen below while the new one slides in, and would stack the same
 * screen again. A push of the route just pushed, while its screen is still
 * the one on top and within [WINDOW_MS], is let go; going back, or
 * waiting a moment, opens it again as before.
 */
internal class NavRepeats {
    private var last: Push? = null

    private data class Push(val route: String, val entryId: String?, val atMs: Long)

    /** Whether [route] may be pushed now, over the screen [topEntryId], at [nowMs]. */
    fun allows(route: String, topEntryId: String?, nowMs: Long): Boolean {
        val push = last ?: return true
        return !(push.route == route && push.entryId == topEntryId && nowMs - push.atMs < WINDOW_MS)
    }

    /** [route] was pushed at [nowMs], and its screen is [entryId]. */
    fun pushed(route: String, entryId: String?, nowMs: Long) {
        last = Push(route, entryId, nowMs)
    }

    companion object {
        /** Longer than a double-tap and a screen's slide in, shorter than a reader meaning to open it again. */
        const val WINDOW_MS = 800L
    }
}

private val repeats = WeakHashMap<NavHostController, NavRepeats>()

/**
 * [route] opened over the screen on top, unless it is the screen that was
 * just opened there ([NavRepeats]); whether it was opened.
 */
internal fun NavHostController.push(route: String): Boolean {
    val guard = synchronized(repeats) { repeats.getOrPut(this) { NavRepeats() } }
    val now = System.currentTimeMillis()
    if (!guard.allows(route, currentBackStackEntry?.id, now)) return false
    navigate(route)
    guard.pushed(route, currentBackStackEntry?.id, now)
    return true
}
