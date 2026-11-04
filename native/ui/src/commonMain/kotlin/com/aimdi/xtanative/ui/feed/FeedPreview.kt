package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What a feed shows: its threads, and why the folded ones are folded, by thread id. */
data class Shown(val threads: List<XThread>, val folds: Map<String, String>)

/** A feed's cache shown again before X answers (XTA's first-page preview). */
object FeedPreview {
    /**
     * [threads] as the feed's rules see them now ([shaping]): what they hide
     * left out, what they fold folded. The order and the per-load caps the
     * cache was kept with stay, so the reader's place in it still holds.
     */
    fun of(threads: List<XThread>, shaping: FeedShaping, now: Instant = Instant.now()): Shown {
        val kept = threads.filter { shaping.keeps(it, now) }
        val folds = shaping.copy(postCaps = emptyMap(), zen = false).page(kept, now).foldReasons
        return Shown(kept, folds)
    }
}

/**
 * Which feeds show their media alone (XTA's Posts | Media on Following, a
 * group's Media chip), by feed key: kept for this run of the app, as XTA's
 * feed session cache keeps the choice, and forgotten on the next.
 */
object MediaModes {
    private val on = MutableStateFlow<Set<String>>(emptySet())

    val keys: StateFlow<Set<String>> = on.asStateFlow()

    fun isOn(key: String): Boolean = key in on.value

    fun set(key: String, media: Boolean) = on.update { if (media) it + key else it - key }
}
