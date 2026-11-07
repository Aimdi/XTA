package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.reddit.FlatComment
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditCommentSort
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditThread
import com.aimdi.xtanative.networks.reddit.flattenComments
import com.aimdi.xtanative.networks.reddit.withoutShownThreads
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [block], or the read failure it ended in; cancellation goes on up. */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * The followed communities as one feed (XTA's `RedditFeedStore`): [read]
 * gives the posts of some communities, newest first. What is shown is read
 * again only when asked to, by a pull ([refresh] with force, which goes past
 * the shared cache too), when the communities change, or once it is older
 * than [FRESH_MILLIS]; a read under way is joined. A failed read keeps the
 * posts already shown.
 */
internal class RedditFeed(
    private val scope: CoroutineScope,
    private val read: suspend (names: List<String>, force: Boolean) -> List<RedditPost>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class State(
        val posts: List<RedditPost> = emptyList(),
        val loading: Boolean = false,
        /** A first answer has arrived. */
        val loaded: Boolean = false,
        val problem: Throwable? = null,
        /** Whether any community is followed. */
        val following: Boolean = false,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private var job: Job? = null
    private var names: List<String>? = null
    private var fetchedAt = 0L

    @Synchronized
    fun refresh(names: List<String>, force: Boolean = false) {
        if (names.isEmpty()) {
            job?.cancel()
            this.names = names
            mutable.value = State(loaded = true)
            return
        }
        val same = names == this.names
        if (!force && same && (job?.isActive == true || (mutable.value.posts.isNotEmpty() && clock() - fetchedAt <= FRESH_MILLIS))) return
        job?.cancel()
        this.names = names
        mutable.update { it.copy(loading = true, problem = null, following = true) }
        job = scope.launch {
            attempt { read(names, force) }
                .onSuccess { posts ->
                    fetchedAt = clock()
                    mutable.value = State(posts = posts.distinctBy { it.id }, loaded = true, following = true)
                }
                .onFailure { error ->
                    mutable.update { shown -> shown.copy(loading = false, loaded = true, problem = if (shown.posts.isEmpty()) error else null) }
                }
        }
    }

    companion object {
        /** As long as XTA shows a plugin's feed again without reading it: moving back to the tab costs nothing. */
        const val FRESH_MILLIS = 10 * 60 * 1000L
    }
}

/**
 * What the followed communities' feed shows (reddit_feed_list.dart): where to
 * start while nothing is followed or nothing posted, why a first read failed,
 * post-shaped bones while it is on its way (never a bare spinner), or the posts.
 */
internal enum class RedditFeedBody {
    NOTHING_FOLLOWED, PROBLEM, BONES, EMPTY, POSTS;

    companion object {
        /** [listed]: the followed communities have been read; [none]: and there are none. */
        fun of(listed: Boolean, none: Boolean, state: RedditFeed.State): RedditFeedBody = when {
            listed && none -> NOTHING_FOLLOWED
            state.posts.isNotEmpty() -> POSTS
            state.problem != null -> PROBLEM
            !listed || state.loading || !state.loaded -> BONES
            else -> EMPTY
        }
    }
}

/**
 * One thread as its screen shows it (XTA's `RedditThreadScreen`): the post,
 * filled in when it arrived with only a title, its comments in the order
 * chosen, what is folded, and the held-back comments loading in place.
 * [visit] notes that it is open and says when it was opened before, for the
 * comments new since.
 */
internal class RedditThreadState(
    first: RedditPost,
    private val scope: CoroutineScope,
    private val visit: suspend () -> Instant?,
    private val read: suspend (permalink: String, sort: RedditCommentSort) -> RedditThread,
    private val fill: suspend (permalink: String) -> RedditPost?,
    private val more: suspend (RedditComment) -> List<RedditComment>,
) {
    data class State(
        val post: RedditPost,
        val selfText: String?,
        /** Null until they arrive. */
        val comments: List<FlatComment>? = null,
        val problem: Throwable? = null,
        val sort: RedditCommentSort = RedditCommentSort.BEST,
        val collapsed: Set<String> = emptySet(),
        /** Stubs whose held-back comments are on their way. */
        val loadingMore: Set<String> = emptySet(),
        /** Reddit refused to hand over held-back comments once: the rest are read on Reddit. */
        val moreRefused: Boolean = false,
        /** When the reader opened the thread before this time; null on a first visit. */
        val lastVisit: Instant? = null,
    )

    private val mutable = MutableStateFlow(State(first, first.selfText))
    val state: StateFlow<State> = mutable.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch { attempt(visit).getOrNull()?.let { last -> mutable.update { it.copy(lastVisit = last) } } }
        load()
        if (first.arrivedBare) scope.launch { attempt { fill(first.permalink) }.getOrNull()?.let(::filled) }
    }

    fun load() {
        job?.cancel()
        mutable.update { it.copy(problem = null) }
        val sort = mutable.value.sort
        job = scope.launch {
            attempt { read(mutable.value.post.permalink, sort) }
                .onSuccess { thread -> mutable.update { it.read(thread) } }
                .onFailure { error -> mutable.update { it.copy(problem = error) } }
        }
    }

    fun resort(sort: RedditCommentSort) {
        mutable.update { it.copy(sort = sort, comments = null, collapsed = emptySet()) }
        load()
    }

    fun toggle(id: String) = mutable.update { it.copy(collapsed = if (id in it.collapsed) it.collapsed - id else it.collapsed + id) }

    fun toggleAll() = mutable.update { state -> state.comments?.let { state.copy(collapsed = RedditThreadRules.toggledAll(it, state.collapsed)) } ?: state }

    /** The comments [stub] held back, in its place; [refused] when Reddit will not hand them over, for the thread to be read on Reddit. */
    fun loadMore(stub: RedditComment, refused: () -> Unit) {
        mutable.update { it.copy(loadingMore = it.loadingMore + stub.id) }
        scope.launch {
            attempt { more(stub) }
                .onSuccess { comments ->
                    mutable.update { state ->
                        val shown = state.comments.orEmpty()
                        val rows = withoutShownThreads(shown, flattenComments(comments))
                        state.copy(comments = RedditThreadRules.replacing(shown, stub, rows), loadingMore = state.loadingMore - stub.id)
                    }
                }
                .onFailure {
                    mutable.update { state -> state.copy(moreRefused = true, loadingMore = state.loadingMore - stub.id) }
                    refused()
                }
        }
    }

    private fun filled(fetched: RedditPost) = mutable.update { it.copy(post = it.post.filledFrom(fetched), selfText = it.selfText ?: fetched.selfText) }

    private fun State.read(thread: RedditThread): State = copy(
        post = RedditThreadRules.withPageMedia(post, thread.postUrl, thread.postImages),
        selfText = selfText ?: thread.selfText,
        comments = flattenComments(thread.comments),
    )
}
