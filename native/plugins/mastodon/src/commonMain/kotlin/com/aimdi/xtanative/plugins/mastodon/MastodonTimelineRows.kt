package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonReadPoint
import com.aimdi.xtanative.networks.mastodon.MastodonReadingCodec
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineState

/** One row of a timeline's list, as XTA's timeline pane stacks them. */
internal sealed interface TimelineRow {
    val key: String

    /** A read that failed over the posts it kept. */
    data class RefreshFailed(val error: Throwable) : TimelineRow {
        override val key get() = "refresh-failed"
    }

    /** Explore's trending tags. */
    data object Trending : TimelineRow {
        override val key get() = "trending"
    }

    data object Loading : TimelineRow {
        override val key get() = "loading"
    }

    /** Nothing shown, and why. */
    data class Problem(val error: Throwable) : TimelineRow {
        override val key get() = "problem"
    }

    data object Empty : TimelineRow {
        override val key get() = "empty"
    }

    /** Posts there are, but the filters keep none of them. */
    data object Filtered : TimelineRow {
        override val key get() = "filtered"
    }

    data class Post(val post: MastodonPost) : TimelineRow {
        override val key get() = POST + post.url
    }

    data object LoadingMore : TimelineRow {
        override val key get() = "loading-more"
    }

    data class MoreFailed(val error: Throwable) : TimelineRow {
        override val key get() = "more-failed"
    }

    data object LoadMore : TimelineRow {
        override val key get() = "load-more"
    }

    companion object {
        const val POST = "post:"
    }
}

internal object TimelineRows {
    /**
     * The rows of a timeline in [state] showing [visible] (its posts the
     * filters keep, one per address): until something is shown, the loading,
     * failed or empty row; then a failed refresh over what it kept, Explore's
     * tags when [trending], the posts, and how the next page is coming.
     */
    fun of(state: MastodonTimelineState, visible: List<MastodonPost>, trending: Boolean): List<TimelineRow> {
        val error = state.error
        if (state.isEmpty) {
            return listOf(
                when {
                    state.loading || !state.loaded -> TimelineRow.Loading
                    error != null -> TimelineRow.Problem(error)
                    else -> TimelineRow.Empty
                },
            )
        }
        val more = state.moreError
        return listOfNotNull(
            error?.let { TimelineRow.RefreshFailed(it) },
            TimelineRow.Trending.takeIf { trending && state.tags.isNotEmpty() },
            TimelineRow.Filtered.takeIf { visible.isEmpty() && state.posts.isNotEmpty() },
        ) + visible.distinctBy { it.url }.map { TimelineRow.Post(it) } + listOfNotNull(
            when {
                state.loadingMore -> TimelineRow.LoadingMore
                more != null -> TimelineRow.MoreFailed(more)
                state.canLoadMore -> TimelineRow.LoadMore
                else -> null
            },
        )
    }

    /** Where [rows] open for a place left at [point]: its post's row and how far past it, or nothing while the post is not there. */
    fun start(rows: List<TimelineRow>, point: MastodonReadPoint): Pair<Int, Int>? {
        val index = rows.indexOfFirst { it is TimelineRow.Post && it.post.url == point.anchor }
        return if (index < 0) null else index to (-point.leading).toInt().coerceAtLeast(0)
    }

    /**
     * The place a list shows: the first post row on screen ([visible], each a
     * row's key and where it starts) and the posts kept around it from all
     * the timeline holds.
     */
    fun place(visible: List<Pair<Any, Int>>, state: MastodonTimelineState): MastodonReadPoint? {
        val (key, offset) = visible.firstOrNull { (key, _) -> key is String && key.startsWith(TimelineRow.POST) } ?: return null
        val anchor = (key as String).removePrefix(TimelineRow.POST)
        val posts = MastodonReadingCodec.window(state.posts, anchor)
        return if (posts.isEmpty()) null else MastodonReadPoint(posts, anchor, offset.toDouble(), state.instance, state.tags)
    }
}
