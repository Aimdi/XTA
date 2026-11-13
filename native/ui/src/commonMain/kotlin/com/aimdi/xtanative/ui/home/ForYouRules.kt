package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.x.model.XThread

/** How Home's For you reads X's home timeline, as XTA's For you tab does. */
object ForYouRules {
    /** Posts asked of each account's timeline per page. */
    const val PAGE_SIZE = 20

    /**
     * For you without replies, as XTA reads it (`includeReplies: false`): a
     * thread keeps the posts that answer nothing, and is left out when none is
     * left. A repost of a reply is the reposter's post, and stays.
     */
    fun withoutReplies(threads: List<XThread>): List<XThread> = threads.mapNotNull { thread ->
        val kept = thread.posts.filter { it.replyToPostId == null && it.replyToScreenName == null }
        when {
            kept.isEmpty() -> null
            kept.size == thread.posts.size -> thread
            else -> thread.copy(posts = kept)
        }
    }

    /** [more] under [shown], each thread once. */
    fun appended(shown: List<XThread>, more: List<XThread>): List<XThread> {
        val known = shown.mapTo(HashSet()) { it.id }
        return shown + more.filter { known.add(it.id) }
    }

    /** Zen mode's pages per session (Settings › Posts), or no cap. */
    fun pageCap(zenMode: Boolean, pages: Int): Int? = if (zenMode) pages.coerceAtLeast(1) else null
}
