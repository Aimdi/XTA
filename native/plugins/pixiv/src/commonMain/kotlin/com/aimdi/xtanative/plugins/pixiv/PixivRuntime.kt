package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivApp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkApi
import com.aimdi.xtanative.networks.pixiv.PixivClient
import com.aimdi.xtanative.networks.pixiv.PixivCommentsApi
import com.aimdi.xtanative.networks.pixiv.PixivDiscoveryApi
import com.aimdi.xtanative.networks.pixiv.PixivNovelApi
import com.aimdi.xtanative.networks.pixiv.PixivSearchApi
import com.aimdi.xtanative.networks.pixiv.PixivSocialApi
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginHost
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * What the Pixiv plugin's screens share while it runs: its settings (XTA's
 * Pixiv preferences, in the app's settings), the client reading as the signed-in account with
 * XTA's published repairs in force, the reads beside it, and what the device
 * keeps (mutes, history, its own bookmarks and follows). Nothing is written
 * to Pixiv.
 */
internal class PixivRuntime(val host: PluginHost) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = PixivSettingsStore(host.settings.state, host.settings::update, scope)

    val client = PixivClient(
        http = host.http,
        store = PixivSettingsCredentials(settings),
        filters = { settings.filters() },
        locale = { host.language },
        appInForce = { PixivApp.published(host.repairs.value) },
    )

    val discovery = PixivDiscoveryApi(client)
    val searchApi = PixivSearchApi(client)
    val social = PixivSocialApi(client)
    val novels = PixivNovelApi(client)
    val comments = PixivCommentsApi(client)
    val bookmarkApi = PixivBookmarkApi(client)

    val mutes = PixivMuteStore(settings)
    val history = PixivHistory(host.store.file("history-works.json"), PixivKind.WORK)
    val novelHistory = PixivHistory(host.store.file("history-novels.json"), PixivKind.NOVEL)
    val bookmarks = PixivDeviceBookmarks(host.store.file("bookmarks.json"))

    /** Where the reader stopped in each novel: viewing history, so it stays in the plugin's folder. */
    val novelPlaces = ReadingJournal(host.store, "novels")

    val bookmarking = PixivBookmarking(bookmarks, { settings.now }, ::followHere, { client.storedUserId })

    /** What a screen hands the next one it opens (the work as its list had it, the list itself), by key. */
    val shelf = PixivShelf()

    private val said = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** What the plugin tells the reader at the foot of its screens. */
    val notices: SharedFlow<String> = said.asSharedFlow()

    fun tell(message: String) {
        said.tryEmit(message)
    }

    /** Reads what every screen needs before it draws: the settings and the bookmarks kept here. */
    suspend fun ready() {
        settings.load()
        bookmarks.load()
    }

    /** Runs [block] beyond the screen that asked for it: a bookmark, a follow, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun open(route: PixivRoute) = host.open(PixivRoutes.of(route))

    val signedIn: Boolean get() = PixivAccounts.signedIn(settings.now)

    /** The reader's own Pixiv id, once a token response named it. */
    val ownId: Long? get() = client.storedUserId

    fun isFollowedHere(id: Long): Boolean = PixivFollows.isFollowed(settings.now, id)

    /** Follows [user] on this device, or stops: Pixiv is never told. */
    fun toggleFollow(user: PixivUser) {
        if (isFollowedHere(user.id)) settings.update { PixivFollows.unfollowed(it, user.id) } else followHere(user)
    }

    private fun followHere(user: PixivUser) = settings.update { PixivFollows.followed(it, user, Instant.now().toString()) }

    /** Whether opened works and novels join the viewing history now. */
    val historyPaused: Boolean get() = settings[PixivPrefs.historyPaused]

    fun close() = scope.cancel()

    companion object {
        const val NETWORK = "Pixiv"
    }
}

/** What screens hand to the screens they open, the last few kept: a route names them by key, as it cannot carry them. */
internal class PixivShelf {
    private val kept = object : LinkedHashMap<String, Any>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean = size > KEPT
    }
    private var last = 0

    @Synchronized
    fun put(value: Any): String = (++last).toString().also { kept[it] = value }

    /** What [key] names; nothing once it was let go, as after the app was restarted. */
    @Synchronized
    fun get(key: String?): Any? = key?.let { kept[it] }

    private companion object {
        const val KEPT = 32
    }
}
