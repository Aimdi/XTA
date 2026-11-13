package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPostRef
import com.aimdi.xtanative.networks.booru.BooruTagInfo
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The posts the viewer swipes through (booru_pager_store.dart). Following the
 * [ViewerSource]'s feed, it grows as the feed loads more, and asks for more as
 * the reader nears the end; a feed refreshed into other posts leaves the
 * viewer as it was. What it asks a host about its posts is asked once ([lookups]).
 */
internal class ViewerState(private val app: BooruApp, private val source: ViewerSource, scope: CoroutineScope) {
    private val mutable = MutableStateFlow(source.posts)
    val posts: StateFlow<List<BooruPost>> = mutable.asStateFlow()

    val lookups = BooruLookups(scope)

    /** The search the posts came from: a post's tags can be added to it. */
    val search: BooruSearch? get() = source.search

    init {
        source.feed?.let { feed ->
            scope.launch { feed.state.collect { next -> if (BooruPager.extends(mutable.value, next.posts)) mutable.value = next.posts } }
        }
    }

    fun show(index: Int) {
        if (index >= mutable.value.size - BooruPager.PREFETCH) source.feed?.loadMore()
    }

    /** [post]'s tag kinds and counts, asked once while the viewer is open; what the post carries stands in when the host cannot say. */
    suspend fun tagInfo(post: BooruPost): Map<String, BooruTagInfo> =
        attempt { lookups.remember("tags:${post.key}") { app.client.tagInfo(post) } } ?: post.tagCategories.mapValues { BooruTagInfo(it.value) }
}

/** A post a link named, read from its site: null until it answered, then the post, or why it is not there. */
internal class LinkedPost(private val app: BooruApp, private val ref: BooruPostRef, private val scope: CoroutineScope) {
    sealed interface State {
        data object Loading : State

        data class Found(val source: ViewerSource) : State

        /** The site has no such post, or it is over the rating cap: [error] null. */
        data class Missing(val error: Throwable?) : State
    }

    private val mutable = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = State.Loading
        scope.launch {
            mutable.value = try {
                app.client.postAt(ref)?.let { State.Found(ViewerSource(listOf(it))) } ?: State.Missing(null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                State.Missing(error)
            }
        }
    }
}

/** [block]'s answer, or null when it failed; a cancelled read still stops. */
internal suspend fun <T> attempt(block: suspend () -> T): T? = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    null
}
