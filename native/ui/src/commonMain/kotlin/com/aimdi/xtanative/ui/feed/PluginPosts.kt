package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** A plugin's post in a shared timeline, from plugin [source]. */
data class SourcedPost(val source: String, val post: NetworkPost) {
    /** What tells its row apart from every other, X's included. */
    val key: String get() = "$source:${post.id}"

    /** Its place in time, in epoch milliseconds. */
    val time: Long get() = post.createdAt?.toEpochMilli() ?: Long.MIN_VALUE
}

/**
 * One plugin's part of a timeline: what it answered last, whether it is
 * reading, and why its last read failed; [cachedAt] (epoch milliseconds) when
 * its posts are the copy kept from an earlier answer ([SourceSnapshots]).
 */
data class SourceRead(val posts: List<SourcedPost> = emptyList(), val loading: Boolean = false, val problem: Problem? = null, val cachedAt: Long? = null) {
    /** Worth a chip over the timeline (XTA's _worthAChip): a failure to retry or explain, or a kept copy to explain. */
    val worthAChip: Boolean get() = problem != null || (cachedAt != null && !loading)
}

/** What a source answered before, kept on the device, and when ([at], epoch milliseconds). */
data class SavedAnswer(val at: Long, val posts: List<NetworkPost>)

/**
 * Where a timeline keeps what each plugin last answered across launches
 * (XTA's FeedSnapshotCache), shown at once the next time while the plugin is
 * asked again. A copy that cannot be read or written is a miss.
 */
interface SourceSnapshots {
    suspend fun read(source: String, ask: SourceAsk): SavedAnswer?

    suspend fun write(source: String, ask: SourceAsk, posts: List<NetworkPost>)

    /** Nothing kept: each plugin starts empty. */
    object None : SourceSnapshots {
        override suspend fun read(source: String, ask: SourceAsk): SavedAnswer? = null

        override suspend fun write(source: String, ask: SourceAsk, posts: List<NetworkPost>) {}
    }
}

/**
 * A timeline's plugin posts at one moment: each source's part, and what
 * sources answered while the reader was down the list ([held], XTA's pending
 * posts), shown once they are back at the top or ask for it.
 */
data class PluginPostsState(val sources: Map<String, SourceRead> = emptyMap(), val held: Map<String, List<SourcedPost>> = emptyMap()) {
    /** Every source's posts shown, newest first. */
    val shown: List<SourcedPost> by lazy { sources.values.flatMap { it.posts }.sortedByDescending { it.time } }

    /** The held posts not shown yet: what the new-posts pill counts. */
    val newHeld: Int by lazy {
        val shownKeys = shown.mapTo(HashSet()) { it.key }
        held.values.sumOf { posts -> posts.count { it.key !in shownKeys } }
    }

    /** The sources whose last read failed, with why. */
    val failed: Map<String, Problem> get() = sources.mapNotNull { (id, read) -> read.problem?.let { id to it } }.toMap()

    /** Some source is reading. */
    val loading: Boolean get() = sources.values.any { it.loading }

    /** The sources worth a chip over the timeline, in their order. */
    val chips: Map<String, SourceRead> get() = sources.filterValues { it.worthAChip }

    /** What was held, shown. */
    fun revealed(): PluginPostsState =
        if (held.isEmpty()) this else copy(sources = sources + held.mapValues { (id, posts) -> (sources[id] ?: SourceRead()).copy(posts = posts) }, held = emptyMap())
}

/**
 * The plugin posts a shared timeline mixes in (XTA's ProgressiveFeedStore):
 * each plugin is read on its own, so one that fails or is slow holds none of
 * the others back, and its failure is its own. What one answers replaces what
 * it showed, unless the reader is down the list with posts on screen: then it
 * is held, and shown once they are back at the top or ask for it, so nothing
 * moves under their eyes. [read] answers a plugin's posts for what is asked
 * of it; [problemOf] says why a read failed. A plugin with nothing shown
 * shows what [snapshots] kept of its last answer while it is asked again, and
 * one that takes longer than [timeoutMillis] has timed out. Called from one
 * thread, the screen's, as a view model's scope runs.
 */
class PluginPosts(
    private val scope: CoroutineScope,
    private val read: suspend (source: String, ask: SourceAsk, force: Boolean) -> List<NetworkPost>,
    private val problemOf: (Throwable) -> Problem = Problem::of,
    private val snapshots: SourceSnapshots = SourceSnapshots.None,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) {
    private val mutable = MutableStateFlow(PluginPostsState())
    val state: StateFlow<PluginPostsState> = mutable.asStateFlow()

    private var asks: Map<String, SourceAsk> = emptyMap()
    private val jobs = HashMap<String, Job>()
    private var away = false

    /** What is asked of each plugin now. */
    val asked: Map<String, SourceAsk> get() = asks

    /**
     * Reads every plugin of [next] (its id to what is asked of it) again, each
     * on its own. A plugin no longer asked leaves with its posts; one asked
     * something else starts empty; the others show what they showed until they
     * answer. [force] reads past the plugins' caches, as a pull does.
     */
    fun load(next: Map<String, SourceAsk>, force: Boolean = false) {
        val previous = asks
        asks = next
        (previous.keys - next.keys).forEach { jobs.remove(it)?.cancel() }
        mutable.update { state ->
            state.copy(
                sources = state.sources.filterKeys { it in next }.mapValues { (id, part) -> if (previous[id] == next[id]) part else SourceRead() },
                held = state.held.filterKeys { it in next && previous[it] == next[it] },
            )
        }
        next.keys.forEach { start(it, force) }
    }

    /** Reads [source] again, as the retry of its failure does. */
    fun retry(source: String) {
        if (source in asks) start(source, force = false)
    }

    /** Whether the reader is down the list; back at the top, what was held is shown. */
    fun readingAway(away: Boolean) {
        this.away = away
        if (!away) reveal()
    }

    /** Shows what was held, as the new-posts pill does. */
    fun reveal() = mutable.update { it.revealed() }

    private fun start(source: String, force: Boolean) {
        val ask = asks[source] ?: return
        jobs.remove(source)?.cancel()
        part(source) { it.copy(loading = true, problem = null) }
        jobs[source] = scope.launch {
            if (mutable.value.sources[source]?.posts.isNullOrEmpty()) showSaved(source, ask)
            val answer = try {
                Result.success(withTimeout(timeoutMillis) { read(source, ask, force) })
            } catch (e: TimeoutCancellationException) {
                Result.failure(SourceTimedOut())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            if (asks[source] != ask) return@launch
            answer.fold(
                onSuccess = { posts ->
                    val answered = answered(source, posts)
                    accept(source, answered, SourceRead(answered))
                    scope.launch { runCatching { snapshots.write(source, ask, answered.map { it.post }) } }
                },
                onFailure = { error ->
                    val problem = if (error is SourceTimedOut) Problem.TimedOut else problemOf(error)
                    part(source) { it.copy(loading = false, problem = problem) }
                },
            )
        }
    }

    /** What [snapshots] kept of [source]'s last answer, shown while it is asked again; a slow or damaged copy is passed over. */
    private suspend fun showSaved(source: String, ask: SourceAsk) {
        val saved = try {
            withTimeoutOrNull(SAVED_TIMEOUT_MILLIS) { snapshots.read(source, ask) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        } ?: return
        if (asks[source] != ask || saved.posts.isEmpty() || !mutable.value.sources[source]?.posts.isNullOrEmpty()) return
        val posts = answered(source, saved.posts)
        accept(source, posts, SourceRead(posts, loading = true, cachedAt = saved.at))
    }

    private fun answered(source: String, posts: List<NetworkPost>): List<SourcedPost> =
        posts.filter { it.createdAt != null }.map { SourcedPost(source, it) }.distinctBy { it.key }

    private fun accept(source: String, answered: List<SourcedPost>, read: SourceRead) = mutable.update { state ->
        val hold = away && state.sources.values.any { it.posts.isNotEmpty() }
        if (hold) {
            val waiting = (state.sources[source] ?: SourceRead()).copy(loading = read.loading, problem = read.problem, cachedAt = read.cachedAt)
            state.copy(sources = state.sources + (source to waiting), held = state.held + (source to answered))
        } else {
            state.revealed().let { shown -> shown.copy(sources = shown.sources + (source to read)) }
        }
    }

    private fun part(source: String, change: (SourceRead) -> SourceRead) =
        mutable.update { state -> state.copy(sources = state.sources + (source to change(state.sources[source] ?: SourceRead()))) }

    /** A read that took longer than the timeline waits. */
    private class SourceTimedOut : Exception()

    companion object {
        /** How long one plugin may take before it has timed out, as XTA's ProgressiveFeedStore waits. */
        const val TIMEOUT_MILLIS = 30_000L

        /** How long the copy kept on the device may take to read before it is passed over. */
        const val SAVED_TIMEOUT_MILLIS = 1_000L
    }
}
