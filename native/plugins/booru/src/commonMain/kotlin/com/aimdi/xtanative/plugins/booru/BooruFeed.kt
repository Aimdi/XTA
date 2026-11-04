package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPostPage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where loading a feed's next page stands, for the footer under its grid. */
internal enum class BooruPaging { IDLE, LOADING, FAILED, END }

/** What a feed shows (booru_store.dart's stores, in one state). */
internal data class BooruFeedState(
    val posts: List<BooruPost> = emptyList(),
    /** The first page is being read, or read again: until it is first answered, no posts means none loaded yet. */
    val loading: Boolean = true,
    /** Why the first page did not arrive, with nothing to show in its place. */
    val error: Throwable? = null,
    val paging: BooruPaging = BooruPaging.IDLE,
    /** How many of the queries behind the first page failed (Following). */
    val failedQueries: Int = 0,
    /** Why the latest refresh failed while the posts from before stay shown. */
    val refreshFailure: Throwable? = null,
    /** How many posts the blacklist hid, and whether the reader asked to see them anyway. */
    val hidden: Int = 0,
    val hiddenShown: Boolean = false,
) {
    /** What the feed's place on screen holds. */
    val view: FeedView
        get() = when {
            posts.isNotEmpty() -> FeedView.POSTS
            loading -> FeedView.LOADING
            error != null -> FeedView.ERROR
            paging == BooruPaging.END -> FeedView.EMPTY
            // The posts so far were all hidden by the cap, the blacklist or a missing file, and the host has more.
            else -> FeedView.HIDDEN_SO_FAR
        }
}

internal enum class FeedView { LOADING, ERROR, EMPTY, HIDDEN_SO_FAR, POSTS }

/**
 * A feed read page by page from [loader] (the host's page numbers, from 1):
 * Latest, Popular, Following, a search, a tag's or a pool's posts. The first
 * page with posts is shown (the cap and the blacklist can empty a page, so a
 * few are skipped while the host has more); a refresh that fails keeps the
 * posts shown under a notice, or the copy [cache] keeps under [cacheKey] and
 * the feed's [feedKey] (site and filters); the posts the blacklist hid can be
 * shown in their places. A feed whose [feedKey] changed is stale and starts
 * over. Used from the one thread of its [scope], as a screen's state is.
 */
internal class BooruFeed(
    private val scope: CoroutineScope,
    private val feedKey: () -> String,
    private val cache: BooruFeedCache? = null,
    private val cacheKey: (() -> String)? = null,
    private val loader: suspend (page: Int) -> BooruPostPage,
) {
    private class Shown(val page: Int, val hasMore: Boolean, val paging: BooruPaging)

    private class FirstPage(val page: BooruPostPage, val number: Int, val pages: List<BooruPostPage>)

    private val mutable = MutableStateFlow(BooruFeedState())
    val state: StateFlow<BooruFeedState> = mutable.asStateFlow()

    private var page = 0
    private var hasMore = true
    private var loadedFor: String? = null
    private var refreshes = 0

    /** Whether the latest refresh has answered: until then no next page is asked for. */
    private var landed = false
    private var refreshing: Job? = null
    private var more: Job? = null
    private val muted = ArrayList<BooruPost>()
    private val order = HashMap<String, Int>()
    private var showMuted = false
    private var closed = false

    /** Whether the site, rating cap or blacklist changed since the feed loaded. */
    val isStale: Boolean get() = loadedFor != null && loadedFor != feedKey()

    /** Loads the feed when it is empty or stale. */
    fun ensureFresh() {
        if (mutable.value.posts.isEmpty() || isStale) refresh()
    }

    /**
     * Loads the first page again. When [changed], the feed asks for something
     * else now (another day, other tags), so the posts shown no longer answer
     * it and a failure is the feed's error.
     */
    fun refresh(changed: Boolean = false) {
        if (closed) return
        val debounce = refreshes++ > 0
        val shown = if (changed) null else shown()
        val cacheId = cacheId()
        page = 0
        hasMore = true
        loadedFor = feedKey()
        landed = false
        more?.cancel()
        refreshing?.cancel()
        mutable.update { it.copy(loading = true, error = null, paging = BooruPaging.IDLE) }
        refreshing = scope.launch {
            // A refresh waits a moment for a newer one before it asks; the first has nothing to wait for.
            if (debounce) delay(DEBOUNCE_MILLIS)
            val first = try {
                firstPage()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed(error, shown, cacheId)
                return@launch
            }
            land(first)
            if (cacheId != null && first.page.posts.isNotEmpty()) cache?.write(cacheId, KeptPage(first.page.posts, first.number, hasMore))
        }
    }

    /**
     * The next page; a stale feed starts over instead, so another site's posts
     * are never added to it. After a failure it waits for [retryMore], and
     * while a refresh is on its way it waits for that.
     */
    fun loadMore() {
        if (closed || !landed || !hasMore || mutable.value.paging != BooruPaging.IDLE) return
        if (isStale) return refresh()
        setPaging(BooruPaging.LOADING)
        more = scope.launch {
            try {
                appendNextPage()
                setPaging(if (hasMore) BooruPaging.IDLE else BooruPaging.END)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setPaging(BooruPaging.FAILED)
            }
        }
    }

    /** Asks again for the page that failed. */
    fun retryMore() {
        if (mutable.value.paging != BooruPaging.FAILED) return
        setPaging(BooruPaging.IDLE)
        loadMore()
    }

    /** Puts away the notice of a failed refresh; the posts from before stay. */
    fun dismissRefreshFailure() {
        mutable.update { it.copy(refreshFailure = null) }
    }

    /** Puts the posts the blacklist hid back in their places. */
    fun showHidden() {
        if (closed || showMuted) return
        showMuted = true
        mutable.update { it.copy(posts = (it.posts + muted).sortedBy { post -> order[post.key] ?: 0 }, hiddenShown = true) }
    }

    /** Hides again the posts [showHidden] put back. */
    fun hideHidden() {
        if (closed || !showMuted) return
        showMuted = false
        val hidden = muted.mapTo(HashSet()) { it.key }
        mutable.update { state -> state.copy(posts = state.posts.filterNot { it.key in hidden }, hiddenShown = false) }
    }

    /** Stops: nothing on its way changes the feed any more. */
    fun close() {
        closed = true
        refreshing?.cancel()
        more?.cancel()
    }

    /** The first page with posts, its number, and every page asked for on the way to it. */
    private suspend fun firstPage(): FirstPage {
        var current = BooruPostPage(emptyList(), next = "1")
        val pages = ArrayList<BooruPostPage>()
        while (!closed && pages.size < FIRST_PAGE_TRIES && current.next != null && current.posts.isEmpty()) {
            current = loader(pages.size + 1)
            pages += current
        }
        return FirstPage(current, pages.size, pages)
    }

    private fun land(first: FirstPage) {
        page = first.number
        hasMore = first.page.next != null
        muted.clear()
        order.clear()
        showMuted = false
        val posts = take(first.pages, HashSet())
        landed = true
        mutable.value = BooruFeedState(
            posts = posts,
            loading = false,
            paging = if (hasMore) BooruPaging.IDLE else BooruPaging.END,
            failedQueries = first.page.failedQueries,
            hidden = muted.size,
        )
    }

    /** A refresh that failed: the posts shown stay, else the copy kept on the device, else the feed shows why. */
    private suspend fun failed(error: Exception, shown: Shown?, cacheId: String?) {
        if (shown != null) return keep(shown, error, mutable.value.posts)
        val kept = cacheId?.let { cache?.read(it) }
        if (closed) return
        if (kept == null) {
            landed = true
            mutable.update { it.copy(posts = emptyList(), loading = false, error = error, failedQueries = 0, refreshFailure = null) }
            return
        }
        keep(Shown(kept.page, kept.hasMore, if (kept.hasMore) BooruPaging.IDLE else BooruPaging.END), error, kept.posts)
    }

    private fun keep(shown: Shown, error: Exception, posts: List<BooruPost>) {
        landed = true
        page = shown.page
        hasMore = shown.hasMore
        mutable.update { it.copy(posts = posts, loading = false, error = null, paging = shown.paging, refreshFailure = error) }
    }

    /** Null when none of this feed's posts are shown. A next page on its way is dropped by the refresh, so the footer goes back to idle. */
    private fun shown(): Shown? {
        val now = mutable.value
        if (now.posts.isEmpty() || isStale) return null
        return Shown(page, hasMore, if (now.paging == BooruPaging.LOADING) BooruPaging.IDLE else now.paging)
    }

    private fun cacheId(): String? {
        val key = cacheKey ?: return null
        return if (cache == null) null else key() + "\n" + feedKey()
    }

    /**
     * Adds the next page with posts not shown yet, skipping pages the filters
     * emptied while the host has more. A host that ignores the page number
     * sends the same posts forever, which ends the feed.
     */
    private suspend fun appendNextPage() {
        var repeats = 0
        for (attempt in 0 until MORE_PAGE_TRIES) {
            if (!hasMore) return
            val next = page + 1
            val loaded = loader(next)
            page = next
            hasMore = loaded.next != null
            val known = HashSet<String>().apply {
                mutable.value.posts.mapTo(this) { it.key }
                muted.mapTo(this) { it.key }
            }
            val fresh = take(listOf(loaded), known)
            if (fresh.isNotEmpty()) {
                mutable.update { it.copy(posts = it.posts + fresh, hidden = muted.size) }
                return
            }
            mutable.update { it.copy(hidden = muted.size) }
            if (loaded.posts.isNotEmpty()) repeats++
        }
        if (repeats == MORE_PAGE_TRIES) hasMore = false
    }

    /**
     * The posts of [pages] to show, in the host's order, noting those the
     * blacklist hid. Posts [known] already, shown or hidden, are skipped: new
     * uploads shift a host's pages, so a page can repeat earlier posts.
     */
    private fun take(pages: List<BooruPostPage>, known: MutableSet<String>): List<BooruPost> {
        val fresh = pages.flatMap { it.allowed }.filter { known.add(it.key) }
        val visible = pages.flatMap { it.posts }.mapTo(HashSet()) { it.key }
        fresh.forEach { order.putIfAbsent(it.key, order.size) }
        muted += fresh.filterNot { it.key in visible }
        return if (showMuted) fresh else fresh.filter { it.key in visible }
    }

    private fun setPaging(paging: BooruPaging) {
        if (!closed) mutable.update { it.copy(paging = paging) }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 50L
        const val FIRST_PAGE_TRIES = 4
        const val MORE_PAGE_TRIES = 3
    }
}
