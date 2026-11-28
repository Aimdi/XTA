package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.GroupFeed
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.feed.HiddenPosts
import com.aimdi.xtanative.ui.feed.feedConcurrency
import com.aimdi.xtanative.ui.intro.IntroFlow
import com.aimdi.xtanative.ui.settings.FeedSwitches
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The Following feed's first page read while the window is still being made,
 * for a start that opens on it: the same reads its view model's first load
 * makes ([GroupFeedViewModel.refresh]: on from the feed kept by the last run,
 * else its first page), through [AppGraph.feedReads], which hands each to the
 * feed when it asks. Nothing is read for a start that opens elsewhere.
 */
object EarlyFollowing {
    /** Whether a start with [settings] and [session] opens on the Following feed: Home, on its default feed, past the intro. */
    fun opensOn(settings: Settings, session: Session): Boolean =
        settings[GeneralPrefs.initialTab].trim() == "feed" && settings[GeneralPrefs.defaultFeedTab] == "following" &&
            !IntroFlow.opensOn(settings, hasAccounts = session.accounts.isNotEmpty())

    /** Starts the reads ahead, once X can be read as the reader set it up. */
    suspend fun read(graph: AppGraph) {
        graph.awaitReadsReady()
        val settings = graph.settings.state.value ?: return
        // As the session will be once the settings' feed switches are kept in step with it.
        val session = FeedSwitches.mirrored(graph.sessions.state.filterNotNull().first(), settings)
        if (!opensOn(settings, session)) return
        val hidden = HiddenPosts.of(graph.profileFilters).first()
        val plan = GroupFeedRules.plan(session, FeedPrefs.of(settings), null, emptySet(), hidden)
        if (plan.chunks.isEmpty()) return
        val saved = graph.feedCache(null).read()
        val concurrency = graph.feedConcurrency()
        graph.feedReads.ahead { reads ->
            // What fails here fails for the feed too, which says so as it asks: nothing to say twice.
            runCatching {
                val feed = GroupFeed(plan, reads, concurrency)
                saved?.let { feed.reopen(it) } ?: feed.first(null)
            }
        }
    }
}
