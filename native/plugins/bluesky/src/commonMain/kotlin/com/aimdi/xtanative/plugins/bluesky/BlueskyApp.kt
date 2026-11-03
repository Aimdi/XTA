package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyClient
import com.aimdi.xtanative.networks.bluesky.BlueskyFollowing
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What the Bluesky plugin's screens share while it runs: the public AppView's
 * client, the accounts followed here, its settings, and the Following
 * timeline's readers (one per replies setting, each keeping its pages).
 */
internal class BlueskyApp(val host: PluginHost) {
    val client = BlueskyClient(host.http)
    val follows = BlueskyFollows(host.store.file("follows.json"))
    val settings = BlueskySettingsStore(host.store.file("settings.json"))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val withReplies = BlueskyFollowing(client)
    private val withoutReplies = BlueskyFollowing(client, filter = Bluesky.AUTHOR_FEED_NO_REPLIES)

    /** The Following timeline's reader, with the replies the settings ask for. */
    fun following(replies: Boolean): BlueskyFollowing = if (replies) withReplies else withoutReplies

    /** Runs [block] beyond the screen that asked for it: a follow, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun close() = scope.cancel()

    /** Where a post's parts lead: the plugin's own post and profile screens, its search, the app's media viewer. */
    val actions = NetworkPostActions(
        open = { post -> host.open(BlueskyRoutes.post(post.id)) },
        openAuthor = { post -> host.open(BlueskyRoutes.profile(post.authorId)) },
        openMention = { who -> host.open(BlueskyRoutes.profile(who)) },
        openTag = { query -> host.openSearch(query) },
        openMedia = { post, index -> host.openMedia(post.media, index) },
    )

    /** The network's name where a problem names it. */
    val network: String get() = NETWORK

    private companion object {
        const val NETWORK = "Bluesky"
    }
}

/** [this] without the posts that may not be shown at all: hidden by moderation, or by their authors from readers without an account. */
internal fun Page<BlueskyPost>.shown(): Page<BlueskyPost> = Page(items.filterNot { it.isUnavailable }, next)
