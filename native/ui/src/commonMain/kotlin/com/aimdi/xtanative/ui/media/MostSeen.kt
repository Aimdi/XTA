package com.aimdi.xtanative.ui.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of a feed's tiles plays by itself, as X's feed picks it: each tile
 * says how much of it is on screen ([report]), and the one seen most, at
 * least [VideoRules.SEEN_SHARE] of it, is [chosen]. The tile chosen keeps
 * its turn until another is seen strictly more, so two tiles seen alike do
 * not take it from each other while the feed scrolls; between tiles that
 * were never chosen, the first to report wins a tie. Tiles report rather
 * than rows: a row taller than the screen, a thread of several posts, or a
 * video further down a row are judged by the video itself.
 */
internal class MostSeen<K : Any> {
    private val shares = LinkedHashMap<K, Float>()
    private val current = MutableStateFlow<K?>(null)

    val chosen: StateFlow<K?> = current.asStateFlow()

    /** [key]'s tile shows [share] of itself now, 0 to 1. */
    fun report(key: K, share: Float) {
        if (share > 0f) shares[key] = share else shares.remove(key)
        settle()
    }

    /** [key]'s tile is gone, or plays nothing by itself. */
    fun forget(key: K) {
        if (shares.remove(key) != null) settle()
    }

    private fun settle() {
        val best = shares.entries.filter { it.value >= VideoRules.SEEN_SHARE }.maxByOrNull { it.value }
        val held = current.value?.let { key -> shares[key]?.takeIf { it >= VideoRules.SEEN_SHARE }?.let { key to it } }
        current.value = when {
            best == null -> null
            held != null && held.second >= best.value -> held.first
            else -> best.key
        }
    }
}
