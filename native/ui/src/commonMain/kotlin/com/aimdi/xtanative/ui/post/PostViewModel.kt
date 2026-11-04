package com.aimdi.xtanative.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XWithheld
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.offline.TimelineCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostUi(
    val focal: XPost? = null,
    val before: List<XPost> = emptyList(),
    /** The posts it answers that X withheld, in their place above it. */
    val withheldBefore: List<XWithheld> = emptyList(),
    /** Why there is no post: X withheld it, or sent nothing for it. */
    val focalWithheld: XWithheld? = null,
    val replies: List<XThread> = emptyList(),
    val loading: Boolean = true,
    val problem: Problem? = null,
    /** X answered once since the screen opened. */
    val answered: Boolean = false,
    val cursor: String? = null,
    /** X's "Show additional replies", where the thread ends. */
    val showMore: String? = null,
    /** X counted replies and sent none: a retry is offered. */
    val repliesMissing: Boolean = false,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
    val sort: ReplySort = ReplySort.RELEVANT,
    /** Zen mode's replies, shown once the reader held the reply button. */
    val repliesRevealed: Boolean = false,
) {
    /** X answered for the post with nothing at all, which is how it answers for a deleted one. */
    val gone: Boolean get() = answered && focal == null
}

/**
 * An opened post (status.dart): drawn at once from what the feed showed,
 * then its conversation from X in the reply order the reader chose for this
 * run, page after page without the replies X sends twice, and X's "Show
 * additional replies" left to the reader.
 */
class PostViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    private val paging = ConversationPaging(id)
    private val state = MutableStateFlow(PostUi(focal = graph.posts.post(id), sort = Sorts.state.value.replies))
    val ui: StateFlow<PostUi> = state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    /** Reads the conversation from its top; [bypassCache] asks X again, as a retry for replies it never sent does. */
    fun load(bypassCache: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null, moreProblem = null, repliesMissing = false) }
            try {
                val conversation = firstPage(state.value.sort, bypassCache)
                remember(conversation)
                val expected = (conversation.focal ?: state.value.focal)?.replyCount ?: 0
                val step = paging.first(conversation, expected)
                state.update {
                    it.copy(
                        focal = conversation.focal ?: it.focal,
                        before = conversation.before,
                        withheldBefore = conversation.withheldBefore,
                        focalWithheld = conversation.focalWithheld,
                        replies = step.replies,
                        cursor = step.next,
                        showMore = step.showMore,
                        repliesMissing = step.repliesMissing,
                        loading = false,
                        answered = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    /**
     * The first page, from the device when it was read in the last ten
     * minutes in X's own order, from X otherwise; at any age when X gives
     * nothing. Re-opening a post is the commonest thing a reader does.
     */
    private suspend fun firstPage(sort: ReplySort, bypassCache: Boolean): XConversation {
        if (!ThreadCache.keeps(sort)) return XConversation.of(graph.client.conversation(id, ranking = Sorts.ranking(sort)), id)
        val cache = graph.timelineCache
        val key = TimelineCache.threadKey(id)
        if (bypassCache) cache.remove(key) else cache.read(key, ThreadCache.MAX_AGE_MS)?.let { return XConversation.of(it.page(), id) }
        return try {
            val page = graph.client.conversation(id, ranking = Sorts.ranking(sort))
            val conversation = XConversation.of(page, id)
            val expected = (conversation.focal ?: state.value.focal)?.replyCount ?: 0
            if (ThreadCache.worthKeeping(conversation, expected)) cache.write(key, page.cached())
            conversation
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            cache.readStale(key)?.let { XConversation.of(it.page(), id) } ?: throw error
        }
    }

    fun loadMore() {
        val now = state.value
        val cursor = now.cursor ?: return
        if (now.loadingMore || now.loading) return
        next(cursor)
    }

    /** The replies X held back as low quality: the reader asked for them. */
    fun showMoreReplies() {
        val cursor = state.value.showMore ?: return
        state.update { it.copy(showMore = null) }
        next(cursor)
    }

    private fun next(cursor: String) {
        job = viewModelScope.launch {
            state.update { it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = XConversation.of(graph.client.conversation(id, cursor, Sorts.ranking(state.value.sort)), id)
                remember(page)
                val step = paging.next(page, cursor)
                state.update {
                    it.copy(
                        replies = it.replies + step.replies,
                        cursor = step.next,
                        showMore = step.showMore,
                        repliesMissing = it.repliesMissing && step.replies.none { thread -> thread.posts.isNotEmpty() },
                        loadingMore = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
            }
        }
    }

    /** Replies in another order, kept for the rest of the run, read from the top again. */
    fun select(sort: ReplySort) {
        if (sort == state.value.sort) return
        Sorts.selectReplies(sort)
        state.update { it.copy(sort = sort, replies = emptyList(), cursor = null, showMore = null) }
        load()
    }

    fun revealReplies() = state.update { it.copy(repliesRevealed = true) }

    private fun remember(conversation: XConversation) {
        graph.posts.remember(conversation.before.map { XThread(it.id, listOf(it)) } + listOfNotNull(conversation.focal?.let { XThread(it.id, listOf(it)) }) + conversation.replies)
    }
}
