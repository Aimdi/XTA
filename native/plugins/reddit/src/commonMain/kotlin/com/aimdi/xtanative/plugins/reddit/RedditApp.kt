package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.reddit.RedditAuth
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditCommentSort
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditReaders
import com.aimdi.xtanative.networks.reddit.RedditReading
import com.aimdi.xtanative.networks.reddit.RedditSearchSort
import com.aimdi.xtanative.networks.reddit.RedditSession
import com.aimdi.xtanative.networks.reddit.RedditSignIn
import com.aimdi.xtanative.networks.reddit.RedditSubredditAbout
import com.aimdi.xtanative.networks.reddit.RedditSubredditResult
import com.aimdi.xtanative.networks.reddit.RedditThread
import com.aimdi.xtanative.networks.reddit.RedditUserResult
import com.aimdi.xtanative.store.settings.RedditFeedMode
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.RedditSource
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginHost
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Reddit plugin's screens share while it runs: its readers under
 * the repairs in force, the session every read goes through, what it keeps
 * on the device (preferences, followed communities, upvotes, saved posts),
 * the merged feed, and where a tap leads.
 */
internal class RedditApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val readers = RedditReaders(host.http) { host.repairs.value }
    private val auth = RedditAuth(host.http)
    private val sessions = RedditSignIn(auth)

    val prefs = RedditPrefsStore(host.store.file("settings.json"))
    val subscriptions = RedditSubscriptions(host.store.file("subscriptions.json"), prefs)
    val votes = RedditVotes(host.store.file("votes.json"))
    val icons = RedditIcons(RedditIconCache(host.store.file("icons.json")), scope) { name -> reading().client.icon(name, session()) }
    val feed = RedditFeed(scope, read = ::readFeed)

    val choices: StateFlow<RedditChoices> = prefs.state.map(RedditChoices::of).stateIn(scope, SharingStarted.Eagerly, RedditChoices.of(Settings()))
    val home: StateFlow<RedditHomeSource> = prefs.state.map(RedditHomeSource::of).stateIn(scope, SharingStarted.Eagerly, RedditHomeSource())
    val saved: StateFlow<List<RedditPost>> = prefs.state.map(RedditSaved::posts).stateIn(scope, SharingStarted.Eagerly, emptyList())
    val followed: StateFlow<List<String>> = subscriptions.state.map(RedditSubscriptions::names).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val changes = MutableStateFlow(0)

    /** Grows each time what a listing reads changes (the sort, the route, the sign-in, the NSFW display): open listings read again. */
    val generation: StateFlow<Int> = changes.asStateFlow()

    private val shelf = RedditShelf()

    init {
        launch {
            prefs.load()
            votes.load()
            val names = RedditSubscriptions.names(subscriptions.load())
            prefs.update { RedditHomeSource.reconciled(it, names) }
        }
    }

    fun reading(): RedditReading = readers.current()

    /**
     * How reads reach Reddit now (XTA's `RedditReadSession.resolve`): the
     * account-free route when asked for, the sign-in when there is one, else
     * the client id. Reddit ending the sign-in signs the reader out.
     */
    suspend fun session(): RedditSession {
        val settings = prefs.load()
        val resolved = sessions.resolve(
            clientId = settings[RedditPrefs.clientId].trim(),
            refreshToken = settings[RedditPrefs.refreshToken].trim().ifEmpty { null },
            preferPublic = settings[RedditPrefs.source] == RedditSource.PUBLIC,
        )
        if (resolved.signedOut) prefs.set(RedditPrefs.refreshToken, "")
        return resolved.session
    }

    /** Runs [block] beyond the screen that asked for it: a follow, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { attempt { block() } }
    }

    fun close() = scope.cancel()

    private suspend fun readFeed(names: List<String>, force: Boolean): List<RedditPost> {
        val choices = RedditChoices.of(prefs.load())
        return reading().feed.posts(
            names,
            sort = choices.sort.listing(),
            timeFilter = choices.window.listing(),
            hideNsfw = choices.hidesNsfw,
            forceRefresh = force,
            session = session(),
        )
    }

    suspend fun listing(subreddit: String, after: String?): Page<RedditPost> {
        val choices = RedditChoices.of(prefs.load())
        val page = reading().client.listing(subreddit, choices.sort.listing(), choices.window.listing(), after, session = session())
        return Page(choices.shown(page.items), page.next)
    }

    suspend fun userPosts(user: String, after: String?): Page<RedditPost> {
        val page = reading().client.userPosts(user, after)
        return Page(RedditChoices.of(prefs.load()).shown(page.items), page.next)
    }

    suspend fun thread(permalink: String, sort: RedditCommentSort): RedditThread = reading().client.thread(permalink, sort, session())

    suspend fun post(permalink: String): RedditPost? = reading().client.post(permalink, session())

    suspend fun moreComments(stub: RedditComment): List<RedditComment> = reading().client.moreComments(stub)

    suspend fun about(subreddit: String): RedditSubredditAbout = reading().client.about(subreddit, session())

    suspend fun galleryImages(permalink: String): List<String> = reading().galleries.images(permalink)

    suspend fun searchPosts(query: String, subreddit: String?, sort: RedditSearchSort): Page<RedditPost> {
        val page = reading().client.searchPosts(query, subreddit, sort, session())
        return Page(RedditChoices.of(prefs.load()).shown(page.items), null)
    }

    suspend fun searchSubreddits(query: String): Page<RedditSubredditResult> = reading().client.searchSubreddits(query, session())

    suspend fun searchUsers(query: String): Page<RedditUserResult> = reading().client.searchUsers(query, session())

    /** When [permalink] was opened before, noting that it is open now. */
    suspend fun visit(permalink: String): Instant? {
        var last: Instant? = null
        prefs.update { settings ->
            val visits = settings[RedditPrefs.threadVisits]
            last = RedditVisits.last(visits, permalink)
            settings.with(RedditPrefs.threadVisits, RedditVisits.recorded(visits, permalink, Instant.now()))
        }
        return last
    }

    fun follow(subreddit: String, follow: Boolean) = launch {
        if (follow) subscriptions.add(subreddit) else subscriptions.remove(subreddit)
        feed.refresh(RedditSubscriptions.names(subscriptions.load()))
    }

    /** A backup's followed communities in place of these, and the feed read for them. */
    suspend fun restoreSubscriptions(text: String) {
        subscriptions.import(text)
        feed.refresh(RedditSubscriptions.names(subscriptions.load()))
    }

    fun toggleVote(id: String) = launch { votes.toggle(id) }

    fun toggleSaved(post: RedditPost) = launch { prefs.update { RedditSaved.with(it, RedditSaved.toggled(RedditSaved.posts(it), post)) } }

    fun selectMode(mode: RedditFeedMode) = launch { prefs.update { RedditHomeSource.selectingMode(it, mode) } }

    fun selectSubreddit(name: String) = launch { prefs.update { RedditHomeSource.selectingSubreddit(it, name) } }

    /** Changes what listings read, and has the open ones and the feed read again. */
    fun change(edit: (Settings) -> Settings) = launch {
        prefs.update(edit)
        reread()
    }

    /** Changes how what is read is shown. */
    fun store(edit: (Settings) -> Settings) = launch { prefs.update(edit) }

    fun setClientId(clientId: String) = launch {
        prefs.set(RedditPrefs.clientId, clientId)
        credentialsChanged()
    }

    /** Trades the code Reddit's sign-in came back with for the sign-in kept here, its one long-lived credential. */
    suspend fun signIn(clientId: String, code: String) {
        prefs.set(RedditPrefs.refreshToken, auth.exchangeCode(clientId, code))
        credentialsChanged()
    }

    /** The sign-in is the refresh token alone: forgetting it signs out, whatever comes after. */
    suspend fun signOut() {
        prefs.set(RedditPrefs.refreshToken, "")
        credentialsChanged()
    }

    /**
     * Whose the sign-in is (XTA's `RedditIdentityStore`), read while the
     * settings are open and kept nowhere; never asked on the account-free
     * route, whose point is that Reddit is not told who reads.
     */
    suspend fun accountName(): String? {
        val settings = prefs.load()
        val asks = settings[RedditPrefs.refreshToken].isNotBlank() && settings[RedditPrefs.clientId].isNotBlank() && settings[RedditPrefs.source] != RedditSource.PUBLIC
        if (!asks) return null
        return attempt { session().userToken?.let { auth.accountName(it) } }.getOrNull()
    }

    /** After the credentials changed: the cached tokens were someone else's. */
    fun credentialsChanged() {
        sessions.forget()
        reading().client.forgetToken()
        launch { reread() }
    }

    private suspend fun reread() {
        changes.update { it + 1 }
        feed.refresh(RedditSubscriptions.names(subscriptions.load()), force = true)
    }

    /** Opens [post]'s thread with all a list knew of it. */
    fun openThread(post: RedditPost) {
        shelf.put(post)
        host.open(RedditRoutes.thread(post.permalink))
    }

    /** The post a thread opens with: as its list had it, else as its link names it. */
    fun postFor(route: RedditRoute.Thread): RedditPost = shelf.get(route.permalink) ?: route.stub()

    fun openSubreddit(name: String) = host.open(RedditRoutes.subreddit(name))

    fun openUser(name: String) = host.open(RedditRoutes.user(name))

    fun openPictures(urls: List<String>, start: Int) = host.openMedia(urls.map { MediaItem(imageUrl = it) }, start)

    /** A video in the app's viewer; the desktop, with no player of its own, opens the post on the web. */
    fun openVideo(post: RedditPost, stream: String) = host.openMedia(
        listOf(
            MediaItem(
                imageUrl = post.cardPreviewImage.orEmpty(),
                kind = MediaItem.Kind.VIDEO,
                videoUrl = stream,
                aspectRatio = RedditMediaRules.aspectRatio(post.videoAspectRatio).toFloat(),
                webUrl = RedditThreadRules.postUrl(post),
            ),
        ),
        0,
    )

}

/** The posts a list opened lately, by permalink, so their thread starts with all the list knew. */
internal class RedditShelf(private val capacity: Int = 64) {
    private val posts = LinkedHashMap<String, RedditPost>()

    @Synchronized
    fun put(post: RedditPost) {
        posts.remove(post.permalink)
        posts[post.permalink] = post
        while (posts.size > capacity) posts.remove(posts.keys.first())
    }

    @Synchronized
    fun get(permalink: String): RedditPost? = posts[permalink]
}
