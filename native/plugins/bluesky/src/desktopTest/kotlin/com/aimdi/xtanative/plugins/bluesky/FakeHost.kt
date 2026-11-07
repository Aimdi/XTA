package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl

/**
 * The app as the Bluesky plugin sees it in a test: an AppView answering with
 * [answer] (which may wait), settings in memory and a folder of its own; the
 * rest is never reached by what the tests drive.
 */
internal class FakeHost(var answer: suspend (HttpUrl) -> XResponse) : PluginHost {
    val asked: MutableList<HttpUrl> = Collections.synchronizedList(ArrayList())
    val values = MutableStateFlow<Settings?>(Settings())
    val opened = mutableListOf<String>()

    override val http: XHttp = XHttp { url, _, _ ->
        asked += url
        answer(url)
    }
    override val store = StoreDir(Files.createTempDirectory("bluesky-host").toFile())
    override val settings = PluginSettings("bluesky", values) { change -> synchronized(values) { values.value = change(values.value ?: Settings()) } }
    override val language = "en"
    override val online: Flow<Boolean> = flowOf(true)
    override val x: XClient get() = error("no X in these tests")
    override val session: StateFlow<Session?> = MutableStateFlow(null)
    override val repairs: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val readingJournal: ReadingJournal get() = error("not read")
    override val readingAppearance: ReadingAppearanceStore get() = error("not read")
    override val offline: OfflineStore get() = error("not read")
    override val saved: StateFlow<Set<String>> = MutableStateFlow(emptySet())

    override fun open(route: String) {
        opened += route
    }

    override fun back() = Unit
    override fun openLink(url: String) = Unit
    override fun openPost(id: String) = Unit
    override fun openProfile(screenName: String) = Unit
    override fun openMedia(media: List<MediaItem>, start: Int) = Unit
    override fun openSearch(query: String?) = Unit
    override fun openSettings() = Unit
    override fun toggleSaved(post: PluginPostArchive) = Unit
    override fun chooseFolder(post: PluginPostArchive) = Unit
    override fun editNote(post: PluginPostArchive) = Unit
    override fun setLiked(post: PluginPostArchive, liked: Boolean) = Unit
    override fun addToGroup(account: String, follow: suspend () -> Unit) = Unit
    override fun openPostMenu(menu: PluginPostMenu) = Unit
    override fun download(media: MediaItem, fileName: String?, subfolder: String?) = Unit
    override fun saveFile(bytes: ByteArray, fileName: String, mimeType: String, subfolder: String?) = Unit
    override fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) = Unit
    override fun openDownloads() = Unit

    /** The hosts the reads went to, in order. */
    val hosts: List<String> get() = synchronized(asked) { asked.map { it.host } }
}

/** Waits (a few seconds at most) until [done], for what the app's own scope writes. */
internal suspend fun eventually(done: () -> Boolean) = withTimeout(5_000) {
    while (!done()) delay(5)
}
