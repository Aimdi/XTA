package com.aimdi.xtanative.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostUi(
    val focal: XPost? = null,
    val before: List<XPost> = emptyList(),
    val replies: List<XThread> = emptyList(),
    val loading: Boolean = true,
    val problem: Problem? = null,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
)

/** An opened post: drawn at once from what the feed showed, then its conversation from X. */
class PostViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    private val state = MutableStateFlow(PostUi(focal = graph.posts.post(id)))
    val ui: StateFlow<PostUi> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val conversation = XConversation.of(graph.client.conversation(id), id)
                graph.posts.remember(conversation.before.map { XThread(it.id, listOf(it)) } + conversation.replies)
                state.update {
                    it.copy(
                        focal = conversation.focal ?: it.focal,
                        before = conversation.before,
                        replies = conversation.replies,
                        cursor = conversation.repliesCursor.takeIf { _ -> conversation.replies.isNotEmpty() },
                        loading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    fun loadMore() {
        val now = state.value
        val cursor = now.cursor ?: return
        if (now.loadingMore || now.loading) return
        viewModelScope.launch {
            state.update { it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = XConversation.of(graph.client.conversation(id, cursor), id)
                graph.posts.remember(page.replies)
                state.update {
                    val known = it.replies.mapTo(HashSet()) { thread -> thread.id }
                    val fresh = page.replies.filter { thread -> thread.id !in known }
                    it.copy(replies = it.replies + fresh, cursor = page.repliesCursor.takeIf { _ -> fresh.isNotEmpty() }, loadingMore = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
            }
        }
    }
}
