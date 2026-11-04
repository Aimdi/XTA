package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.CatchUpMessage
import com.aimdi.xtanative.core.feed.FeedOrder
import com.aimdi.xtanative.core.feed.FeedPage
import com.aimdi.xtanative.core.feed.FeedPause
import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.feed.FeedStops
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant

/**
 * What a group feed shows of the pages it read: the threads, why the folded
 * ones are folded, where paging stopped on purpose (catch-up mode at the last
 * read post, zen mode's page cap) and whether X mixed in posts from outside
 * the group.
 */
data class FeedShown(
    val threads: List<XThread> = emptyList(),
    val folds: Map<String, String> = emptyMap(),
    val stops: FeedStops = FeedStops(),
    /** The feed could read on. */
    val hasMore: Boolean = false,
    val unrelated: Boolean = false,
    /** A source failed since the first page: a catch-up card must not say the reader is done. */
    val incomplete: Boolean = false,
) {
    val pause: FeedPause? get() = stops.pause

    /** More may be read now: the feed has more and did not stop on purpose. */
    val canLoadMore: Boolean get() = hasMore && stops.pause == null

    /** What the card closing a catch-up feed says. */
    val catchUpMessage: CatchUpMessage get() = CatchUpMessage.of(mayBeIncomplete = incomplete, nothingNew = threads.isEmpty())
}

/** The group feed's paging (_feed.dart, paginated_tweet_list.dart): plain, so what it shows can be tested. */
object GroupFeedPaging {
    /** A first page, read from the top again: the stops start over. */
    fun first(page: FeedPage, pageCap: Int?, isSeen: ((XThread) -> Boolean)?): FeedShown {
        val step = FeedStops(pageCap = pageCap).page(page.threads, first = true, isSeen = isSeen, hasMore = page.hasMore)
        return FeedShown(step.shown, page.foldReasons, step.stops, page.hasMore, page.unrelated, page.failures.isNotEmpty())
    }

    /** The next page under [shown]; a thread already on screen is not shown twice. */
    fun more(shown: FeedShown, page: FeedPage, isSeen: ((XThread) -> Boolean)?): FeedShown {
        val step = shown.stops.page(page.threads, first = false, isSeen = isSeen, hasMore = page.hasMore)
        return shown.copy(
            threads = appended(shown.threads, step.shown),
            folds = shown.folds + page.foldReasons,
            stops = step.stops,
            hasMore = page.hasMore,
            unrelated = shown.unrelated || page.unrelated,
            incomplete = shown.incomplete || page.failures.isNotEmpty(),
        )
    }

    /** Reading on past a stop: what it held back goes on screen, then paging goes on. */
    fun resumed(shown: FeedShown): FeedShown {
        val more = shown.stops.moreWhenPaused
        val step = shown.stops.resume()
        return shown.copy(threads = appended(shown.threads, step.shown), stops = step.stops, hasMore = more)
    }

    /**
     * The sources that failed, read again: what they brought goes among the
     * threads shown, in the feed's order (by time, or by likes for a popular one).
     */
    fun retried(shown: FeedShown, page: FeedPage, popular: Boolean): FeedShown {
        val merged = FeedOrder.newestFirst(appended(shown.threads, page.threads))
        return shown.copy(
            threads = if (popular) FeedOrder.byLikes(merged) else merged,
            folds = shown.folds + page.foldReasons,
            hasMore = shown.hasMore || page.hasMore,
            unrelated = shown.unrelated || page.unrelated,
            incomplete = page.failures.isNotEmpty(),
        )
    }

    private fun appended(threads: List<XThread>, more: List<XThread>): List<XThread> {
        val known = threads.mapTo(HashSet()) { it.id }
        return threads + more.filter { known.add(it.id) }
    }

    /**
     * What a cached feed shows while the first page loads: its threads as the
     * feed's rules shape them now, so a word muted since is muted there too.
     */
    fun preview(threads: List<XThread>, shaping: FeedShaping, now: Instant): FeedShown {
        val shaped = shaping.shape(threads, now)
        return FeedShown(shaped.threads, shaped.foldReasons)
    }

    /** Where the "you're caught up" divider goes: above the first read thread, when something new sits above it. */
    fun dividerIndex(threads: List<XThread>, lastSeen: ReadPosition?, tracks: Boolean, catchUp: Boolean): Int? =
        if (!tracks || catchUp || lastSeen == null) null else lastSeen.boundaryIndex(threads)
}
