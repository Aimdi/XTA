package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonFollow
import com.aimdi.xtanative.networks.mastodon.MastodonInstances
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.plugins.PluginDiscovery
import java.time.Instant

/**
 * Mastodon's part of a group's Discover (group_discovery_sources.dart's
 * mastodonDiscoveryReader): what a member boosted and quoted, read from their
 * latest posts where they live. The answers depend on the reader's own
 * instances, which are asked too. Following one stays here.
 */
internal class MastodonDiscovery(private val app: MastodonApp) : PluginDiscovery() {
    override val scope: String get() = app.configured.joinToString(",")

    override suspend fun around(account: String): List<PluginDiscovered> =
        app.client.accountPostsAnywhere(MastodonInstances.feedCandidates(account, app.configured), account, POSTS).flatMap(::shared)

    /** A boost or a quote by the member: who wrote what was shared, with it showing them (mastodonDiscoveryAccounts). */
    private fun shared(post: MastodonPost): List<PluginDiscovered> = listOfNotNull(
        post.takeIf { it.boosted }?.let { found(it, DiscoverySignal.REPOSTED, it.timelineDate) },
        post.quote?.let { found(it, DiscoverySignal.QUOTED, post.publishedAt) },
    )

    private fun found(shared: MastodonPost, signal: DiscoverySignal, at: Instant?) = PluginDiscovered(
        id = shared.acct,
        handle = shared.acct,
        name = shared.authorName,
        signal = signal,
        avatarUrl = shared.avatarUrl,
        text = shared.text,
        postUrl = shared.url,
        postedAt = shared.publishedAt,
        pointedAt = at,
        post = app.shown(shared),
        route = MastodonRoutes.profile(shared.acct),
        follow = { app.ensureFollowed(MastodonFollow(shared.acct, shared.authorName, shared.avatarUrl)) },
    )

    private companion object {
        /** One page of a member's posts, as XTA reads it for Discover. */
        const val POSTS = 20
    }
}
