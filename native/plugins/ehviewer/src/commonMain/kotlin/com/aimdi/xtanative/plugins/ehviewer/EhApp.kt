package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.ehviewer.EhClient
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.networks.ehviewer.EhPreview
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.downloads.PluginDownloads
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginNotices
import com.aimdi.xtanative.ui.plugins.PluginSearchHistory
import com.aimdi.xtanative.ui.plugins.PluginShelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the EH plugin's screens share while it runs: the client, reading with
 * the reader's preferences (the app's settings, under XTA's keys) and the
 * cookies pasted here; the favourites and the reading history kept in the
 * plugin's folder; what one screen hands the next; and what the plugin tells
 * the reader. Nothing is written to the site.
 */
internal class EhApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val readying = Mutex()
    private var ready = false

    val cookies = EhCookies(host.store.file("cookies.json"))
    val client = EhClient(host.http) { EhConfig.client(current(), cookies.now) }
    val favorites = EhFavorites(host.store.file("favorites.json"))
    val history = EhHistory(host.store.file("history.json"))
    val shelf = PluginShelf()
    val notices = PluginNotices()

    /** The app's settings once read; null before. */
    val settings: StateFlow<Settings?> get() = host.settings.state

    /** The settings now, the defaults until they are read. */
    fun current(): Settings = host.settings.state.value ?: Settings()

    /** What every read needs first: the settings, the cookies and what the device keeps. */
    suspend fun ready() = readying.withLock {
        if (ready) return@withLock
        if (host.settings.state.value == null) {
            host.settings.update { it }
            host.settings.state.filterNotNull().first()
        }
        cookies.load()
        favorites.load()
        history.load()
        ready = true
    }

    /** The plugin's own preferences changed by [edit] (`plugin.ehviewer.`). */
    fun update(edit: (Settings) -> Settings) = launch { host.settings.update(edit) }

    /** Runs [block] beyond the screen that asked for it; a failure ends it quietly. */
    fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A setting or a row that could not be written stays as it was.
            }
        }
    }

    /** A search run: first in the history XTA keeps in its preferences. */
    suspend fun rememberSearch(query: String) =
        host.settings.update { it.with(EhViewerPrefs.searchHistory, PluginSearchHistory.remembered(it[EhViewerPrefs.searchHistory], query)) }

    fun forgetSearch(query: String) = update { it.with(EhViewerPrefs.searchHistory, PluginSearchHistory.forgotten(it[EhViewerPrefs.searchHistory], query)) }

    fun clearSearches() = update { it.with(EhViewerPrefs.searchHistory, emptyList()) }

    fun close() = scope.cancel()

    fun openGallery(gallery: EhGallery) = host.open(EhRoutes.gallery(gallery, shelf.put(gallery)))

    /** The reader on [page], with what the gallery's page already read. */
    fun openReader(gallery: EhGallery, page: Int, previews: List<EhPreview>) =
        host.open(EhRoutes.reader(gallery, page, shelf.put(EhReading(gallery, previews))))

    /** The search, run for [query] when there is one (a tag, an uploader). */
    fun openSearch(query: String? = null) = host.open(EhRoutes.search(query))

    /** A page's picture through the app's downloads, named after its gallery as XTA names it ("ehviewer-<gid>-<file>"). */
    fun download(gallery: EhGallery, imageUrl: String) =
        host.download(MediaItem(imageUrl), fileName = PluginDownloads.fileName("ehviewer-${gallery.gid}", imageUrl))
}
