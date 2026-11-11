package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivApp
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkApi
import com.aimdi.xtanative.networks.pixiv.PixivClient
import com.aimdi.xtanative.networks.pixiv.PixivCommentsApi
import com.aimdi.xtanative.networks.pixiv.PixivDiscoveryApi
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovelApi
import com.aimdi.xtanative.networks.pixiv.PixivSearchApi
import com.aimdi.xtanative.networks.pixiv.PixivSocialApi
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.FeedReads
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * What the Pixiv plugin's screens share while it runs: its settings (XTA's
 * Pixiv preferences, in the app's settings), the client reading as the signed-in account with
 * XTA's published repairs in force, the reads beside it, XTA's writes to that
 * account (bookmarks, follows, the watchlist), what the device keeps (mutes,
 * history, where novels were left, the creators in the app's groups and the
 * bookmarks it kept before), and the works the app's shared rows drew, which
 * their bookmark and menu file in the app's Saved.
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

    /** Where the reader stopped in each novel, in XTA's preference for it. */
    val novelPlaces = PixivNovelPlaces(settings) { host.settings.state.value?.get(PostsPrefs.rememberReadingPosition) ?: true }

    /** Following creators on Pixiv. */
    val following = PixivFollowing({ id, restrict -> social.follow(id, restrict) }, { id -> social.unfollow(id) })

    val bookmarking = PixivBookmarking(bookmarkApi, novels, following, { settings.now }, { client.storedUserId }, bookmarks)

    private val revealed = MutableStateFlow<Set<Long>>(emptySet())

    /** The comments whose links out of Pixiv the reader chose to see: shown so in their thread too. */
    val revealedComments: StateFlow<Set<Long>> = revealed.asStateFlow()

    fun revealComment(id: Long) = revealed.update { it + id }

    /** What a screen hands the next one it opens (the work as its list had it, the list itself), by key. */
    val shelf = PixivShelf()

    private val said = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** What the plugin tells the reader at the foot of its screens. */
    val notices: SharedFlow<String> = said.asSharedFlow()

    fun tell(message: String) {
        said.tryEmit(message)
    }

    private val watched = MutableSharedFlow<Boolean>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A series added to or taken off the watchlist on its page: true for a novel series. Home's watchlist reads itself again, as XTA's does. */
    val watchlistChanges: SharedFlow<Boolean> = watched.asSharedFlow()

    fun watchlistChanged(novels: Boolean) {
        watched.tryEmit(novels)
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

    /** [route] in place of the screen on top, as a novel's next chapter takes its place (XTA's pushReplacement). */
    fun replace(route: PixivRoute) {
        host.back()
        open(route)
    }

    val signedIn: Boolean get() = PixivAccounts.signedIn(settings.now)

    /** The reader's own Pixiv id, once a token response named it. */
    val ownId: Long? get() = client.storedUserId

    /** Forgets what this session changed on Pixiv, for another account (pixiv_accounts.dart's pixivAccountDataForgetter). */
    fun accountChanged() {
        bookmarking.clear()
        following.marks.clear()
    }

    /** Whether [id] is among the creators kept for the app's groups (XTA's Pixiv group subscriptions). */
    fun isFollowedHere(id: Long): Boolean = PixivFollows.isFollowed(settings.now, id)

    /** [id] no longer followed here and out of the app's groups, as XTA's PixivGroupSubscriptionsStore.remove takes it out of them. */
    fun unfollow(id: Long) {
        settings.update { PixivFollows.unfollowed(it, id) }
        host.unfollowed("$id")
    }

    /** The creators followed here as the app's Subscriptions list shows them (XTA's Pixiv subscriptions): a tap opens their profile. */
    val subscriptions: Flow<List<PluginSubscription>> = settings.state.map { PixivFollows.of(it).map(::subscriptionOf) }

    private fun subscriptionOf(follow: PixivFollow) = PluginSubscription(
        id = "${follow.id}",
        name = follow.displayName,
        handle = follow.account.ifEmpty { "${follow.id}" },
        avatarUrl = follow.avatarUrl,
        route = PixivRoutes.of(PixivRoute.User(follow.id)),
        followedAt = XtaTime.parse(follow.createdAt)?.toEpochMilli() ?: 0,
        unfollow = { unfollow(follow.id) },
    )

    /** Keeps [user] for the app's groups, as XTA's PixivGroupSubscriptionsStore.add: Pixiv is not told. */
    fun followHere(user: PixivUser) = settings.update { PixivFollows.followed(it, user, Instant.now().toString()) }

    /** [user]'s groups, followed here first once a group is picked (pixiv_group.dart's addPixivToGroup). */
    fun addToGroup(user: PixivUser) = host.addToGroup("${user.id}") { if (!isFollowedHere(user.id)) followHere(user) }

    private val drawn = DrawnPosts<PixivIllust> { "${it.id}" }
    private val records = DrawnPosts<PluginPostArchive> { it.id }

    /** [illust] as the app's shared rows draw it, kept so that what a row asks of it finds the work. */
    fun shown(illust: PixivIllust): NetworkPost = PixivShared.post(drawn.put(illust), settings[PixivPrefs.imageHost])

    /** A work or novel Saved kept as XTA's link record, as Saved draws it; null for another network's record. */
    fun shownSaved(content: JsonElement): NetworkPost? = PixivShared.record(content)?.let { PixivShared.saved(records.put(it), settings[PixivPrefs.imageHost]) }

    /** Where a work the app draws leads: its screen, its creator's; its bookmark and the shared post sheet. */
    val actions = NetworkPostActions(
        open = ::openShown,
        openAuthor = { post -> post.authorId.toLongOrNull()?.let { openUser(it) } ?: openShown(post) },
        openMention = {},
        openTag = { tag -> openTagSearch(tag) },
        openMedia = { post, _ -> openShown(post) },
        archive = { post -> drawn[post.id]?.let(PixivShared::archive) ?: records[post.id] },
        more = { post -> (drawn[post.id]?.let(PixivShared::menu) ?: records[post.id]?.let { PluginPostMenu(it, post.url.orEmpty()) })?.let(host::openPostMenu) },
    )

    private fun openShown(post: NetworkPost) {
        val illust = drawn[post.id]
        if (illust != null) openWork(illust) else post.url?.let(host::openLink)
    }

    /**
     * The creators followed here in the groups that hold them (pixiv_group.dart's
     * loadPixivGroupPosts): each one's newest works, those muted left out, those
     * with a date. Home's timelines have no switch for them in XTA.
     */
    val feed: PluginFeed = object : PluginFeed() {
        override suspend fun followed(): List<String> = PixivFollows.of(settings.load()).map { "${it.id}" }

        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
            val mutes = PixivMutes.of(settings.load())
            val creators = accounts.filter { it.toLongOrNull()?.let { id -> id > 0 } == true }
            return mutes.filter(FeedReads.each(creators, FeedReads.ACCOUNTS_AT_ONCE) { client.userIllusts(it.toLong()).items }).filter { it.createdAt != null }.map(::shown)
        }
    }

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
