package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsDirectClient
import com.aimdi.xtanative.networks.threads.ThreadsForYou
import com.aimdi.xtanative.networks.threads.ThreadsForYouFeed
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.networks.threads.ThreadsReader
import com.aimdi.xtanative.networks.threads.ThreadsRepairs
import com.aimdi.xtanative.networks.threads.ThreadsReplyOrder
import com.aimdi.xtanative.networks.threads.ThreadsRetryBudget
import com.aimdi.xtanative.networks.threads.ThreadsRssHub
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsSession
import com.aimdi.xtanative.networks.threads.ThreadsSetup
import com.aimdi.xtanative.networks.threads.ThreadsXy
import com.aimdi.xtanative.store.backup.ProfileSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.MergedFeed
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFollowable
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginNotices
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSearchHistory
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the Threads plugin's screens share while it runs: Meta's public pages
 * and, when the reader pasted one, their session, read with XTA's published
 * repairs in force; an RSSHub instance or an Xy server when they named one;
 * the accounts followed and the posts liked on this device; the tab's feed
 * and the followed accounts' part of the app's timelines; the preferences
 * (the app's settings under XTA's keys); the posts the app drew lately, which
 * their bookmark and menu file in the app's archive; and where a tap leads.
 * Nothing is posted, liked, reposted or followed on Threads.
 */
internal class ThreadsApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val direct = ThreadsDirectClient(
        http = host.http,
        session = ::session,
        memory = ThreadsSettingsMemory(host.settings.state, host.settings::update, scope),
        repairs = { ThreadsRepairs.published(host.repairs.value) },
    )
    val rssHub = ThreadsRssHub(host.http)
    val xy = ThreadsXy(host.http)
    val reader = ThreadsReader(direct, rssHub, xy, setup = ::setup)

    val accounts = ThreadsAccounts(host.store.file("accounts.json"))
    val likes = ThreadsLikes(host.store.file("likes.json"), ::settings, host.settings::update)
    val feed = MergedFeed(scope, ::readFeed)
    val notices = PluginNotices()
    val shelf = ThreadsShelf()
    private val profiles = ThreadsProfileShelf(host.store.file("profiles.json"))

    /** Threads' own recommended feed: the pasted Bearer's For you timeline, else threads.com's public front page. */
    val forYou = ThreadsForYouFeed(ThreadsForYou(direct)::page)

    /** The section the tab shows. */
    val section = MutableStateFlow(ThreadsSection.HOME)

    /** Where the reader stopped in the For you feed. */
    @Volatile
    var forYouPlace = ThreadsPlace()
        private set

    private val forYouFile = host.store.file("for_you.json")
    private val forYouRestore = Mutex()
    private var forYouRestored = false
    private val forYouChanged = MutableStateFlow(0)

    /** The posts the app's shared rows drew lately, by id: what their taps, bookmark and menu find again. */
    private val drawn = DrawnPosts<ThreadsPost> { it.id }

    /** The settings now, the defaults until the app has read them. */
    fun current(): Settings = host.settings.state.value ?: Settings()

    /** The settings as they change, the defaults until the app has read them. */
    val prefs: StateFlow<Settings> = host.settings.state.map { it ?: Settings() }.stateIn(scope, SharingStarted.Eagerly, Settings())

    val options: StateFlow<ThreadsFeedOptions> = prefs.map { ThreadsFeedOptions.decode(it[ThreadsPrefs.feedOptions]) }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ThreadsFeedOptions())

    /** The order of a conversation's replies, kept for the rest of the run and shared by every conversation, as XTA keeps it. */
    val replyOrder = MutableStateFlow(ThreadsReplyOrder.TOP)

    init {
        // What the For you section keeps is written a moment after the last change, not at every scrolled row.
        scope.launch {
            forYouChanged.collectLatest { change ->
                if (change == 0) return@collectLatest
                delay(KEEP_AFTER_MILLIS)
                keepForYou()
            }
        }
    }

    /** Waits for what every read needs: the app's settings, the accounts and the likes. */
    suspend fun ready() {
        settings()
        accounts.load()
        likes.load()
    }

    suspend fun settings(): Settings = host.settings.state.filterNotNull().first()

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

    /**
     * The session as the reader pasted it, read afresh on every request: in
     * XTA's preferences, which no backup or export carries (`SecretPrefs`), so
     * Instagram can copy its cookies as XTA's does.
     */
    fun session(): ThreadsSession = ThreadsSessions.of(current())

    fun setup(): ThreadsSetup = ThreadsSetup(
        rssHubInstance = current()[ThreadsPrefs.instance],
        xyBase = current()[ThreadsPrefs.apiBase].ifBlank { ThreadsXy.DEFAULT_BASE },
        xyToken = current()[ThreadsPrefs.apiToken],
    )

    /** Paints the feed the reader last saw, before anything is asked of Meta (threads_store.dart's `restore`). */
    suspend fun restoreFeed() {
        ready()
        if (feed.state.value.items.isNotEmpty()) return
        val handles = ThreadsAccounts.handles(accounts.load()).toSet()
        val snapshot = ThreadsFeedSnapshot.decode(settings()[ThreadsPrefs.feedSnapshot]).followedBy(handles)
        if (snapshot.isEmpty) return
        snapshot.answeredAt.forEach { (handle, at) -> reader.remember(handle, snapshot.postsOf(handle), at) }
        feed.restore(snapshot.posts)
    }

    private suspend fun readFeed(force: Boolean, onPartial: (List<ThreadsPost>) -> Unit): List<ThreadsPost> {
        ready()
        val handles = ThreadsAccounts.handles(accounts.load())
        val posts = reader.following(handles, force, onPartial)
        if (handles.isNotEmpty()) {
            accounts.keepCurrent(posts)
            remember(posts)
        }
        return posts
    }

    /** Only the followed accounts' feed is kept: a Bearer's "For you" is no one's account. */
    private suspend fun remember(posts: List<ThreadsPost>) {
        if (posts.isEmpty()) return
        val snapshot = ThreadsFeedSnapshot(posts, reader.answeredAt).encode()
        host.settings.update { it.with(ThreadsPrefs.feedSnapshot, snapshot) }
    }

    // ---- The For you section ----

    /** Paints the For you feed and the section the reader last saw, once, before anything is asked of Meta. */
    suspend fun restoreForYou() = forYouRestore.withLock {
        if (forYouRestored) return@withLock
        val kept = ThreadsForYouKept.decode(forYouFile.read())
        forYou.restore(kept.posts, kept.source, kept.readAt)
        forYouPlace = kept.place
        section.value = kept.section
        forYouRestored = true
    }

    /** Reads the For you feed anew: a new selection, which starts at its top. */
    fun refreshForYou() = launch {
        ready()
        val before = forYou.state.value.readAt
        forYou.refresh()
        if (forYou.state.value.readAt != before) forYouPlace = ThreadsPlace()
        forYouChanged.update { it + 1 }
    }

    fun moreForYou() = launch {
        forYou.more()
        forYouChanged.update { it + 1 }
    }

    fun retryMoreForYou() = launch {
        forYou.retryMore()
        forYouChanged.update { it + 1 }
    }

    fun keepForYouPlace(place: ThreadsPlace) {
        if (place == forYouPlace) return
        forYouPlace = place
        forYouChanged.update { it + 1 }
    }

    /** Shows [shown], and remembers it for the next launch. */
    fun show(shown: ThreadsSection) {
        if (section.value == shown) return
        section.value = shown
        forYouChanged.update { it + 1 }
    }

    private suspend fun keepForYou() {
        if (!forYouRestored) return
        val state = forYou.state.value
        forYouFile.write(ThreadsForYouKept(state.posts, state.source, state.readAt, forYouPlace, section.value).encode())
    }

    /** Follows [account] on this device, and has the feed read for it. */
    fun follow(account: ThreadsAccount, force: Boolean = true) = launch {
        accounts.add(account)
        feed.refresh(force)
    }

    /** Follows [profile] from its page, says so, and has the feed read for it. */
    fun followProfile(profile: ThreadsProfile) = launch {
        accounts.add(ThreadsAccount.of(profile))
        notices.tell(Strings.account_added.get(host.language))
        feed.refresh()
    }

    /** Follows [handle] with what its profile says, or its handle alone when the card cannot be read: its public posts load either way. */
    fun followHandle(handle: String) = launch {
        ready()
        val profile = try {
            direct.profile(handle)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        accounts.add(profile?.let(ThreadsAccount::of) ?: ThreadsAccount(handle, handle))
        feed.refresh(force = true)
    }

    fun unfollow(handle: String) = launch { unfollowNow(handle) }

    /** What [handle]'s profile showed when it was last read; null when it was not kept, or cannot be read. */
    suspend fun keptProfile(handle: String): ThreadsKeptProfile? = attemptKept { profiles.of(handle) }

    /** [page] kept as [handle]'s profile for the next time it opens. */
    fun keepProfile(handle: String, page: ThreadsProfilePage) = launch { profiles.keep(handle, page, Instant.now()) }

    private suspend fun <T> attemptKept(read: suspend () -> T?): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** [handle] no longer followed here and out of the app's groups (XTA's ThreadsAccountsStore.remove); the feed read without it. */
    private suspend fun unfollowNow(handle: String) {
        accounts.remove(handle)
        host.unfollowed(handle.trim().lowercase())
        feed.refresh(force = true)
    }

    /** The accounts followed here as the app's Subscriptions list shows them (XTA's ThreadsSubscription): a tap opens the profile. */
    val subscriptions: Flow<List<PluginSubscription>> = accounts.state.onStart { accounts.load() }.filterNotNull().map { rows -> rows.map(::subscriptionOf) }

    private fun subscriptionOf(row: ProfileSubscriptionRow) = PluginSubscription(
        id = row.id,
        name = row.name,
        avatarUrl = row.avatarUrl,
        route = ThreadsRoutes.profile(row.id),
        followedAt = XtaTime.parse(row.createdAt)?.toEpochMilli() ?: 0,
        unfollow = { unfollowNow(row.id) },
    )

    /** [account] followed here unless it is, as putting it in a group follows it first (XTA's addThreadsAccountToGroup). */
    suspend fun ensureFollowed(account: ThreadsAccount) {
        if (ThreadsAccounts.follows(accounts.load(), account.handle)) return
        accounts.add(account)
        feed.refresh()
    }

    /**
     * [text] as a handle for a group's add box ("@handle"), followed here
     * without a read, as XTA's box follows it: the handle is its id either way,
     * and its name comes with its first posts.
     */
    fun followable(text: String): PluginFollowable? {
        val handle = ThreadsLinks.normaliseHandle(text) ?: return null
        return PluginFollowable("@$handle") {
            ensureFollowed(ThreadsAccount(handle, handle))
            handle
        }
    }

    /** [account] in the app's groups, followed here first once a group is picked. */
    fun addToGroup(account: ThreadsAccount) = host.addToGroup(account.handle.trim().lowercase()) { ensureFollowed(account) }

    /** [post] liked here, or no longer; the first like the reader makes says that likes stay on this device, as XTA's heart does. */
    fun toggleLike(post: ThreadsPost) = launch { if (likes.toggle(post)) host.likedOnDevice() }

    fun setOptions(options: ThreadsFeedOptions) = update { it.with(ThreadsPrefs.feedOptions, options.encode()) }

    fun remember(query: String) = update { it.with(ThreadsPrefs.searchHistory, PluginSearchHistory.remembered(it[ThreadsPrefs.searchHistory], query)) }

    /** [query] out of the recent searches, as XTA's other plugins let a reader drop one. */
    fun forget(query: String) = update { it.with(ThreadsPrefs.searchHistory, PluginSearchHistory.forgotten(it[ThreadsPrefs.searchHistory], query)) }

    /** A backup's followed accounts in place of these, and the feed read for them. */
    suspend fun restoreAccounts(text: String) {
        accounts.import(text)
        feed.refresh(force = true)
    }

    // ---- The app's timelines, Saved and the post sheet ----

    /** [post] as the app's shared row draws it, kept (its quote with it) so that its taps, bookmark and menu find it again. */
    fun shown(post: ThreadsPost): NetworkPost {
        drawn.put(post)
        post.quoted?.let(drawn::put)
        return ThreadsShared.post(post, current()[MediaPrefs.videoQuality])
    }

    /** The post [shown] names, as the app drew it lately; null for one not drawn lately. */
    fun drawnPost(shown: NetworkPost): ThreadsPost? = drawn[shown.id]

    /** Where a post the app draws leads: its conversation, its author, the viewer; its bookmark and the shared post sheet. */
    val actions = NetworkPostActions(
        open = { shown -> drawn[shown.id]?.let(::openPost) ?: shown.url?.let(host::openLink) },
        openAuthor = { shown -> openProfile(shown.authorId) },
        openMention = { who -> openProfile(ThreadsLinks.normaliseHandle(who) ?: who.lowercase()) },
        openTag = ::openTag,
        openMedia = { shown, index -> drawn[shown.id]?.let { openMedia(it, index) } ?: host.openMedia(shown.media, index) },
        archive = { shown -> drawn[shown.id]?.let(ThreadsArchive::of) },
        more = { shown -> drawn[shown.id]?.let(::openMenu) },
    )

    /** XTA's one post sheet for [post] (showPluginPostActions): Saved, its note, its author's groups, its link. */
    fun openMenu(post: ThreadsPost) = host.openPostMenu(
        PluginPostMenu(ThreadsArchive.of(post), post.url ?: post.id, author = post.handle.trim().lowercase(), follow = { ensureFollowed(ThreadsAccount.of(post)) }),
    )

    /** The accounts followed here in Home's timelines and the groups' (XTA's loadThreadsInterleaved). */
    val shared: PluginFeed = SharedFeed()

    private inner class SharedFeed : PluginFeed() {
        override suspend fun followed(): List<String> = ThreadsAccounts.handles(accounts.load())

        /**
         * Through the tab's reads and their ten minutes, so the tab, Home and
         * the groups ask Meta once; tried again as Meta advises; at most ten
         * of each account, as XTA mixes Threads in.
         */
        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
            ready()
            val posts = ThreadsRetryBudget.retrying(online = { host.online.first() }) { reader.postsFor(accounts, force) }
            this@ThreadsApp.accounts.keepCurrent(posts)
            return ThreadsShared.mixed(posts, kinds).map(::shown)
        }
    }

    // ---- Where a tap leads ----

    fun openPost(post: ThreadsPost) {
        val route = ThreadsRoutes.post(post, shelf.put(post)) ?: return
        host.open(route)
    }

    fun openProfile(handle: String) = host.open(ThreadsRoutes.profile(handle))

    fun openTag(tag: String) = host.open(ThreadsRoutes.search(ThreadsSearchTerms(tag.removePrefix("#"), tag = true)))

    fun openSearch(terms: ThreadsSearchTerms) = host.open(ThreadsRoutes.search(terms))

    /** [post]'s pictures and videos in the viewer, from [index], each video at the quality the setting picks. */
    fun openMedia(post: ThreadsPost, index: Int, opens: Boolean = true) {
        val quality = current()[MediaPrefs.videoQuality]
        val viewed = ThreadsViewed(ThreadsMediaItems.items(post, quality), ThreadsMediaItems.qualities(post), post.takeIf { opens })
        host.open(ThreadsRoutes.media(shelf.put(viewed), index))
    }

    companion object {
        /** The network's name where a problem names it. */
        const val NETWORK = "Threads"

        /** How long after the last change the For you section is written to its file. */
        private const val KEEP_AFTER_MILLIS = 1_500L
    }
}
