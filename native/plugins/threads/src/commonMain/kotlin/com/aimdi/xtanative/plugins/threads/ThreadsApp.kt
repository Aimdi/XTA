package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsAccountFailure
import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsDirectClient
import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import com.aimdi.xtanative.networks.threads.ThreadsFeedMix
import com.aimdi.xtanative.networks.threads.ThreadsForYou
import com.aimdi.xtanative.networks.threads.ThreadsForYouFeed
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPeople
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
    private val profiles = ThreadsProfileShelf(host.store::file, legacy = host.store.file("profiles.json"))
    private val conversations = ThreadsConversationShelf(host.store::file)
    val accountNotes = ThreadsAccountNotes(host.store.file("account_notes.json"))

    /** The posts the reader asked Threads to translate, as Meta translates them for a session. */
    val translations = ThreadsTranslations(scope) { post, target ->
        ThreadsPeople.mediaId(post)?.let { direct.translation(it, post.facts.captionId, target) }
    }

    /** The topics and communities followed on this device. */
    val topics = ThreadsTopics(host.store.file("topics.json"))

    /** Home's loads, given up once Home is no longer shown. */
    val homeLoads = ThreadsShownLoads()

    /** Where the reader stopped in the Home section. */
    val homePlace = ThreadsHomePlace(host.store.file("home.json"), scope)

    /**
     * Threads' own recommended feed: the pasted Bearer's For you timeline,
     * else threads.com's public front page, growing with each refresh and
     * going on into the topics and communities it explores.
     */
    val forYou = ThreadsForYouFeed(ThreadsForYou(direct, ::exploreTopics)::page)

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
        topics.load()
    }

    suspend fun settings(): Settings = host.settings.state.filterNotNull().first()

    /** A list's next page, tried again as Meta advises, as every read of the tab is; the client spaces the requests themselves. */
    suspend fun <T> nextPage(read: suspend () -> T): T = ThreadsRetryBudget.retrying(online = { host.online.first() }, read = read)

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

    private val signedInNow = MutableStateFlow<String?>(null)

    /** Whom the cookies signed in as when the session was last tried; null before that, or when it failed. */
    val signedIn: StateFlow<String?> = signedInNow

    private val signInsNow = MutableStateFlow(0)

    /** Counts the sessions taken from Threads' sign-in page, so the settings' fields show the new one. */
    val signIns: StateFlow<Int> = signInsNow

    /** [cookies] from Threads' sign-in page kept as a pasted session is, then tried once with a read; the settings say how it went. */
    fun signIn(cookies: String) = launch {
        ready()
        host.settings.update { it.with(ThreadsPrefs.directCookies, cookies) }
        signInsNow.update { it + 1 }
        notices.tell(checkSession())
    }

    /**
     * The session tried once with a read: "Signed in as @handle" for cookies,
     * the Bearer's own read otherwise, or the settings' words for why it failed.
     */
    suspend fun checkSession(): String {
        val language = host.language
        return try {
            if (!session().hasCookies) return Strings.direct_test_ok.get(language, direct.verify())
            val handle = direct.currentUser().username
            signedInNow.value = handle
            Strings.signed_in_as.get(language, handle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            signedInNow.value = null
            ThreadsFailures.settings(e).get(language)
        }
    }

    /**
     * Whom the cookies sign in as, for the reader's own profile: known since
     * the session was last tried, else asked once (a session read) and kept
     * for the run. Null when it cannot be read, the notices saying why.
     */
    suspend fun ownHandle(): String? {
        signedInNow.value?.let { return it }
        return try {
            direct.currentUser().username.also { signedInNow.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notices.tell(ThreadsFailures.settings(e).get(host.language))
            null
        }
    }

    /** The session gone from this device: the cookies and the Bearer, and whom they signed in as. */
    fun clearSession() {
        signedInNow.value = null
        update { it.with(ThreadsPrefs.directCookies, "").with(ThreadsPrefs.directBearer, "") }
    }

    fun setup(): ThreadsSetup = ThreadsSetup(
        rssHubInstance = current()[ThreadsPrefs.instance],
        xyBase = current()[ThreadsPrefs.apiBase].ifBlank { ThreadsXy.DEFAULT_BASE },
        xyToken = current()[ThreadsPrefs.apiToken],
    )

    /** Paints the feed the reader last saw, before anything is asked of Meta (threads_store.dart's `restore`). */
    suspend fun restoreFeed() {
        ready()
        homePlace.restore()
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
        val followedTopics = topicPages()
        var painted: List<ThreadsPost>? = null
        val held = reader.heldExtras(handles, followedTopics)
        val posts = try {
            homeLoads.whileShown {
                val own = ownPosts(handles, followedTopics.isNotEmpty(), force) { partial ->
                    painted = ThreadsFeedMix.merge(partial, held).also(onPartial)
                }
                ThreadsFeedMix.merge(own, reader.extras(handles, followedTopics))
            } ?: return painted ?: feed.state.value.items
        } finally {
            failedNow.value = reader.failures(handles)
        }
        if (handles.isNotEmpty()) {
            accounts.keepCurrent(posts)
            remember(posts)
        }
        return posts
    }

    /** The followed accounts' threads; with none followed but topics, nothing of their own instead of Home's "follow someone". */
    private suspend fun ownPosts(handles: List<String>, topicsFollowed: Boolean, force: Boolean, onPartial: (List<ThreadsPost>) -> Unit): List<ThreadsPost> =
        try {
            reader.following(handles, force, onPartial)
        } catch (e: ThreadsException) {
            if (!topicsFollowed || e.failure != ThreadsFailure.NOT_CONFIGURED) throw e
            emptyList()
        }

    /** What Home reads for each followed topic: a community's own page where Meta named one, else the topic's name. */
    private suspend fun topicPages(): List<String> = topics.load().map { it.url ?: it.name }

    /** The topics and communities For you shows to explore: those followed here, then those of the posts For you and Home show. */
    fun discoveredTopics(): List<ThreadsTopic> =
        ThreadsDiscovery.topics(forYou.state.value.posts + feed.state.value.items, topics.state.value.orEmpty(), cap = ThreadsForYou.MAX_EXPLORED)

    /** What For you explores past the public page: [discoveredTopics], then a few every guest can read. */
    private fun exploreTopics(): List<String> =
        (discoveredTopics().map(ThreadsDiscovery::page) + ThreadsDiscovery.STARTER_TOPICS).distinctBy { it.lowercase() }

    /** Follows [topic] here, says so, and has Home read its posts. */
    fun followTopic(topic: ThreadsTopic) = launch {
        topics.follow(topic)
        notices.tell(Strings.topic_followed.get(host.language))
        feed.refresh()
    }

    fun unfollowTopic(name: String) = launch {
        topics.unfollow(name)
        feed.refresh()
    }

    /** Only the followed accounts' feed is kept: a Bearer's "For you" is no one's account. */
    private suspend fun remember(posts: List<ThreadsPost>) {
        if (posts.isEmpty()) return
        val snapshot = ThreadsFeedSnapshot(posts, reader.answeredAt).encode()
        host.settings.update { it.with(ThreadsPrefs.feedSnapshot, snapshot) }
    }

    private val failedNow = MutableStateFlow<List<ThreadsAccountFailure>>(emptyList())

    /** The followed accounts whose last read failed, with why, as the last load left them. */
    val failures: StateFlow<List<ThreadsAccountFailure>> = failedNow

    private val retryingNow = MutableStateFlow<Set<String>>(emptySet())

    /** The accounts being read again from the failed accounts' list. */
    val retrying: StateFlow<Set<String>> = retryingNow

    /** [handle] read again now, its row saying so meanwhile; then the feed takes what it gave. */
    fun retryAccount(handle: String) = launch {
        if (handle in retryingNow.value) return@launch
        retryingNow.update { it + handle }
        try {
            ready()
            attemptKept { reader.retry(handle) }
            failedNow.value = reader.failures(ThreadsAccounts.handles(accounts.load()))
        } finally {
            retryingNow.update { it - handle }
        }
        feed.refresh()
    }

    // ---- The For you section ----

    /** Paints the For you feed and the section the reader last saw, once, before anything is asked of Meta. */
    suspend fun restoreForYou() = forYouRestore.withLock {
        if (forYouRestored) return@withLock
        val text = forYouFile.read()
        val kept = ThreadsForYouKept.decode(text)
        forYou.restore(kept.posts, kept.source, kept.readAt)
        forYouPlace = kept.place
        section.value = ThreadsForYouKept.openingSection(text, follows = attemptKept { accounts.load() }.orEmpty().isNotEmpty())
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

    /**
     * Follows [account] on this device, says so, and has the feed read for it:
     * the new account is asked, the others give what was read within their ten minutes.
     */
    fun follow(account: ThreadsAccount) = launch { followed(listOf(account)) }

    /** Follows [profile] from its page, says so, and has the feed read for it. */
    fun followProfile(profile: ThreadsProfile) = follow(ThreadsAccount.of(profile))

    private suspend fun followed(added: List<ThreadsAccount>) {
        if (added.isEmpty()) return
        accounts.addAll(added)
        notices.tell(Strings.account_added.get(host.language))
        feed.refresh()
    }

    /**
     * Follows [handles], typed or pasted: one with what its profile says, or
     * its handle alone when the card cannot be read; several by their handles
     * at once, their names coming with their first posts. Their public posts
     * load either way.
     */
    fun followHandles(handles: List<String>) = launch {
        ready()
        val one = handles.singleOrNull()?.let { handle -> attemptKept { direct.profile(handle) } }
        followed(if (one != null) listOf(ThreadsAccount.of(one)) else handles.map { ThreadsAccount(it, it) })
    }

    fun unfollow(handle: String) = launch { unfollowNow(handle) }

    /** What [handle]'s profile showed when it was last read; null when it was not kept, or cannot be read. */
    suspend fun keptProfile(handle: String): ThreadsKeptProfile? = attemptKept { profiles.of(handle) }

    /** [page] kept as [handle]'s profile for the next time it opens. */
    fun keepProfile(handle: String, page: ThreadsProfilePage) = launch { profiles.keep(handle, page, Instant.now()) }

    /** What the conversation at [url] showed when it was last read; null when it was not kept, or cannot be read. */
    suspend fun keptConversation(url: String): ThreadsKeptEntry<ThreadsConversation>? = attemptKept { conversations.of(url) }

    /** [conversation] kept as [url]'s for the next time it opens. */
    fun keepConversation(url: String, conversation: ThreadsConversation) = launch { conversations.keep(url, conversation, Instant.now()) }

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
        feed.refresh()
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
        feed.refresh()
    }

    // ---- The app's timelines, Saved and the post sheet ----

    /** [post] as the app's shared row draws it, kept (its quote with it) so that its taps, bookmark and menu find it again. */
    fun shown(post: ThreadsPost): NetworkPost {
        drawn.put(post)
        post.quoted?.let(drawn::put)
        val settings = current()
        val covered = Strings.media_covered.get(host.language).takeIf { ThreadsMediaCover.hides(settings) }
        return ThreadsShared.post(post, settings[MediaPrefs.videoQuality], Strings.spoiler.get(host.language), covered)
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
    fun openMenu(post: ThreadsPost) = host.openPostMenu(ThreadsShared.menu(this, post))

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
            val posts = try {
                ThreadsRetryBudget.retrying(online = { host.online.first() }) { ThreadsFeedMix.merge(reader.postsFor(accounts, force), reader.heldExtras(accounts)) }
            } finally {
                failedNow.value = reader.failures(followed())
            }
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

    /** A community's own page; a plain topic's search page. */
    fun openTopic(topic: ThreadsTopic) = if (topic.community) host.open(ThreadsRoutes.community(topic)) else openTag(topic.name)

    fun openSearch(terms: ThreadsSearchTerms) = host.open(ThreadsRoutes.search(terms))

    /** [post]'s pictures and videos in the viewer, from [index], each video at the quality the setting picks. */
    fun openMedia(post: ThreadsPost, index: Int, opens: Boolean = true) {
        val quality = current()[MediaPrefs.videoQuality]
        val viewed = ThreadsViewed(ThreadsMediaItems.items(post, quality), ThreadsMediaItems.qualities(post), post.takeIf { opens }, post)
        host.open(ThreadsRoutes.media(shelf.put(viewed), index))
    }

    /**
     * [post]'s [index]th picture full screen among every picture and video
     * of [among], a conversation's posts as shown, to swipe through them
     * all; back on the conversation, each is in its post. [post] alone when
     * it is not one of them.
     */
    fun openMedia(post: ThreadsPost, index: Int, opens: Boolean, among: List<ThreadsPost>?) {
        val start = among?.let { ThreadsMediaItems.galleryStart(it, post, index) } ?: return openMedia(post, index, opens)
        val quality = current()[MediaPrefs.videoQuality]
        val viewed = ThreadsViewed(among.flatMap { ThreadsMediaItems.items(it, quality) }, among.flatMap(ThreadsMediaItems::qualities))
        host.open(ThreadsRoutes.media(shelf.put(viewed), start))
    }

    /** [work] run beyond the screen that started it, as a batch of downloads is. */
    fun beyondScreen(work: suspend () -> Unit) {
        scope.launch { work() }
    }

    companion object {
        /** The network's name where a problem names it. */
        const val NETWORK = "Threads"

        /** How long after the last change the For you section is written to its file. */
        private const val KEEP_AFTER_MILLIS = 1_500L
    }
}
