package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsDirectClient
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsReader
import com.aimdi.xtanative.networks.threads.ThreadsRepairs
import com.aimdi.xtanative.networks.threads.ThreadsReplyOrder
import com.aimdi.xtanative.networks.threads.ThreadsRssHub
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsSession
import com.aimdi.xtanative.networks.threads.ThreadsSetup
import com.aimdi.xtanative.networks.threads.ThreadsXy
import com.aimdi.xtanative.store.settings.MediaPrefs
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Threads plugin's screens share while it runs: Meta's public pages
 * and, when the reader pasted one, their session, read with XTA's published
 * repairs in force; an RSSHub instance or an Xy server when they named one;
 * the accounts followed and the posts liked on this device; the tab's feed;
 * the preferences (the app's settings under XTA's keys) and where a tap
 * leads. Nothing is posted, liked, reposted or followed on Threads.
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

    /** The settings now, the defaults until the app has read them. */
    fun current(): Settings = host.settings.state.value ?: Settings()

    /** The settings as they change, the defaults until the app has read them. */
    val prefs: StateFlow<Settings> = host.settings.state.map { it ?: Settings() }.stateIn(scope, SharingStarted.Eagerly, Settings())

    val options: StateFlow<ThreadsFeedOptions> = prefs.map { ThreadsFeedOptions.decode(it[ThreadsPrefs.feedOptions]) }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ThreadsFeedOptions())

    /** The order of a conversation's replies, kept for the rest of the run and shared by every conversation, as XTA keeps it. */
    val replyOrder = MutableStateFlow(ThreadsReplyOrder.TOP)

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

    fun unfollow(handle: String) = launch {
        accounts.remove(handle)
        feed.refresh(force = true)
    }

    fun toggleLike(post: ThreadsPost) = launch { likes.toggle(post) }

    fun setOptions(options: ThreadsFeedOptions) = update { it.with(ThreadsPrefs.feedOptions, options.encode()) }

    fun remember(query: String) = update { it.with(ThreadsPrefs.searchHistory, ThreadsSearchHistory.remembered(it[ThreadsPrefs.searchHistory], query)) }

    /** A backup's followed accounts in place of these, and the feed read for them. */
    suspend fun restoreAccounts(text: String) {
        accounts.import(text)
        feed.refresh(force = true)
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
    fun openMedia(post: ThreadsPost, index: Int) {
        val quality = current()[MediaPrefs.videoQuality]
        val viewed = ThreadsViewed(ThreadsMediaItems.items(post, quality), ThreadsMediaItems.qualities(post))
        host.open(ThreadsRoutes.media(shelf.put(viewed), index))
    }

    companion object {
        /** The network's name where a problem names it. */
        const val NETWORK = "Threads"
    }
}
