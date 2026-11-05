package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.plugins.PluginDiscovery

/**
 * Pixiv's part of a group's Discover (group_discovery_sources.dart's
 * pixivDiscoveryReader): the creators Pixiv relates to a member, a few
 * members at a time, without those the account follows on Pixiv or the
 * reader muted, each shown through their first work the reader would see.
 * The answers are the signed-in account's, under the reader's R-18, AI and
 * mute choices: a change asks again. Adding one follows it on this device
 * only, never on Pixiv.
 */
internal class PixivDiscover(private val runtime: PixivRuntime) : PluginDiscovery() {
    override val perScan: Int get() = 4

    override val scope: String
        get() = listOf(runtime.client.storedUserId, runtime.client.showR18, runtime.client.hideAi, PixivMutes.of(runtime.settings.now).hashCode()).joinToString("|")

    override suspend fun around(account: String): List<PluginDiscovered> {
        val seed = account.toLongOrNull()?.takeIf { it > 0 } ?: return emptyList()
        val mutes = PixivMutes.of(runtime.settings.load())
        return runtime.client.relatedUsers(seed)
            .filter { !it.user.isFollowed && it.user.id != seed && it.user.id !in mutes.authorIds }
            .map { found(it, mutes.filter(it.illusts).firstOrNull()) }
    }

    private fun found(preview: PixivUserPreview, work: PixivIllust?): PluginDiscovered {
        val user = preview.user
        return PluginDiscovered(
            id = "${user.id}",
            handle = user.account.ifEmpty { "${user.id}" },
            name = user.name,
            signal = DiscoverySignal.RELATED,
            avatarUrl = user.avatarUrl,
            bio = user.comment.ifEmpty { null },
            text = work?.let { listOf(it.title, it.tags.joinToString(" ") { tag -> tag.displayName }).joinToString(" ").trim() }.orEmpty(),
            postUrl = work?.url,
            postedAt = work?.createdAt,
            post = work?.let(runtime::shown),
            route = PixivRoutes.of(PixivRoute.User(user.id)),
            follow = { if (!runtime.isFollowedHere(user.id)) runtime.followHere(user) },
        )
    }
}
