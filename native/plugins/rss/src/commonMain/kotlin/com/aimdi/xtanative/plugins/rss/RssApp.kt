package com.aimdi.xtanative.plugins.rss

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssClient
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.networks.rss.RssFeeds
import com.aimdi.xtanative.networks.rss.RssReadIds
import com.aimdi.xtanative.networks.rss.RssRoutes
import com.aimdi.xtanative.networks.rss.RssTags
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.RssPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the RSS plugin's screens share while it runs: the client, the feeds
 * followed, the items read and each feed's tags, as XTA keeps them, in the
 * app's settings under XTA's keys (RssPrefs), so XTA's backup restores them
 * and the app's carries them; the timeline; and the items and feeds its
 * screens were opened with.
 */
internal class RssApp(val host: PluginHost) {
    /** Where what outlives a screen runs: a follow, a read mark, a reader's last place. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val client = RssClient(host.http)

    /** The followed feeds, newest first as XTA adds them; null until the settings are read. */
    val feeds: StateFlow<List<RssFeed>?> = observe(RssPrefs.feeds) { RssFeeds.decode(it) }
    val read: StateFlow<Set<String>> = observe(RssPrefs.readIds, emptySet()) { RssReadIds.decode(it).toSet() }
    val tags: StateFlow<Map<String, List<String>>> = observe(RssPrefs.tags, emptyMap()) { RssTags.decode(it) }
    val timeline = RssTimelineModel(client, scope) { feeds.value.orEmpty() }
    val opml = RssOpmlImports()

    /** The followed feeds' items in the app's timelines and groups, and where they lead there. */
    val shared = RssShared(this)
    private val openedItems = Recent<RssItem>()
    private val openedFeeds = Recent<RssFeed>()

    fun close() = scope.cancel()

    /** Runs [block] beyond the screen that asked for it; a failure (a full disk) ends it quietly. */
    fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    suspend fun loadedFeeds(): List<RssFeed> = feeds.filterNotNull().first()

    suspend fun follow(feed: RssFeed) = change(RssPrefs.feeds) { RssFeeds.encode(RssFeeds.followed(RssFeeds.decode(it), feed)) }

    suspend fun unfollow(id: String) = change(RssPrefs.feeds) { RssFeeds.encode(RssFeeds.unfollowed(RssFeeds.decode(it), id)) }

    fun isFollowing(id: String): Boolean = feeds.value.orEmpty().any { it.id == id }

    /** [feed] in the groups the reader picks (XTA's addRssFeedToGroup), followed first once one is picked. */
    fun addToGroup(feed: RssFeed) = host.addToGroup(feed.id) { followIfNeeded(feed) }

    suspend fun followIfNeeded(feed: RssFeed) {
        if (!isFollowing(feed.id)) follow(feed)
    }

    /** The feed [item] came from: the followed one, else one made from what the item says of it. */
    fun feedOf(item: RssItem): RssFeed = feedFor(item.feedId) ?: RssFeed(item.feedId, item.feedId, item.feedTitle)

    suspend fun markRead(ids: Iterable<String>) = change(RssPrefs.readIds) { RssReadIds.encode(RssReadIds.marked(RssReadIds.decode(it), ids)) }

    suspend fun setTags(feedId: String, chosen: List<String>) = change(RssPrefs.tags) { RssTags.encode(RssTags.withTags(RssTags.decode(it), feedId, chosen)) }

    // ---- where things open ----

    fun openItem(item: RssItem) {
        openedItems.keep(item.id, item)
        host.open(RssRoutes.item(item))
    }

    fun openFeed(feed: RssFeed) {
        openedFeeds.keep(feed.id, feed)
        host.open(RssRoutes.feed(feed))
    }

    fun openAdd() = host.open(RssRoutes.ADD)

    /** The item a reader route names, as it was opened while the plugin ran. */
    fun itemFor(id: String): RssItem? = openedItems.get(id) ?: timeline.state.value.items.firstOrNull { it.id == id }

    fun feedFor(id: String): RssFeed? = feeds.value.orEmpty().firstOrNull { it.id == id } ?: openedFeeds.get(id)

    private suspend fun change(pref: Pref<String>, edit: (String) -> String) = host.settings.update { it.with(pref, edit(it[pref])) }

    private fun <T> observe(pref: Pref<String>, read: (String) -> T): StateFlow<T?> =
        host.settings.state.map { settings -> settings?.let { read(it[pref]) } }.stateIn(scope, SharingStarted.Eagerly, null)

    private fun <T> observe(pref: Pref<String>, initial: T, read: (String) -> T): StateFlow<T> =
        host.settings.state.map { settings: Settings? -> settings?.let { read(it[pref]) } ?: initial }.stateIn(scope, SharingStarted.Eagerly, initial)

    /** The last few of what screens were opened with, while the plugin runs. */
    internal class Recent<T> {
        private val values = LinkedHashMap<String, T>()

        @Synchronized
        fun keep(key: String, value: T) {
            values.remove(key)
            values[key] = value
            while (values.size > LIMIT) values.remove(values.keys.first())
        }

        @Synchronized
        fun get(key: String): T? = values[key]

        private companion object {
            const val LIMIT = 64
        }
    }
}
