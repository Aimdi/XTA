package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.store.StoreDir
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the app gives a plugin, through [XtaPlugin.attach]: one host per
 * plugin, bound to its id.
 *
 * Beside this, a plugin builds with what the app provides as libraries and
 * never bundles (PLUGINS.md): Compose and Material 3, coroutines, OkHttp,
 * jsoup, kotlinx-serialization, Coil, `:core`, `:store`, `:services`
 * (articles, links, translation), `:networks`' shared types, and the shared
 * pieces in this package, drawn in the app's theme: the post row and opened
 * post ([NetworkPostCard], [NetworkFocalPost]), the media tiles and viewer
 * ([MediaTiles], [openMedia]), the paged list with its problem and loading
 * rows ([PagedList]), the screen scaffold ([PluginScaffold]), its text
 * ([PluginStrings]) and what it keeps per screen ([rememberKept]); and
 * `:ui`'s avatar, hairline, author line and times (`ui.common`, `ui.post`).
 *
 * The navigation calls are made from the UI, as a tap makes them.
 */
interface PluginHost {
    /** HTTP through the app's client and its connection pool; a plugin sends its network's own headers. */
    val http: XHttp

    /** The plugin's own folder of the store, for its follows and caches. Uninstalling erases it. */
    val store: StoreDir

    /**
     * The app's settings: a plugin keeps its preferences here, under
     * `plugin.<id>.` as XTA kept a network's (`:store`'s NetworkPrefs), so a
     * backup, XTA's or the app's, brings them back. It changes only its own.
     */
    val settings: PluginSettings

    /** The reader's language as a BCP 47 tag: "en", "de", "pt-BR". A composable follows it with [currentLanguage]. */
    val language: String

    /** Whether there is a connection, as it changes: the present state first. */
    val online: Flow<Boolean>

    /** The app's X client, reading with the reader's X accounts, for a plugin that reads X too. */
    val x: XClient

    /** The X session (the accounts and follows imported from XTA); null until it has been read. */
    val session: StateFlow<Session?>

    /**
     * XTA's published repairs in force (`repairs.json`, by key): a plugin's
     * client takes each value it may need to change without a release from
     * here, with its slot's [com.aimdi.xtanative.core.repairs.RepairSlot.valueIn].
     */
    val repairs: StateFlow<Map<String, String>>

    /** Pushes the plugin's own screen [route] (one [XtaPlugin.screen] knows) on the app's back stack. */
    fun open(route: String)

    /** Leaves the screen on top, as the back button does. */
    fun back()

    /** Opens [url] as the app opens links: in the plugin that opens it (this one included), in the app's X screens for an X link, else on the web as the reader's link settings say. */
    fun openLink(url: String)

    /** An X post in the app's post screen. */
    fun openPost(id: String)

    /** An X profile in the app's profile screen. */
    fun openProfile(screenName: String)

    /** [media] full screen from [start]: photos to zoom, videos to play. */
    fun openMedia(media: List<MediaItem>, start: Int)

    /** The plugin's search, with [query] sent when it is given. */
    fun openSearch(query: String?)

    /** The plugin's settings. */
    fun openSettings()
}
