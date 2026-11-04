package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyClient
import com.aimdi.xtanative.networks.bluesky.BlueskyFollowing
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyQuoteSort
import com.aimdi.xtanative.networks.bluesky.BlueskyReplySort
import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * What the Bluesky plugin's screens share while it runs, as XTA's providers
 * share them: the AppView's client, the accounts followed and the posts liked
 * here, its preferences (in the app's settings), the Following timeline, the
 * custom feeds and lists, and the orders a reader picked for this session.
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
        if (follows.follows(profile.did) || follows.follows(profile.handle)) follows.unfollow(profile.actor) else follows.follow(profile, now())
    }

    fun toggleLike(post: BlueskyPost) = launch { likes.toggle(post, now()) }

    fun openPost(uri: String) = host.open(BlueskyRoutes.post(uri))

    fun openProfile(actor: String) = host.open(BlueskyRoutes.profile(actor))

    fun openSearch(query: String = "", author: String = "", kind: BlueskySearchKind = BlueskySearchKind.PEOPLE) =
        host.open(BlueskyRoutes.search(query, author, kind))

    /** Where a post's parts lead: the plugin's own post and profile screens, its search for a tag, the app's media viewer. */
    val actions = NetworkPostActions(
        open = { post -> openPost(post.id) },
        openAuthor = { post -> openProfile(post.authorId) },
        openMention = ::openProfile,
        openTag = { query -> openSearch(query, kind = BlueskySearchKind.POSTS) },
        openMedia = { post: NetworkPost, index -> host.openMedia(post.media, index) },
    )

    /** The network's name where a problem names it. */
    val network: String get() = NETWORK

    private companion object {
        const val NETWORK = "Bluesky"

        fun now(): Long = System.currentTimeMillis()
    }
}
