package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.ui.plugins.PluginText
import java.time.Instant

/**
 * How what is loaded of a post's quotes and boosters is ordered on the device,
 * as XTA's pluginQuoteSort and pluginReposterSort order them: Mastodon ranks
 * neither list.
 */
internal object MastodonOrders {
    val quoteOrders = listOf(MastodonOrder.RECENT, MastodonOrder.OLDEST, MastodonOrder.MOST_LIKED)
    val reposterOrders = listOf(MastodonOrder.RECENT, MastodonOrder.MOST_FOLLOWERS)

    /** Quotes newest first, oldest first, or most liked first; undated ones last. */
    fun quotes(posts: List<MastodonPost>, order: MastodonOrder): List<MastodonPost> = when (order) {
        MastodonOrder.OLDEST -> posts.sortedWith(compareBy<MastodonPost> { it.publishedAt == null }.thenBy { it.publishedAt ?: Instant.MAX })
        MastodonOrder.MOST_LIKED -> posts.sortedByDescending { it.favouritesCount }
        else -> posts.sortedWith(compareBy<MastodonPost> { it.publishedAt == null }.thenByDescending { it.publishedAt ?: Instant.MIN })
    }

    /** Boosters as the instance sent them (the most recent first), or by their followers. */
    fun reposters(people: List<MastodonProfile>, order: MastodonOrder): List<MastodonProfile> =
        if (order == MastodonOrder.MOST_FOLLOWERS) people.sortedByDescending { it.followersCount } else people

    fun label(order: MastodonOrder): PluginText = when (order) {
        MastodonOrder.RECENT -> Strings.sort_recent
        MastodonOrder.OLDEST -> Strings.sort_oldest
        MastodonOrder.MOST_LIKED -> Strings.sort_most_liked
        MastodonOrder.MOST_FOLLOWERS -> Strings.sort_most_followers
    }
}
