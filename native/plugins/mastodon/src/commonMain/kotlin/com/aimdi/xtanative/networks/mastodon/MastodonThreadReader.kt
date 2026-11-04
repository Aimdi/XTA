package com.aimdi.xtanative.networks.mastodon

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * An opened post and its public conversation, as XTA's MastodonThreadStore
 * holds it: read from the first of [instances] that finds the post, its
 * replies folded and unfolded, the posts above it shown on request, the
 * author's own thread picked out, and the replies ordered as asked.
 */
class MastodonThreadReader(
    private val client: MastodonClient,
    private val instances: () -> List<String>,
    seed: MastodonPost,
    order: MastodonReplyOrder,
    private val scope: CoroutineScope,
) {
    data class State(
        val thread: MastodonThread,
        /** The replies whose own replies are folded away. */
        val collapsed: Set<String> = emptySet(),
        val ancestorsOpen: Boolean = false,
        val authorOnly: Boolean = false,
        val order: MastodonReplyOrder = MastodonReplyOrder.OLDEST,
        val loading: Boolean = false,
        val error: Throwable? = null,
    ) {
        val replies: List<MastodonReplyRow> get() = MastodonThreads.replies(thread, collapsed, authorOnly, order)

        val ancestors: List<MastodonPost> get() = MastodonThreads.ancestors(thread)
    }

    private val mutable = MutableStateFlow(State(MastodonThread(seed), order = order))
    val state: StateFlow<State> = mutable.asStateFlow()
    private val request = AtomicInteger()

    /** Folds the replies under [id], or unfolds them. */
    fun toggle(id: String) = mutable.update { it.copy(collapsed = if (id in it.collapsed) it.collapsed - id else it.collapsed + id) }

    fun toggleAncestors() = mutable.update { it.copy(ancestorsOpen = !it.ancestorsOpen) }

    /** Only the author's replies and those leading to them, or all; either unfolds everything. */
    fun selectAuthor(only: Boolean) = mutable.update { if (it.authorOnly == only) it else it.copy(authorOnly = only, collapsed = emptySet()) }

    fun selectOrder(order: MastodonReplyOrder) = mutable.update { it.copy(order = order) }

    /** Every reply unfolded, or every reply that has replies folded. */
    fun setAllExpanded(expanded: Boolean) = mutable.update { now ->
        val folded = if (expanded) emptySet() else MastodonThreads.replies(now.thread, emptySet(), now.authorOnly).filter { it.descendants > 0 }.map { it.post.id }.toSet()
        now.copy(collapsed = folded)
    }

    /** Back to the opened post: the posts above it put away. */
    fun focusSelected() = mutable.update { it.copy(ancestorsOpen = false) }

    /** The conversation read again; folds of replies still there are kept. */
    fun refresh() {
        val started = request.incrementAndGet()
        mutable.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val thread = client.threadAnywhere(instances(), state.value.thread.status)
                if (request.get() != started) return@launch
                val ids = thread.descendants.map { it.id }.toSet()
                mutable.update { it.copy(thread = thread, collapsed = it.collapsed intersect ids, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request.get() == started) mutable.update { it.copy(loading = false, error = e) }
            }
        }
    }
}
