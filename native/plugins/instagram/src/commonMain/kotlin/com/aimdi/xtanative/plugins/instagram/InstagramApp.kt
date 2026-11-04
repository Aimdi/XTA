package com.aimdi.xtanative.plugins.instagram

import com.aimdi.xtanative.core.feed.MemberCache
import com.aimdi.xtanative.networks.instagram.InstagramAuthor
import com.aimdi.xtanative.networks.instagram.InstagramClient
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.networks.instagram.InstagramProfile
import com.aimdi.xtanative.networks.instagram.InstagramRepairs
import com.aimdi.xtanative.store.settings.InstagramPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.plugins.MergedFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginNotices
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the Instagram plugin's screens share while it runs: the guest-first
 * client with XTA's published repairs in force and its cookies kept where
 * XTA keeps them (never backed up), the accounts followed and the posts
 * liked on this device, the Following feed, the preferences and where a tap
 * leads. Nothing is liked, followed or commented on Instagram.
 */
internal class InstagramApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val making = Mutex()
    private var made: InstagramClient? = null

    @Volatile
    private var keptCookies: String? = null

    val follows = InstagramFollows(host.store.file("follows.json"))
    val notices = PluginNotices()

    /** Each followed account's newest posts, a dozen of each, two read at once, kept ten minutes (instagram_store.dart). */
    private val members = MemberCache<InstagramPost>(dateOf = { it.createdAt }, perMember = 12, concurrency = 2)

    val following = MergedFeed(scope) { force, partial -> readFollowing(force, partial) }

    val prefs: StateFlow<Settings> = host.settings.state.map { it ?: Settings() }.stateIn(scope, SharingStarted.Eagerly, Settings())

    fun current(): Settings = host.settings.state.value ?: Settings()

    suspend fun settings(): Settings = host.settings.state.filterNotNull().first()

    /** The client, made once the app's settings are read, with the cookies kept from the last start. */
    suspend fun client(): InstagramClient = making.withLock {
        made ?: InstagramClient(
            http = host.http,
            cookies = settings()[InstagramPrefs.cookies].also { keptCookies = it },
            onCookies = ::keepCookies,
            repairs = { InstagramRepairs.published(host.repairs.value) },
        ).also { made = it }
    }

    /** Whether a whole session is held, read from the cookies kept. */
    fun hasSession(settings: Settings): Boolean = InstagramClient.hasSession(settings[InstagramPrefs.cookies])

    /** Every change of the jar is kept for the next start, as XTA keeps it after each answer. */
    private fun keepCookies(header: String) {
        if (header == keptCookies) return
        keptCookies = header
        update { it.with(InstagramPrefs.cookies, header) }
    }

    /** Runs [block] beyond the screen that asked for it: a follow, a like, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // What was kept stays as it was; the next change tries again.
            }
        }
    }

    fun update(edit: (Settings) -> Settings) = launch { host.settings.update(edit) }

    fun close() = scope.cancel()

    private suspend fun readFollowing(force: Boolean, onPartial: (List<InstagramPost>) -> Unit): List<InstagramPost> {
        val keys = follows.load().map { it.id }
        val client = client()
        return members.merge(keys, forceRefresh = force, maxFetches = FOLLOWS_PER_LOAD, onPartial = onPartial) { key -> client.profileMedia(key).items }
    }

    fun follow(profile: InstagramProfile) = launch {
        follows.follow(profile)
        following.refresh()
    }

    fun follow(author: InstagramAuthor) = launch {
        follows.follow(author)
        following.refresh()
    }

    fun unfollow(handle: String) = launch {
        follows.unfollow(handle)
        following.refresh()
    }

    /** A backup's followed accounts in place of these, and the feed read for them. */
    suspend fun restoreFollows(text: String) {
        follows.import(text)
        following.refresh()
    }

    fun toggleLike(id: String) = update { InstagramLikes.toggled(it, id) }

    fun remember(query: String) = update { it.with(InstagramPrefs.searchHistory, InstagramSearchHistory.remembered(it[InstagramPrefs.searchHistory], query)) }

    fun clearHistory() = update { it.with(InstagramPrefs.searchHistory, emptyList()) }

    suspend fun setCookies(raw: String) = client().setCookies(raw)

    suspend fun clearSession() = client().clearSession()

    /** Reads with the Instagram-family session pasted in Threads, as XTA copies it; false when Threads has none. */
    suspend fun importThreadsCookies(): Boolean {
        val raw = settings()[ThreadsPrefs.directCookies].trim()
        if (raw.isEmpty()) return false
        setCookies(raw)
        return true
    }

    fun openProfile(handle: String) = host.open(InstagramRoutes.profile(handle))

    fun openPhotos(post: InstagramPost, index: Int) = host.openMedia(InstagramMedia.photos(post), index)

    companion object {
        const val NETWORK = "Instagram"

        /** How many followed accounts one load asks (instagramMaxFollowsPerLoad). */
        const val FOLLOWS_PER_LOAD = 20
    }
}
