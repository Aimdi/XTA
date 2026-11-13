package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPopularQuery
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPostPage
import com.aimdi.xtanative.store.settings.BooruPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** The home tab's three feeds. */
internal enum class HomeTab { LATEST, POPULAR, FOLLOWING }

/**
 * The home tab's feeds (booru_screen.dart): Latest and Following keep their
 * first page on the device for a launch without a network; Popular reads the
 * day, week or month chosen; Following reads the followed tags and saved
 * searches, and again whenever they change, once it has been shown. Every
 * Following load notes the newest post of each site as seen.
 */
internal class HomeFeeds(private val app: BooruApp, scope: CoroutineScope) {
    private val feedKey = { app.client.feedKey }

    val latest = BooruFeed(scope, feedKey, app.cache, { "latest" }) { page -> app.client.latest(page.toString()) }

    private val day = MutableStateFlow(BooruPopularQuery.today())

    /** The day, week or month Popular shows. */
    val popularQuery: StateFlow<BooruPopularQuery> = day.asStateFlow()

    val popular = BooruFeed(scope, feedKey) { page -> app.client.popular(day.value, page.toString()) }

    val following = BooruFeed(scope, feedKey, app.cache, { (listOf("following") + app.follows.state.value.orEmpty()).joinToString("\n") }) { page ->
        followingPage(page)
    }

    /** Each site's newest post Following had shown before this visit. */
    val seenBefore: Map<String, Long> = app.seenBefore ?: BooruVisits.seen(app.current()[BooruPrefs.followingSeen]).also { app.seenBefore = it }

    private var followingShown = false

    init {
        scope.launch {
            var first = true
            app.follows.state.filterNotNull().distinctUntilChanged().collect {
                if (!first && followingShown) following.refresh(changed = true)
                first = false
            }
        }
        scope.launch { attempt { app.follows.load() } }
    }

    fun feed(tab: HomeTab): BooruFeed = when (tab) {
        HomeTab.LATEST -> latest
        HomeTab.POPULAR -> popular
        HomeTab.FOLLOWING -> following
    }

    /** Loads [tab]'s feed when it has not been, or the site or filters changed since. Following waits for the followed tags. */
    fun ensure(tab: HomeTab) {
        if (tab != HomeTab.FOLLOWING) return feed(tab).ensureFresh()
        if (app.follows.state.value == null) return
        if (!followingShown || following.isStale) {
            followingShown = true
            following.refresh()
        }
    }

    /** Popular for [query]: the posts shown no longer answer it. */
    fun showPopular(query: BooruPopularQuery) {
        if (query == day.value) return
        day.value = query
        popular.refresh(changed = true)
    }

    fun isNew(post: BooruPost): Boolean = BooruVisits.isNew(seenBefore, post)

    /** Following's single page, counting the followed tags that failed (loadBooruFollowing); what it shows is seen from now on. */
    private suspend fun followingPage(page: Int): BooruPostPage {
        val loaded = app.client.following(app.follows.load(), page.toString())
        if (loaded.posts.isNotEmpty()) {
            app.update { it.with(BooruPrefs.followingSeen, BooruVisits.encode(BooruVisits.marked(BooruVisits.seen(it[BooruPrefs.followingSeen]), loaded.posts))) }
        }
        return loaded
    }
}
