package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.reading.ReadingJournal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Where the app's article readers leave each article, for [com.aimdi.xtanative.store.reading.ArticleReading]. */
    val readingJournal: ReadingJournal

    /** The text size and line height every article reader shares. */
    val readingAppearance: ReadingAppearanceStore

    /** The app's offline library: an article kept there, and its pictures, read without a connection. */
    val offline: OfflineStore

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

    // The reader's archive and groups, on this device alone: nothing reaches the network.

    /** The ids filed in Saved, as they change: what draws a bookmark filled. */
    val saved: StateFlow<Set<String>>

    /**
     * The plugin's own posts in Saved, newest first, as they change: those
     * whose record names it (its `xtaPlugin`, or a link record's `source`),
     * each with the reader's note; null until Saved has been read. What a
     * plugin's own saved list shows ([PluginSavedScreen]).
     */
    val savedPosts: StateFlow<List<PluginSavedPost>?> get() = NoSavedPosts

    /** The bookmark tapped: [post] saved in Saved where a plain tap files (the folder remembered, else unfiled), or taken out once saved. */
    fun toggleSaved(post: PluginPostArchive)

    /** The app's save sheet for [post], as a long press on a bookmark opens it: its folders, a new one, Remember this folder. */
    fun chooseFolder(post: PluginPostArchive)

    /** The note on [post] in Saved, in the app's note editor; writing one saves the post first, where a plain tap files. */
    fun editNote(post: PluginPostArchive)

    /** [post] in the archive's likes (Saved › Likes), or out of them: a network's own like mirrored there, as XTA mirrors Reddit's upvotes. */
    fun setLiked(post: PluginPostArchive, liked: Boolean)

    /**
     * The app's group picker for [account], one of the plugin's (as its
     * [PluginFeed] reads it): the groups picked hold it as "<plugin id>:<account>",
     * and their feeds read its posts. [follow] runs first once a group is
     * picked, for the plugin to follow it, as XTA's picker does.
     */
    fun addToGroup(account: String, follow: suspend () -> Unit = {})

    /**
     * The reader unfollowed [account], one of the plugin's (as its group
     * members name it): the app takes it out of every group, as XTA's stores
     * do when they remove an account. A plugin says so wherever it unfollows.
     */
    fun unfollowed(account: String) {}

    /** The shared post menu over the screen: [menu]'s post saved or taken out, its note, its author's groups, its link shared or opened. */
    fun openPostMenu(menu: PluginPostMenu)

    /**
     * [media] saved through the app's downloads, where the download setting
     * says and with their notices: a photo, or a video's own file (a stream
     * has none to save). [fileName] and [subfolder] are for a plugin that
     * names its files itself; else it is named after the plugin and the file.
     */
    fun download(media: MediaItem, fileName: String? = null, subfolder: String? = null)

    /**
     * A file the plugin made itself ([bytes] as [fileName], of [mimeType]: an
     * animation it put together, a novel's text) saved where the download
     * setting says, with the downloads' notices; a name without an extension
     * gets the one [mimeType] stands for.
     */
    fun saveFile(bytes: ByteArray, fileName: String, mimeType: String, subfolder: String? = null)

    /**
     * Such a file handed to the system's share sheet: Android's, through the
     * app's file provider. The desktop has none, so there it is saved as
     * [saveFile] saves it, the notice saying where and opening it.
     */
    fun shareFile(bytes: ByteArray, fileName: String, mimeType: String)

    /** The app's Downloads screen: what was saved, what is under way, and retries. */
    fun openDownloads()

    /**
     * [text] said at the foot of the window over whatever screen is up, as
     * XTA's snack bars and the app's own notices are (how a save to the
     * reader's own server went); the newest takes the place of the one shown.
     * A host with no window to say it in says nothing.
     */
    fun tell(text: String) {}

    /**
     * [text] said while [work] runs, as XTA's working snack bar ("Uploading…"),
     * and taken away once it ends: what [tell] says next takes its place.
     */
    suspend fun <T> working(text: String, work: suspend () -> T): T = work()

    /**
     * [files] saved through the app's downloads as one batch, as XTA saves a
     * work's pages: in [subfolder] of where the download setting says, as many
     * at a time as it allows, with one notice for them all, how many were
     * saved (a lone file says it as [download] does). Answers once each has
     * ended, file by file, whether it was saved.
     */
    suspend fun downloadAll(files: List<PluginDownload>, subfolder: String? = null): List<Boolean> = files.map { false }

    /**
     * [files] queued in the app's downloads to run on their own, as XTA's
     * Booru queues the posts a feed has loaded: each the downloads have saved
     * or are saving already is left out, and so are those past what the queue
     * takes. Answers how many were queued and how many were left out as there
     * already; null when none could be, as the download setting asks where
     * each file goes and a batch needs a folder (the app says so).
     */
    suspend fun queueDownloads(files: List<PluginDownload>, subfolder: String? = null): PluginQueued? = null
}

/** What [PluginHost.savedPosts] holds where a host keeps no Saved: nothing. */
private val NoSavedPosts: StateFlow<List<PluginSavedPost>?> = MutableStateFlow(emptyList())
