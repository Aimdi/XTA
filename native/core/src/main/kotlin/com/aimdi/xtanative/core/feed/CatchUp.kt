package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant

/** The keys feeds are kept under: a group's feed by its id, the home feeds by these. */
object FeedKeys {
    const val FOLLOWING = "following"
    const val FOR_YOU = "for_you"

    /** XTA's own id for the Following feed, under which older builds kept its read position. */
    const val LEGACY_FOLLOWING = "-1"

    /** Where a feed's read position is kept: XTA's `-1` is the Following feed. */
    fun readPositionKey(groupId: String): String = if (groupId == LEGACY_FOLLOWING) FOLLOWING else groupId

    /** XTA's preference holding a feed's catch-up mode. */
    fun catchUpPreference(groupId: String) = "feed.catch_up_mode.$groupId"
}

/**
 * The last post the reader is known to have read in a feed. Compared by time,
 * not by presence: the post itself may have left the feed since.
 */
data class ReadPosition(val threadId: String, val createdAt: Instant?) {
    /**
     * Whether [thread] sits at or below this position. Thread ids are not in
     * time order (a conversation carries its root's id), so besides the id
     * itself the first post's time decides.
     */
    fun isSeen(thread: XThread): Boolean {
        if (thread.id == threadId) return true
        val at = thread.posts.firstOrNull()?.createdAt ?: return false
        return createdAt != null && !at.isAfter(createdAt)
    }

    /** The first seen thread with something new above it; null when nothing is new or the boundary is not loaded yet. */
    fun boundaryIndex(threads: List<XThread>): Int? = threads.indexOfFirst(::isSeen).takeIf { it > 0 }

    companion object {
        /** The newest thread that says when it was written: what a feed read from its top records. */
        fun newestRecordable(threads: List<XThread>): XThread? = threads.firstOrNull { it.posts.firstOrNull()?.createdAt != null }

        fun of(thread: XThread) = ReadPosition(thread.id, thread.posts.firstOrNull()?.createdAt)
    }
}

/** What a feed does with a first page it has just finished loading. */
sealed interface FirstPageAction {
    /** Bring the last read thread back under the top bar: there are unread posts above it. */
    data class RestoreToBoundary(val index: Int) : FirstPageAction

    /** Remember [thread] as the newest post read in this feed. */
    data class RecordPosition(val thread: XThread) : FirstPageAction

    data object DoNothing : FirstPageAction

    companion object {
        /**
         * The decision for one finished first page. The caught-up boundary is
         * chosen once per opening ([caughtUpAlreadyEvaluated]) and then frozen,
         * so the divider never moves under the reader. A place the reader was
         * scrolled to ([sessionOffset]) outranks it. Only a reader at the top
         * ([atTop]) has read everything above: a refresh fired mid-scroll
         * records nothing.
         */
        fun of(
            threads: List<XThread>,
            lastSeen: ReadPosition?,
            caughtUpAlreadyEvaluated: Boolean,
            sessionOffset: Double?,
            atTop: Boolean,
        ): FirstPageAction {
            if (!caughtUpAlreadyEvaluated && lastSeen != null && (sessionOffset == null || sessionOffset <= 0)) {
                lastSeen.boundaryIndex(threads)?.let { return RestoreToBoundary(it) }
            }
            if (!atTop) return DoNothing
            return ReadPosition.newestRecordable(threads)?.let(::RecordPosition) ?: DoNothing
        }
    }
}

/**
 * A page cut at the reader's last position: [keep] is what is new, [held]
 * everything from the first read thread down, handed back rather than dropped
 * so "show older posts" puts it on screen without asking X again.
 */
data class CatchUpSplit(val keep: List<XThread>, val held: List<XThread>, val reachedBoundary: Boolean) {
    companion object {
        fun of(threads: List<XThread>, isSeen: (XThread) -> Boolean): CatchUpSplit {
            val index = threads.indexOfFirst(isSeen)
            return if (index < 0) CatchUpSplit(threads, emptyList(), false) else CatchUpSplit(threads.take(index), threads.drop(index), true)
        }
    }
}

/** What the card closing a catch-up feed may claim. */
enum class CatchUpMessage {
    /** Everything between the newest post and the last read one was loaded. */
    CAUGHT_UP,

    /** The boundary was the very first thread: nothing was posted since. */
    NOTHING_NEW,

    /** The load stopped before reaching the posts read before: the reader must not be told they are done. */
    MAY_BE_INCOMPLETE,
    ;

    companion object {
        fun of(mayBeIncomplete: Boolean, nothingNew: Boolean): CatchUpMessage = when {
            mayBeIncomplete -> MAY_BE_INCOMPLETE
            nothingNew -> NOTHING_NEW
            else -> CAUGHT_UP
        }
    }
}

/**
 * Closing the hole between fresh posts and the ones read before: one search
 * page holds only the newest, so after a long absence it sits above the old
 * posts with a gap between them.
 */
object GapFill {
    /** The most extra pages one chunk reads to close it, so a week away cannot cost dozens of requests. */
    const val MAX_FILLS = 4

    /** Whether to read another page: everything just read is still newer than [storedNewestId]. */
    fun shouldContinue(
        storedNewestId: Long?,
        oldestFetchedId: Long?,
        pageNonEmpty: Boolean,
        hasCursor: Boolean,
        fillsSoFar: Int,
        maxFills: Int = MAX_FILLS,
    ): Boolean = storedNewestId != null && pageNonEmpty && hasCursor && fillsSoFar < maxFills && (oldestFetchedId ?: 0) > storedNewestId
}

/** Whether a feed has unread posts, for the dots on its tab and tile. */
object Unread {
    /**
     * Whether a feed keeps a read position: only a chronological one (popular
     * order has no "seen up to"), and only when the reader asked for it
     * everywhere or turned catch-up mode on here. The media grid never does.
     */
    fun tracks(popular: Boolean, readingPosition: Boolean, catchUp: Boolean, mediaOnly: Boolean = false): Boolean =
        !popular && !mediaOnly && (readingPosition || catchUp)

    /**
     * Unread when tracking and something was read from X after the last read
     * mark; never marked read means unread. Untracked feeds never light up:
     * no position is written, so every feed would.
     */
    fun has(tracks: Boolean, newestReadAt: Long?, lastReadAt: Long?): Boolean = when {
        !tracks || newestReadAt == null -> false
        lastReadAt == null -> true
        else -> newestReadAt > lastReadAt
    }

    /** The Following feed's last read mark, under its key or XTA's older `-1`. */
    fun lastReadForFollowing(lastReadByFeed: Map<String, Long>): Long? =
        lastReadByFeed[FeedKeys.FOLLOWING] ?: lastReadByFeed[FeedKeys.LEGACY_FOLLOWING]
}

/**
 * A refresh the reader did not ask for (a member added to the group), held
 * while they read further down and run when they are back at the top, where
 * jumping there costs nothing. Several changes make one refresh.
 */
class HeldRefresh {
    var isPending: Boolean = false
        private set

    /** Asks for a refresh; true when it should run now. */
    fun request(atTop: Boolean): Boolean {
        isPending = !atTop
        return atTop
    }

    /** The reader is back at the top; true when a held refresh should run now. */
    fun returnedToTop(): Boolean = isPending.also { isPending = false }

    companion object {
        /** How close to the top counts as the top, in pixels. */
        const val TOP_THRESHOLD = 8.0

        /**
         * Whether the reader is at the top. An unknown position ([pixels] null,
         * while the list is between layouts) keeps the last known answer:
         * taking it for the top ran held refreshes mid-scroll.
         */
        fun atTop(pixels: Double?, lastKnownAtTop: Boolean, threshold: Double = TOP_THRESHOLD): Boolean =
            pixels?.let { it <= threshold } ?: lastKnownAtTop
    }
}

/** Why paging stopped short of the end of a feed. */
enum class FeedPause {
    /** Zen mode's pages per session. */
    PAGE_CAP,

    /** Catch-up mode reached the last read post. */
    CAUGHT_UP,
}

/**
 * The stops that make a feed finite, as XTA's feed controller applies them to
 * every page: catch-up mode at the reader's last read post, then zen mode's
 * page cap. A stopped feed loads no more until the reader asks; what the stop
 * cut off a page is [held] for then.
 */
data class FeedStops(
    val pageCap: Int? = null,
    val pages: Int = 0,
    /** The reader chose to read past their last position: catch-up stops once per first page. */
    val passedCatchUp: Boolean = false,
    val pause: FeedPause? = null,
    val held: List<XThread> = emptyList(),
    /** The feed had more to read when it stopped. */
    val moreWhenPaused: Boolean = false,
) {
    /** What to show of a page, and the stops after it. */
    data class Step(val stops: FeedStops, val shown: List<XThread>)

    /** Whether there is anything past the pause: a feed that stopped on its last page has nothing. */
    val canContinue: Boolean get() = pause != null && (moreWhenPaused || held.isNotEmpty())

    /**
     * A page just read; [first] when the feed was read from its top again.
     * [isSeen] is the catch-up stop, null when catch-up mode is off or nothing
     * was read here yet. [hasMore] says the feed could read on.
     */
    fun page(threads: List<XThread>, first: Boolean, isSeen: ((XThread) -> Boolean)?, hasMore: Boolean): Step {
        val base = copy(pages = if (first) 1 else pages + 1, passedCatchUp = passedCatchUp && !first, pause = null, held = emptyList(), moreWhenPaused = false)
        if (!base.passedCatchUp && isSeen != null) {
            val split = CatchUpSplit.of(threads, isSeen)
            if (split.reachedBoundary) {
                return Step(base.copy(pause = FeedPause.CAUGHT_UP, held = split.held, moreWhenPaused = hasMore), split.keep)
            }
        }
        val capped = hasMore && pageCap != null && base.pages >= pageCap
        return Step(if (capped) base.copy(pause = FeedPause.PAGE_CAP, moreWhenPaused = true) else base, threads)
    }

    /** Reads on past the pause: the held threads go on screen first, then paging goes on. */
    fun resume(): Step {
        val reason = pause ?: return Step(this, emptyList())
        return Step(copy(pages = 0, passedCatchUp = passedCatchUp || reason == FeedPause.CAUGHT_UP, pause = null, held = emptyList(), moreWhenPaused = false), held)
    }
}
