package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A plugin's post in a shared timeline, from plugin [source]. */
data class SourcedPost(val source: String, val post: NetworkPost) {
    /** What tells its row apart from every other, X's included. */
    val key: String get() = "$source:${post.id}"

    /** Its place in time, in epoch milliseconds. */
    val time: Long get() = post.createdAt?.toEpochMilli() ?: Long.MIN_VALUE
}

/** One plugin's part of a timeline: what it answered last, whether it is reading, and why its last read failed. */
data class SourceRead(val posts: List<SourcedPost> = emptyList(), val loading: Boolean = false, val problem: Problem? = null)

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
 * of it; [problemOf] says why a read failed. Called from one thread, the
 * screen's, as a view model's scope runs.
 */
class PluginPosts(
    private val scope: CoroutineScope,
    private val read: suspend (source: String, ask: SourceAsk, force: Boolean) -> List<NetworkPost>,
    private val problemOf: (Throwable) -> Problem = Problem::of,
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
            val answer = try {
                Result.success(read(source, ask, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            if (asks[source] != ask) return@launch
            answer.fold(
                onSuccess = { posts -> accept(source, posts) },
                onFailure = { error -> part(source) { it.copy(loading = false, problem = problemOf(error)) } },
            )
        }
    }

    private fun accept(source: String, posts: List<NetworkPost>) = mutable.update { state ->
        val answered = posts.filter { it.createdAt != null }.map { SourcedPost(source, it) }.distinctBy { it.key }
        val hold = away && state.sources.values.any { it.posts.isNotEmpty() }
        if (hold) {
            state.copy(sources = state.sources + (source to (state.sources[source] ?: SourceRead()).copy(loading = false)), held = state.held + (source to answered))
        } else {
            state.revealed().let { shown -> shown.copy(sources = shown.sources + (source to SourceRead(answered))) }
        }
    }

    private fun part(source: String, change: (SourceRead) -> SourceRead) =
        mutable.update { state -> state.copy(sources = state.sources + (source to change(state.sources[source] ?: SourceRead()))) }
}
