package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The plugin side of one timeline: what it asks of the plugins ([asks]),
 * followed as plugins come and go, their switches turn and its groups
 * change, and read again whenever the timeline is refreshed. Its [posts]
 * are what the timeline places among X's ([FeedMix]); each plugin's last
 * answer is kept in [snapshots] for the next launch.
 */
class MixedSources(
    scope: CoroutineScope,
    feeds: PluginFeeds,
    asks: Flow<Map<String, SourceAsk>>,
    problemOf: (Throwable) -> Problem = Problem::of,
    snapshots: SourceSnapshots = SourceSnapshots.None,
) {
    val posts = PluginPosts(scope, feeds::posts, problemOf, snapshots)

    init {
        scope.launch { asks.distinctUntilChanged().collect { posts.load(it) } }
    }

    /** Every plugin read again, past its caches when [force] (a pull, the refresh button). */
    fun refresh(force: Boolean) = posts.load(posts.asked, force)

    /** The plugins whose last read failed, read again: the connection is back. */
    fun retryFailed() = posts.state.value.failed.keys.forEach(posts::retry)
}
