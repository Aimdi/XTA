package com.aimdi.xtanative.networks.substack

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The home feed as its screens follow it, as XTA's SubstackFeedStore: the
 * merged [SubstackFeed] of the followed publications, whether a refresh or
 * a next page is under way, and what failed. The first follows paint what
 * the last start [recall]s; each answer is [remember]ed for the next. A
 * refresh that is not forced leaves a feed alone that answered whole within
 * [SubstackFeed.FRESH_MS]. Its calls are made from one thread (the screen's),
 * and [scope] runs there.
 */
class SubstackHomeFeed(
    reads: SubstackPostReads,
    private val scope: CoroutineScope,
    private val recall: suspend () -> Map<String, SubstackRememberedPage> = { emptyMap() },
    private val remember: suspend (Map<String, SubstackRememberedPage>) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class State(
        /** Every loaded post, one copy each, newest first. */
        val posts: List<SubstackPost> = emptyList(),
        val publications: List<SubstackPublication> = emptyList(),
        val canLoadMore: Boolean = false,
        val failedCount: Int = 0,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val refreshError: Throwable? = null,
        val loadMoreError: Throwable? = null,
        /** The follows are known and a first answer, or what was remembered, is in. */
        val loaded: Boolean = false,
    ) {
        /** Some publications failed, or the whole refresh did, while posts may stay on screen. */
        val failed: Boolean get() = failedCount > 0 || refreshError != null
    }

    private val feed = SubstackFeed(reads, emptyList(), clock = clock)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private var identity: Set<String>? = null
    private var loadedIdentity: Set<String>? = null
    private var fetchedAt: Long? = null
    private var recalled = false
    private var job: Job? = null

    /** Follows [publications] from now on, and reads what that changed, as a refresh that is not forced. */
    fun follow(publications: List<SubstackPublication>) {
        val next = publications.mapTo(HashSet()) { "${it.id}\n${it.baseUrl}" }
        if (next == identity) return
        identity = next
        mutable.update { it.copy(publications = publications) }
        scope.launch {
            feed.follow(publications)
            if (!recalled) {
                recalled = true
                if (feed.restore(safely { recall() } ?: emptyMap())) publish(feed.snapshot())
            }
            if (publications.isEmpty()) {
                mutable.update { it.copy(posts = emptyList(), canLoadMore = false, failedCount = 0, refreshError = null, loaded = true) }
            } else {
                refresh(force = false)
            }
        }
    }

    fun refresh(force: Boolean = true) {
        val now = state.value
        if (now.publications.isEmpty() || (!force && now.refreshing)) return
        val fresh = fetchedAt?.let { clock() - it < SubstackFeed.FRESH_MS } == true
        if (!force && loadedIdentity == identity && !now.failed && fresh) return
        load(more = false, force = force)
    }

    /** The next page; a refresh instead when the follows changed since the last answer. */
    fun loadMore() {
        val now = state.value
        if (now.refreshing || now.loadingMore || !now.canLoadMore) return
        if (loadedIdentity != null && loadedIdentity != identity) return refresh()
        load(more = true, force = true)
    }

    private fun load(more: Boolean, force: Boolean) {
        job?.cancel()
        val asked = identity
        mutable.update { it.copy(refreshing = !more, loadingMore = more, loadMoreError = null, refreshError = if (more) it.refreshError else null) }
        job = scope.launch {
            val snapshot = try {
                if (more) feed.more() else feed.refresh(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (asked == identity) failed(e, more)
                return@launch
            }
            if (asked == identity) answered(snapshot, more, asked)
        }
    }

    private suspend fun answered(snapshot: SubstackFeed.Snapshot, more: Boolean, asked: Set<String>?) {
        loadedIdentity = asked
        val failure = snapshot.failures.firstOrNull()
        if (failure == null) fetchedAt = clock()
        publish(snapshot)
        mutable.update {
            if (more) it.copy(loadingMore = false, loadMoreError = failure, refreshError = if (failure == null) null else it.refreshError)
            else it.copy(refreshing = false, refreshError = failure)
        }
        safely { remember(feed.remembered()) }
    }

    private fun failed(error: Throwable, more: Boolean) = mutable.update {
        if (more) it.copy(loadingMore = false, loadMoreError = error) else it.copy(refreshing = false, refreshError = error, loaded = true)
    }

    private fun publish(snapshot: SubstackFeed.Snapshot) {
        mutable.update { it.copy(posts = snapshot.posts, canLoadMore = snapshot.canLoadMore, failedCount = snapshot.failedCount, loaded = true) }
    }

    /** [block], or null when it fails: only a faster next start is lost. */
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
