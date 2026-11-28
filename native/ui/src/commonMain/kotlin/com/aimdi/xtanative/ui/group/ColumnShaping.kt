package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.x.model.XThread

/**
 * A feed as one deck column shows it, some of its threads left out (its
 * reposts or replies, [com.aimdi.xtanative.core.session.DeckColumnOptions]):
 * [state] with those threads gone, its jump moved to the thread it named, and
 * the way back from a shown thread's place to the feed's own ([original]), so
 * the place the feed keeps is the feed's whatever the column hides.
 */
class ShapedFeed(val state: GroupFeedUi, private val kept: List<Int>) {
    /** The feed's own index of the [shown]th thread the column shows. */
    fun original(shown: Int): Int = kept.getOrNull(shown) ?: shown

    companion object {
        /** [state] with only the threads [keep] keeps; [state] itself without [keep]. */
        fun of(state: GroupFeedUi, keep: ((XThread) -> Boolean)?): ShapedFeed {
            val threads = state.run.shown.threads
            if (keep == null) return ShapedFeed(state, threads.indices.toList())
            val kept = threads.indices.filter { keep(threads[it]) }
            val run = state.run
            val jump = run.jump?.let { jump -> jump.copy(threadIndex = kept.count { it < jump.threadIndex }) }
            val shown = run.shown.copy(threads = kept.map(threads::get))
            return ShapedFeed(state.copy(run = run.copy(shown = shown, jump = jump)), kept)
        }
    }
}
