package com.aimdi.xtanative.plugins.websources

import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.websources.InstalledWebSource
import com.aimdi.xtanative.networks.websources.WebSourceClient
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceFollow
import com.aimdi.xtanative.networks.websources.WebSourceFollows
import com.aimdi.xtanative.networks.websources.WebSourceItem
import com.aimdi.xtanative.networks.websources.WebSourceList
import com.aimdi.xtanative.networks.websources.WebSourceOpening
import com.aimdi.xtanative.networks.websources.WebSourceReading
import com.aimdi.xtanative.networks.websources.WebSourcesInstalled
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.WebSourcesPrefs
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.RssReads
import com.aimdi.xtanative.ui.plugins.loaded
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the web sources plugin's screens share while it runs: the client,
 * the sources installed and the lists followed, as XTA keeps them, in the
 * app's settings under XTA's keys (WebSourcesPrefs), so XTA's backup
 * restores them and the app's carries them; the posts its reader was
 * opened with, and those read there to their end.
 */
internal class WebSourcesApp(val host: PluginHost) {
    /** Where what outlives a screen runs: an install, a follow, the reader's last place. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val client = WebSourceClient(host.http)

    /** The sources installed, in the order they were added; null until the settings are read. */
    val installed: StateFlow<List<InstalledWebSource>?> = observe(WebSourcesPrefs.installed, WebSourcesInstalled::decode)
    val follows: StateFlow<List<WebSourceFollow>?> = observe(WebSourcesPrefs.follows, WebSourceFollows::decode)

    /** The posts read to their end in the RSS reader, among RSS's items as XTA keeps them ([RssReads]). */
    val read: StateFlow<Set<String>> = host.settings.state.map(RssReads::of).stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** The followed lists' posts in the app's timelines and groups, and where they lead there. */
    val shared = WebSourceShared(this)
    private val opened = LinkedHashMap<String, RssItem>()

    fun close() = scope.cancel()

    /** Runs [block] beyond the screen that asked for it; a failure ends it quietly. */
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

    fun source(id: String): InstalledWebSource? = installed.value.orEmpty().firstOrNull { it.source.id == id }

    suspend fun install(source: WebSourceDefinition, origin: String?) = change(WebSourcesPrefs.installed) { WebSourcesInstalled.encode(WebSourcesInstalled.installed(WebSourcesInstalled.decode(it), source, origin)) }

    /** Removes [id] and every list followed of it, as XTA's removeWebSource does. */
    suspend fun remove(id: String) {
        change(WebSourcesPrefs.follows) { WebSourceFollows.encode(WebSourceFollows.withoutSource(WebSourceFollows.decode(it), id)) }
        change(WebSourcesPrefs.installed) { WebSourcesInstalled.encode(WebSourcesInstalled.removed(WebSourcesInstalled.decode(it), id)) }
    }

    suspend fun agreeToUpdate(id: String) = change(WebSourcesPrefs.installed) { WebSourcesInstalled.encode(WebSourcesInstalled.agreed(WebSourcesInstalled.decode(it), id)) }

    fun isFollowing(follows: List<WebSourceFollow>, source: WebSourceDefinition, list: WebSourceList) = WebSourceFollows.isFollowing(follows, source.id, list.id)

    suspend fun follow(source: WebSourceDefinition, list: WebSourceList) =
        change(WebSourcesPrefs.follows) { WebSourceFollows.encode(WebSourceFollows.followed(WebSourceFollows.decode(it), source, list, Instant.now())) }

    suspend fun unfollow(source: WebSourceDefinition, list: WebSourceList) =
        change(WebSourcesPrefs.follows) { WebSourceFollows.encode(WebSourceFollows.unfollowed(WebSourceFollows.decode(it), WebSourceFollows.subscriptionId(source.id, list.id))) }

    /**
     * Reads every installed source's file again, at most once in
     * [com.aimdi.xtanative.networks.websources.WebSourceUpdates.INTERVAL_HOURS],
     * as XTA does at start: a newer version on sites agreed to replaces its
     * source, one asking for a new site waits for the reader.
     */
    fun checkForUpdates() = launch {
        val settings = host.settings.loaded()
        val now = Instant.now()
        if (!WebSourcesInstalled.dueForUpdate(settings[WebSourcesPrefs.checkedAt], now)) return@launch
        host.settings.update { it.with(WebSourcesPrefs.checkedAt, WebSourcesInstalled.checkedAt(now)) }
        val updates = WebSourcesInstalled.decode(settings[WebSourcesPrefs.installed])
            .mapNotNull { entry -> client.update(entry)?.let { entry.source.id to it } }.toMap()
        if (updates.isNotEmpty()) change(WebSourcesPrefs.installed) { WebSourcesInstalled.encode(WebSourcesInstalled.updated(WebSourcesInstalled.decode(it), updates)) }
    }

    /** [id], a post opened in the RSS reader and read to its end, marked read among RSS's items, as XTA's reader marks it. */
    suspend fun markRead(id: String) = RssReads.mark(host.settings, listOf(id))

    // ---- where things open ----

    fun openSource(source: WebSourceDefinition, listId: String? = null) = host.open(WebSourceRoutes.source(source.id, listId))

    fun openSearch(source: WebSourceDefinition) = host.open(WebSourceRoutes.search(source.id))

    fun openAdd() = host.open(WebSourceRoutes.ADD)

    /**
     * [item] of [source] opened where the app draws it (Home's timelines, the
     * groups), as its own lists open it: in the reader with its text, else
     * with the article its source names, read under a notice, else at its address.
     */
    fun openItem(source: WebSourceDefinition, item: WebSourceItem) {
        when (val opening = WebSourceReading.opening(source, item)) {
            is WebSourceOpening.Reader -> opened(source, item, opening.html)
            is WebSourceOpening.Address -> opened(source, item, null)
            WebSourceOpening.Article -> launch { opened(source, item, host.working(Strings.reading.get(host.language)) { article(source, item) }) }
        }
    }

    /** [item] in the reader with [html], its full text; at its address when there is none. */
    fun opened(source: WebSourceDefinition, item: WebSourceItem, html: String?) {
        if (!html.isNullOrEmpty()) openReader(source, WebSourceReading.itemOf(source, item, html)) else WebSourceReading.address(item)?.let(host::openLink)
    }

    /** The full text [source]'s article rule names for [item]; null when it cannot be read. */
    suspend fun article(source: WebSourceDefinition, item: WebSourceItem): String? = try {
        client.article(source, item)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    fun openReader(source: WebSourceDefinition, item: RssItem) {
        synchronized(opened) {
            opened.remove(item.id)
            opened[item.id] = item
            while (opened.size > KEPT) opened.remove(opened.keys.first())
        }
        host.open(WebSourceRoutes.reader(source.id, item))
    }

    fun openedItem(key: String): RssItem? = synchronized(opened) { opened[key] }

    private suspend fun change(pref: Pref<String>, edit: (String) -> String) = host.settings.update { it.with(pref, edit(it[pref])) }

    private fun <T> observe(pref: Pref<String>, read: (String) -> T): StateFlow<T?> =
        host.settings.state.map { settings -> settings?.let { read(it[pref]) } }.stateIn(scope, SharingStarted.Eagerly, null)

    private companion object {
        const val KEPT = 32
    }
}
