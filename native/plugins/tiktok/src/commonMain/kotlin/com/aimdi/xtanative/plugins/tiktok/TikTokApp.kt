package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.networks.tiktok.TikTokClient
import com.aimdi.xtanative.networks.tiktok.TikTokFollowing
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.networks.tiktok.TikTokProfile
import com.aimdi.xtanative.networks.tiktok.TikTokRepairs
import com.aimdi.xtanative.networks.tiktok.TikTokSession
import com.aimdi.xtanative.store.backup.TikTokSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.MergedFeed
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginNotices
import com.aimdi.xtanative.ui.plugins.PluginShelf
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the TikTok plugin's screens share while it runs: the guest client,
 * with XTA's published repairs in force and the guest session kept in the
 * plugin's folder; the accounts followed here and their Following timeline;
 * the preferences (the app's settings, under XTA's keys: hearts, recent
 * searches, the embed player); what one screen hands the next; the videos in
 * the app's groups and where they lead; and what the plugin tells the
 * reader. Nothing is written to TikTok. [playsHere]: the app plays videos
 * itself, as Android does; the desktop opens them on the site.
 */
internal class TikTokApp(val host: PluginHost, private val playsHere: Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionFile = TikTokSessionFile(host.store.file("session.json"))
    private val opening = Mutex()
    private val writing = Mutex()
    private val latestSession = AtomicReference<TikTokSession?>(null)
    private var made: TikTokClient? = null

    /** The tab last chosen, kept while the plugin runs as XTA keeps it for the session ('tiktok/view'). */
    val tab = MutableStateFlow(TikTokTab.FOLLOWING)

    val follows = TikTokFollows(host.store.file("follows.json"))
    val following = TikTokFollowing(read = { secUid -> client().creatorItems(secUid, cursor = "0").items })

    /**
     * The Following tab (XTA's TikTokFollowingStore): the merged videos of the
     * accounts followed here, painted as each account answers, kept while the
     * plugin runs.
     */
    val feed = MergedFeed<TikTokPost>(scope) { force, onPartial -> following.timeline(follows.load().map { it.secUid }, force, onPartial) }
    val shelf = PluginShelf()
    val notices = PluginNotices()
    private val drawn = TikTokDrawn()

    /** The app's settings once read; null before. */
    val settings: StateFlow<Settings?> get() = host.settings.state

    fun current(): Settings = host.settings.state.value ?: Settings()

    /** The guest client, made once with the session kept from before, which it keeps up to date. */
    suspend fun client(): TikTokClient = opening.withLock {
        made ?: TikTokClient(
            host.http,
            session = sessionFile.load(),
            onSession = ::keep,
            repairs = { TikTokRepairs.published(host.repairs.value) },
        ).also { made = it }
    }

    /** What every screen needs first: the settings and the accounts followed here. */
    suspend fun ready() {
        if (host.settings.state.value == null) {
            host.settings.update { it }
            host.settings.state.filterNotNull().first()
        }
        follows.load()
    }

    /** The plugin's own preferences changed by [edit] (`plugin.tiktok.`). */
    fun update(edit: (Settings) -> Settings) = launch { host.settings.update(edit) }

    /** Runs [block] beyond the screen that asked for it; a failure ends it quietly. */
    fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A row or a setting that could not be written stays as it was.
            }
        }
    }

    fun toggleLike(id: String) = update { TikTokMarks.withLikeToggled(it, id) }

    suspend fun rememberSearch(query: String) = host.settings.update { TikTokMarks.withSearch(it, query) }

    /** Forgets the guest's cookies and device id, and the timelines read with them. */
    suspend fun clearSession() {
        client().clearSession()
        following.clear()
    }

    fun close() = scope.cancel()

    fun openProfile(handle: String) = host.open(TikTokRoutes.profile(handle))

    fun openVideo(post: TikTokPost) = host.open(TikTokRoutes.video(post.id, post.author.uniqueId, shelf.put(post)))

    fun openSearch(query: String? = null) = host.open(TikTokRoutes.search(query))

    /** XTA's sheet over a video's long press, the app's shared post menu. */
    fun openPostMenu(post: TikTokPost) = host.openPostMenu(TikTokShared.menu(post))

    /** The app's group picker for an account followed here, or followed once a group is picked (XTA's addTikTokToGroup). */
    fun addToGroup(profile: TikTokProfile) = host.addToGroup(profile.uniqueId.lowercase()) { if (!follows.isFollowed(profile.uniqueId)) follows.follow(profile) }

    fun addToGroup(row: TikTokSubscriptionRow) = host.addToGroup(row.id)

    /** [handle] no longer followed here and out of the app's groups, as XTA's TikTokFollowsStore.unfollow takes it out of them. */
    suspend fun unfollow(handle: String) {
        follows.unfollow(handle)
        host.unfollowed(handle.lowercase())
    }

    /** The accounts followed here as the app's Subscriptions list shows them (XTA's TikTok subscriptions): a tap opens the profile. */
    val subscriptions: Flow<List<PluginSubscription>> = follows.state.onStart { follows.load() }.filterNotNull().map { rows -> rows.map(::subscriptionOf) }

    private fun subscriptionOf(row: TikTokSubscriptionRow) = PluginSubscription(
        id = row.id,
        name = row.name,
        avatarUrl = row.avatarUrl,
        route = TikTokRoutes.profile(row.id),
        followedAt = XtaTime.parse(row.createdAt)?.toEpochMilli() ?: 0,
        unfollow = { unfollow(row.id) },
    )

    /** [post] as the app's shared row draws it, kept for a tap on that row to open what it drew. */
    fun shown(post: TikTokPost): NetworkPost {
        drawn.put(post)
        return TikTokShared.post(post)
    }

    /** The video behind [shown], for its own card where the app draws it; null for one drawn too long ago. */
    fun drawnPost(shown: NetworkPost): TikTokPost? = drawn.get(shown.id)

    /** Where a video the app draws leads: the player (the site where there is none), its author, the search; its bookmark and the shared menu. */
    val actions = NetworkPostActions(
        open = ::play,
        openAuthor = { shown -> openProfile(shown.authorHandle) },
        openMention = ::openProfile,
        openTag = { query -> openSearch(query) },
        openMedia = { shown, _ -> play(shown) },
        archive = { shown -> drawn.get(shown.id)?.let(TikTokShared::archive) },
        more = { shown -> drawn.get(shown.id)?.let(::openPostMenu) },
    )

    /**
     * The accounts followed here in the app's groups (XTA's loadTikTokGroupPosts):
     * a group's members of TikTok, "tiktok:<handle>", read through the Following
     * tab's pages and their cache.
     */
    val shared: PluginFeed = object : PluginFeed() {
        override suspend fun followed(): List<String> = follows.load().map { it.id }

        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
            val wanted = accounts.toSet()
            val secUids = follows.load().filter { it.id in wanted }.map { it.secUid }
            return following.timeline(secUids, force).map(::shown)
        }
    }

    private fun play(shown: NetworkPost) {
        val post = drawn.get(shown.id)
        when {
            !playsHere -> shown.url?.let(host::openLink)
            post != null -> openVideo(post)
            else -> host.open(TikTokRoutes.video(shown.id, shown.authorHandle, null))
        }
    }

    /** The newest session TikTok handed out, written in turn, so an older one never lands last. */
    private fun keep(session: TikTokSession) {
        latestSession.set(session)
        launch { writing.withLock { latestSession.get()?.let { sessionFile.save(it) } } }
    }
}
