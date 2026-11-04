package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.plugins.PluginDiscovery
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bluesky's part of a group's Discover (group_discovery_sources.dart's
 * _BlueskyScan): the AppView's own "similar accounts" for a member; when it
 * has none to give, what the member reposted and quoted. Following one stays
 * here, as every follow does: Bluesky is never told.
 */
internal class BlueskyDiscovery(private val app: BlueskyApp) : PluginDiscovery() {
    override suspend fun around(account: String): List<PluginDiscovered> {
        val suggested = try {
            app.client.suggestedFollowsByActor(account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (suggested.isNotEmpty()) return suggested.map(::suggestion)
        return app.reader.posts(listOf(account)).filterNot { it.isUnavailable }.flatMap(::shared)
    }

    private fun suggestion(profile: BlueskyProfile) = PluginDiscovered(
        id = profile.actor,
        handle = profile.handle,
        name = profile.displayName,
        signal = DiscoverySignal.SUGGESTED,
        avatarUrl = profile.avatarUrl,
        bio = profile.description.ifEmpty { null },
        route = BlueskyRoutes.profile(profile.actor),
        follow = { app.ensureFollowed(profile) },
    )

    /** A member's repost or quote: who wrote what was shared, the shared post showing them (blueskyDiscoveryAccounts). */
    private fun shared(post: BlueskyPost): List<PluginDiscovered> = listOfNotNull(
        post.takeIf { it.isRepost }?.let { found(it, DiscoverySignal.REPOSTED, it.repostedAt ?: it.publishedAt) },
        post.quotedPost?.let { found(it, DiscoverySignal.QUOTED, post.publishedAt) },
    )

    private fun found(shared: BlueskyPost, signal: DiscoverySignal, at: Instant?): PluginDiscovered {
        val author = shared.author()
        return PluginDiscovered(
            id = author.actor,
            handle = shared.handle,
            name = shared.authorName,
            signal = signal,
            avatarUrl = shared.avatarUrl,
            text = shared.text,
            postUrl = shared.url,
            postedAt = shared.publishedAt,
            pointedAt = at,
            post = app.shown(shared),
            route = BlueskyRoutes.profile(author.actor),
            follow = { app.ensureFollowed(author) },
        )
    }
}
