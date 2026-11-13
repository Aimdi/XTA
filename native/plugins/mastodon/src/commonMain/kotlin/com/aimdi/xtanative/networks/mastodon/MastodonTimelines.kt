package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.networks.Page
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one of the reader's timelines stands, as XTA's timeline stores hold it. */
data class MastodonTimelineState(
    val posts: List<MastodonPost> = emptyList(),
    /** Explore's trending tags. */
    val tags: List<MastodonTag> = emptyList(),
    /** The instance that served it: its later pages are read there, and Local and Federated name it. */
    val instance: String? = null,
    /** A read is under way; what is shown stays meanwhile. */
    val loading: Boolean = false,
    /** A first read has finished, or posts were restored. */
    val loaded: Boolean = false,
    /** Why the last read failed: over what it kept, or in place of a list that has nothing. */
    val error: Throwable? = null,
    val loadingMore: Boolean = false,
    val moreError: Throwable? = null,
    /** Where the next page starts; null when there is none. */
    val next: String? = null,
) {
    val isEmpty: Boolean get() = posts.isEmpty() && tags.isEmpty()

    val canLoadMore: Boolean get() = next != null && !loading && !loadingMore && moreError == null && posts.isNotEmpty()
}

/**
 * One timeline read in [scope], as XTA's _MastodonReadStore reads it: a read
 * keeps what is shown until it answers, and a read overtaken by a newer one
 * or by [forget] changes nothing. Pages after the first are read on the
 * instance that served the first, ids being local to it.
 */
abstract class MastodonTimelineReader(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(MastodonTimelineState())
    val state: StateFlow<MastodonTimelineState> = mutable.asStateFlow()

    private val generation = AtomicInteger()
    private var job: Job? = null

    /** The first page read again. */
    fun refresh() = read { paint -> first(paint) }

    /** The next page, unless one is being read, failed, or there is none. */
    fun loadMore() {
        val now = state.value
        val instance = now.instance
        val cursor = now.next
        if (!now.canLoadMore || instance == null || cursor == null) return
        val started = generation.get()
        mutable.update { it.copy(loadingMore = true, moreError = null) }
        scope.launch {
            try {
                val page = more(instance, cursor)
                if (generation.get() == started) {
                    mutable.update { it.copy(posts = MastodonFeeds.unique(it.posts, page.items), next = page.next?.takeIf { next -> next != cursor }, loadingMore = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation.get() == started) mutable.update { it.copy(loadingMore = false, moreError = e) }
            }
        }
    }

    /** The page that failed, read again. */
    fun retryMore() {
        if (state.value.moreError == null) return
        mutable.update { it.copy(moreError = null) }
        loadMore()
    }

    /** Everything shown forgotten, and reads under way dropped: the instances changed. */
    open fun forget() {
        generation.incrementAndGet()
        job?.cancel()
        mutable.value = MastodonTimelineState()
    }

    /** [posts] shown at once, as they were left, while nothing else is: the next page goes on below the last of them. */
    fun restore(posts: List<MastodonPost>, instance: String?, tags: List<MastodonTag> = emptyList()) {
        if (posts.isEmpty() && tags.isEmpty()) return
        mutable.update { now ->
            if (!now.isEmpty || now.loading) now
            else now.copy(posts = MastodonFeeds.unique(emptyList(), posts), tags = tags, instance = instance, next = posts.lastOrNull()?.pagingId, loaded = true)
        }
    }

    /** The first page; [paint] shows what it has so far, for a read that arrives in parts. */
    protected abstract suspend fun first(paint: (List<MastodonPost>) -> Unit): MastodonTimelineState

    protected open suspend fun more(instance: String, cursor: String): Page<MastodonPost> = Page(emptyList(), null)

    private fun read(load: suspend (paint: (List<MastodonPost>) -> Unit) -> MastodonTimelineState) {
        val started = generation.incrementAndGet()
        job?.cancel()
        mutable.update { it.copy(loading = true, error = null, loadingMore = false, moreError = null) }
        val paint = { posts: List<MastodonPost> -> if (generation.get() == started) mutable.update { it.copy(posts = posts) } }
        job = scope.launch {
            try {
                val read = load(paint)
                if (generation.get() == started) mutable.value = read.copy(loading = false, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation.get() == started) mutable.update { it.copy(loading = false, loaded = true, error = e) }
            }
        }
    }

    companion object {
        /** Public timelines and hashtags come thirty a page, as XTA asks for them. */
        const val PAGE_SIZE = 30

        /** A timeline's first page, as its state: on [instance], from where it goes on. */
        fun firstPage(page: FromInstance<Page<MastodonPost>>): MastodonTimelineState =
            MastodonTimelineState(posts = MastodonFeeds.unique(emptyList(), page.value.items), instance = page.instance, next = page.value.next)
    }
}

/** The Local or Federated timeline of the first of the reader's instances, then the built-in ones, that gives it. */
class MastodonPublicReader(
    private val client: MastodonClient,
    private val instances: () -> List<String>,
    val local: Boolean,
    scope: CoroutineScope,
) : MastodonTimelineReader(scope) {
    override suspend fun first(paint: (List<MastodonPost>) -> Unit): MastodonTimelineState =
        firstPage(client.publicTimelineAnywhere(instances(), local, PAGE_SIZE))

    override suspend fun more(instance: String, cursor: String): Page<MastodonPost> = client.publicTimeline(instance, local, PAGE_SIZE, maxId = cursor)
}

/** A hashtag's public posts, from the first instance that has them. */
class MastodonTagReader(
    private val client: MastodonClient,
    private val instances: () -> List<String>,
    val tag: String,
    scope: CoroutineScope,
) : MastodonTimelineReader(scope) {
    override suspend fun first(paint: (List<MastodonPost>) -> Unit): MastodonTimelineState =
        firstPage(client.tagTimelineAnywhere(instances(), tag, PAGE_SIZE))

    override suspend fun more(instance: String, cursor: String): Page<MastodonPost> = client.tagTimeline(instance, tag, PAGE_SIZE, maxId = cursor)
}

/** Explore: trending tags and posts (Phanpy and Tusky's Explore); the tags are a nicety the posts never wait for. */
class MastodonExploreReader(
    private val client: MastodonClient,
    private val instances: () -> List<String>,
    scope: CoroutineScope,
) : MastodonTimelineReader(scope) {
    override suspend fun first(paint: (List<MastodonPost>) -> Unit): MastodonTimelineState =
        client.explore(instances()).let { MastodonTimelineState(posts = it.posts, tags = it.tags) }
}

/**
 * Following: each followed account's posts merged newest first
 * ([MastodonFollowing]); the timeline grows as accounts answer, over what it
 * showed before.
 */
class MastodonFollowingReader(
    private val following: MastodonFollowing,
    private val accts: () -> List<String>,
    scope: CoroutineScope,
) : MastodonTimelineReader(scope) {
    @Volatile
    private var force = false

    /** Read again; with [force], every account is asked again, not only those read more than ten minutes ago. */
    fun refresh(force: Boolean) {
        this.force = force
        refresh()
    }

    override suspend fun first(paint: (List<MastodonPost>) -> Unit): MastodonTimelineState {
        val shown = state.value.posts
        val posts = following.posts(accts(), force) { partial -> paint(merged(partial + shown)) }
        return MastodonTimelineState(posts = posts)
    }

    override fun forget() {
        following.forget()
        super.forget()
    }

    private fun merged(posts: List<MastodonPost>): List<MastodonPost> =
        MastodonFeeds.unique(emptyList(), posts).sortedByDescending { it.timelineDate ?: Instant.MIN }

    companion object {
        /** What of a kept timeline still belongs in Following: posts of [follows], or boosted by them. */
        fun kept(posts: List<MastodonPost>, follows: (String) -> Boolean): List<MastodonPost> =
            posts.filter { follows(it.acct) || it.boostedByAcct?.let(follows) == true }
    }
}
