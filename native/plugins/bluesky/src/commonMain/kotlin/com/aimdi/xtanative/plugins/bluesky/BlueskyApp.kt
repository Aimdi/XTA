package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyClient
import com.aimdi.xtanative.networks.bluesky.BlueskyFollow
import com.aimdi.xtanative.networks.bluesky.BlueskyFollowing
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyQuoteSort
import com.aimdi.xtanative.networks.bluesky.BlueskyReplySort
import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
import com.aimdi.xtanative.networks.bluesky.BlueskyXtaRows
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * What the Bluesky plugin's screens share while it runs, as XTA's providers
 * share them: the AppView's client, the accounts followed and the posts liked
 * here, its preferences (in the app's settings), the Following timeline, the
 * custom feeds and lists, the orders a reader picked for this session, and
 * the posts the app drew lately, which their bookmark and their menu file in
 * the app's archive.
 */
internal class BlueskyApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = BlueskyPreferences(host.settings, scope)
    val client = BlueskyClient(host.http) { prefs.appView.value }
    val follows = BlueskyFollows(host.store.file("follows.json"))
    val likes = BlueskyLikes(host.store.file("likes.json"), prefs)
    val reader = BlueskyFollowing(client)
    val following = FollowingTimeline(this, scope)
    val feeds = BlueskyFeeds(this, scope)
    val lists = BlueskyLists(this, scope)
    private val shelf = BlueskyShelf()

    /** The reply order of every thread, and the quotes' order, for the rest of the session (XTA's ConversationSortStore). */
    val replySort = MutableStateFlow(BlueskyReplySort.RELEVANT)
    val quoteSort = MutableStateFlow(BlueskyQuoteSort.RECENT)

    init {
        launch {
            prefs.adopt(host.store.file("settings.json"))
            follows.load()
            likes.load()
        }
    }

    /** Runs [block] beyond the screen that asked for it: a follow, a like, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun close() = scope.cancel()

    /** Follows [profile] here, or unfollows it when it is followed. */
    fun toggleFollow(profile: BlueskyProfile) = launch {
        if (followed(profile)) unfollowNow(profile.did, profile.handle) else follows.follow(profile, now())
    }

    fun unfollow(actor: String) = launch { unfollowNow(actor) }

    /**
     * The account [names] name (its DID, its handle) no longer followed here,
     * and out of the app's groups by each name it has there, as XTA's
     * BlueskyAccountsStore.remove takes it out of its groups.
     */
    private suspend fun unfollowNow(vararg names: String) {
        val known = names.filter { it.isNotEmpty() }
        val gone = follows.load().filter { follow -> known.any(follow::isOf) }
        known.forEach { follows.unfollow(it) }
        gone.flatMap { listOf(it.did, it.handle) }.filter { it.isNotEmpty() }.distinct().forEach(host::unfollowed)
    }

    /** The accounts followed here as the app's Subscriptions list shows them (XTA's BlueskySubscription): a tap opens the profile. */
    val subscriptions: Flow<List<PluginSubscription>> = follows.state.onStart { follows.load() }.filterNotNull().map { list -> list.map(::subscriptionOf) }

    private fun subscriptionOf(follow: BlueskyFollow) = PluginSubscription(
        id = follow.actor,
        name = follow.name,
        handle = follow.shownHandle,
        avatarUrl = follow.avatarUrl,
        route = BlueskyRoutes.profile(follow.actor),
        followedAt = follow.followedAt,
        backupId = BlueskyXtaRows.idOf(follow),
        unfollow = { unfollowNow(follow.actor) },
    )

    /** [profile] followed here unless it is, as putting an account in a group follows it first (XTA's ensureFollowed). */
    suspend fun ensureFollowed(profile: BlueskyProfile) {
        if (!followed(profile)) follows.follow(profile, now())
    }

    private fun followed(profile: BlueskyProfile) = follows.follows(profile.did) || follows.follows(profile.handle)

    fun toggleLike(post: BlueskyPost) = launch { likes.toggle(post, now()) }

    fun openPost(uri: String) = host.open(BlueskyRoutes.post(uri))

    fun openProfile(actor: String) = host.open(BlueskyRoutes.profile(actor))

    fun openSearch(query: String = "", author: String = "", kind: BlueskySearchKind = BlueskySearchKind.PEOPLE) =
        host.open(BlueskyRoutes.search(query, author, kind))

    /** [post] as the app draws it, kept so that its bookmark and its menu find the post again. */
    fun shown(post: BlueskyPost, language: String = host.language): NetworkPost {
        shelf.put(post)
        return post.toNetworkPost(language)
    }

    /**
     * Where a post's parts lead: the plugin's own post and profile screens,
     * its search for a tag, the app's media viewer; where the app draws it,
     * its bookmark and its menu.
     */
    val actions = NetworkPostActions(
        open = { post -> openPost(post.id) },
        openAuthor = { post -> openProfile(post.authorId) },
        openMention = ::openProfile,
        openTag = { query -> openSearch(query, kind = BlueskySearchKind.POSTS) },
        openMedia = { post: NetworkPost, index -> host.openMedia(post.media, index) },
        archive = { shown -> shelf.get(shown.id)?.let(BlueskyArchive::of) },
        more = { shown -> shelf.get(shown.id)?.let(::openMenu) },
    )

    /** XTA's post sheet for [post] (showBlueskyPostActions): Saved, its note, its author's groups, its link, who reposted and quoted it. */
    fun openMenu(post: BlueskyPost) = host.openPostMenu(
        PluginPostMenu(
            BlueskyArchive.of(post),
            post.url,
            author = post.did.ifEmpty { post.handle },
            follow = { ensureFollowed(post.author()) },
            reposts = { host.open(BlueskyRoutes.activity(post.uri, post.url, quotes = false)) },
            quotes = { host.open(BlueskyRoutes.activity(post.uri, post.url, quotes = true)) },
        ),
    )

    /** The accounts followed here in Home's timelines and the groups' (XTA's loadBlueskyInterleaved). */
    val feed: PluginFeed = HomeFeed()

    private inner class HomeFeed : PluginFeed() {
        override suspend fun followed(): List<String> = follows.load().map { it.actor }

        /** Through the Following timeline's pages, the first [SHARED] it takes that have a date to be placed by. */
        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> =
            reader.posts(accounts, force)
                .filter { kinds.takes(it.isReply, it.isRepost) && !it.isUnavailable && it.publishedAt != null }
                .take(SHARED)
                .map { shown(it) }
    }

    /** The network's name where a problem names it. */
    val network: String get() = NETWORK

    private companion object {
        const val NETWORK = "Bluesky"

        /** XTA's kBlueskyInterleavedPageSize. */
        const val SHARED = 10

        fun now(): Long = System.currentTimeMillis()
    }
}

/** The posts the app drew lately, by URI, so their bookmark and their menu can file the post itself in the archive. */
internal class BlueskyShelf(private val capacity: Int = 400) {
    private val posts = LinkedHashMap<String, BlueskyPost>()

    @Synchronized
    fun put(post: BlueskyPost) {
        posts.remove(post.uri)
        posts[post.uri] = post
        while (posts.size > capacity) posts.remove(posts.keys.first())
    }

    @Synchronized
    fun get(uri: String): BlueskyPost? = posts[uri]
}
