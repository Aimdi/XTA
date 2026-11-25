package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.saved.ProfileFilters
import com.aimdi.xtanative.ui.feed.HiddenPosts
import com.aimdi.xtanative.ui.feed.MutedEverywhere
import java.time.Instant

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

    /**
     * [threads] as For you shows them under the profiles' [filters]: without
     * the posts of an account hidden altogether, whoever reposted them. The
     * reposts' and replies' filters are for the feeds of accounts followed;
     * For you already reads no replies. The posts with a word of [muted]
     * (Settings › Posts) are left out too; For you folds nothing, so a word
     * set to fold hides here.
     */
    fun shown(threads: List<XThread>, filters: ProfileFilters, muted: List<MutedKeyword> = emptyList(), now: Instant = Instant.now()): List<XThread> =
        MutedEverywhere.shown(HiddenPosts.withoutAuthors(threads, filters), muted, now)

    /** [more] under [shown], each thread once. */
    fun appended(shown: List<XThread>, more: List<XThread>): List<XThread> {
        val known = shown.mapTo(HashSet()) { it.id }
        return shown + more.filter { known.add(it.id) }
    }

    /** Zen mode's pages per session (Settings › Posts), or no cap. */
    fun pageCap(zenMode: Boolean, pages: Int): Int? = if (zenMode) pages.coerceAtLeast(1) else null
}
