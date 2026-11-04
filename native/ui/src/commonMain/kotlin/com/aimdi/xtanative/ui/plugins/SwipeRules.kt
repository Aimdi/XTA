package com.aimdi.xtanative.ui.plugins

/** What a followed account's row swiped from its end does ([SwipeToUnfollow]). */
object SwipeRules {
    /**
     * The row swiped [fromEnd] asks to unfollow ([ask], which confirms
     * first, as XTA's confirmDismiss does); it never leaves by the swipe
     * alone, but with the follow once it is confirmed, so this is false.
     */
    fun settles(fromEnd: Boolean, ask: () -> Unit): Boolean {
        if (fromEnd) ask()
        return false
    }
}
