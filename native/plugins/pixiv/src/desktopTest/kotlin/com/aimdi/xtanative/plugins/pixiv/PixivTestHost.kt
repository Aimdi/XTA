package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.reading.ReadingAppearanceStore
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.nio.file.Files
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** The app as the Pixiv plugin's tests play it: settings in memory, a store in a fresh folder, Pixiv through [http]. */
internal class PixivTestHost(
    initial: Settings = Settings(),
    override val http: XHttp = FakePixivHttp { answer("{}") },
) : PluginHost {
    val appSettings = MutableStateFlow<Settings?>(initial)
    val opened = mutableListOf<String>()
    val links = mutableListOf<String>()
    val told = mutableListOf<String>()
    val unfollowedAccounts = mutableListOf<String>()

    /** Every batch the downloads were handed, and whether each file of one is saved: all of them, unless a test says otherwise. */
    val batches = mutableListOf<List<PluginDownload>>()
    var saves: (List<PluginDownload>) -> List<Boolean> = { files -> files.map { true } }

    override val store = StoreDir(Files.createTempDirectory("pixiv-test").toFile())
    override val settings = PluginSettings("pixiv", appSettings) { change -> appSettings.value = change(appSettings.value ?: Settings()) }
    override val language = "en"
    override val online: Flow<Boolean> = flowOf(true)
    override val x: XClient get() = error("not in tests")
    override val session: StateFlow<Session?> = MutableStateFlow(null)
    override val repairs: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val readingJournal: ReadingJournal get() = error("not in tests")
    override val readingAppearance: ReadingAppearanceStore get() = error("not in tests")
    override val offline: OfflineStore get() = error("not in tests")
    override val saved: StateFlow<Set<String>> = MutableStateFlow(emptySet())

    override fun open(route: String) {
        opened += route
    }

    override fun back() {}
    override fun openLink(url: String) {
        links += url
    }

    override fun openPost(id: String) {}
    override fun openProfile(screenName: String) {}
    override fun openMedia(media: List<MediaItem>, start: Int) {}
    override fun openSearch(query: String?) {}
    override fun openSettings() {}
    override fun toggleSaved(post: PluginPostArchive) {}
    override fun chooseFolder(post: PluginPostArchive) {}
    override fun editNote(post: PluginPostArchive) {}
    override fun setLiked(post: PluginPostArchive, liked: Boolean) {}
    override fun addToGroup(account: String, follow: suspend () -> Unit) {}
    override fun unfollowed(account: String) {
        unfollowedAccounts += account
    }

    override fun openPostMenu(menu: PluginPostMenu) {}
    override fun download(media: MediaItem, fileName: String?, subfolder: String?) {}
    override fun saveFile(bytes: ByteArray, fileName: String, mimeType: String, subfolder: String?) {}
    override fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {}
    override fun openDownloads() {}
    override suspend fun downloadAll(files: List<PluginDownload>, subfolder: String?): List<Boolean> {
        batches += files
        return saves(files)
    }

    override fun tell(text: String) {
        told += text
    }
}

/** Waits up to five seconds for [check] to hold, as a write the runtime launched lands. */
internal suspend fun eventually(check: () -> Boolean) {
    kotlinx.coroutines.withTimeout(5_000) { while (!check()) kotlinx.coroutines.delay(5) }
}

/** Settings signed in as [userId] with an access token still good. */
internal fun signedInSettings(userId: Long = 77): Settings = Settings()
    .with(com.aimdi.xtanative.store.settings.PixivPrefs.refreshToken, "refresh-me")
    .with(com.aimdi.xtanative.store.settings.PixivPrefs.accessToken, "access-1")
    .with(com.aimdi.xtanative.store.settings.PixivPrefs.accessExpiresAt, "2999-01-01T00:00:00Z")
    .with(com.aimdi.xtanative.store.settings.PixivPrefs.userId, userId)

/** What the plugin says at the foot of its screens from now on, as it says it. */
internal fun PixivRuntime.heard(): List<String> {
    val heard = java.util.Collections.synchronizedList(mutableListOf<String>())
    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { notices.collect { heard += it } }
    return heard
}

/** Haptics that only count. */
internal fun quietHaptics() = PixivHaptics({}, { false })

/** A runtime over [host], its work stopped once [block] ends. */
internal fun <T> withRuntime(host: PixivTestHost = PixivTestHost(), block: suspend (PixivRuntime) -> T): T = kotlinx.coroutines.runBlocking {
    val runtime = PixivRuntime(host)
    try {
        runtime.ready()
        block(runtime)
    } finally {
        runtime.scope.cancel()
    }
}
