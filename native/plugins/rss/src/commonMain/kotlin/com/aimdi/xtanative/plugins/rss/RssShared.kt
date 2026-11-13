package com.aimdi.xtanative.plugins.rss

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.Rss
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.FeedReads
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.TextRun
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * RSS in the app's timelines, as XTA's loadRssInterleaved mixes it: the
 * newest items of the followed feeds (eight of each) in Home's Following and
 * For you while its switch is on, and of the feeds a group holds. Each is
 * drawn as RSS's own article row with its read mark ([card]), and is a link
 * post, filed in Saved as XTA's link record; a tap opens the reader, a long
 * press the shared post menu, whose Add to group takes the feed.
 */
internal class RssShared(private val app: RssApp) : PluginFeed() {
    private val drawn = DrawnPosts<RssItem> { idOf(it) }

    override suspend fun followed(): List<String> = app.loadedFeeds().map { it.id }

    /** One feed that cannot be read leaves the others' items; only when none answers does this fail. */
    override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
        val wanted = app.loadedFeeds().filter { it.id in accounts }
        return FeedReads.each(wanted) { feed -> app.client.items(feed).items.take(PER_FEED) }.filter { it.publishedAt != null }.map(::shown)
    }

    /** [item] as the app's timelines hold it, and as the shared row draws it where [card] has no row for it: its feed over its title and excerpt, its picture. */
    fun shown(item: RssItem): NetworkPost {
        drawn.put(item)
        return NetworkPost(
            id = idOf(item),
            authorId = item.feedId,
            authorName = item.feedTitle,
            authorHandle = hostOf(app.feedFor(item.feedId)) ?: item.feedTitle,
            createdAt = item.publishedAt,
            title = item.title,
            text = listOfNotNull(item.excerpt?.takeIf { it.isNotBlank() }?.let(TextRun::Plain)),
            media = listOfNotNull(item.imageUrl?.let { MediaItem(it) }),
            url = item.link,
        )
    }

    /** The article row of the item [post] names, as XTA's timelines draw it ([SharedItemCard]); none for one not drawn lately. */
    fun card(post: NetworkPost): (@Composable () -> Unit)? {
        val item = drawn[post.id] ?: return null
        return { SharedItemCard(app, item) }
    }

    /** Where an item the app draws leads: the reader, its feed, its picture, its bookmark and the shared menu. */
    val actions = NetworkPostActions(
        open = { post -> drawn[post.id]?.let(app::openItem) ?: post.url?.let(app.host::openLink) },
        openAuthor = { post -> app.feedFor(post.authorId)?.let(app::openFeed) },
        openMention = {},
        openTag = {},
        openMedia = { post, index -> app.host.openMedia(post.media, index) },
        archive = { post -> drawn[post.id]?.let(::archive) },
        more = { post -> drawn[post.id]?.let { app.host.openPostMenu(menu(it)) } },
    )

    /** XTA's link record of [item] (rss_interleaved.dart's snapshot): its link, its author or feed, its title over its excerpt. */
    fun archive(item: RssItem): PluginPostArchive =
        PluginPostArchive.link(Rss.NETWORK, item.link.orEmpty(), item.author ?: item.feedTitle, item.title + "\n" + item.excerpt.orEmpty())

    /** The shared post menu for [item]: its feed is the account Add to group takes, followed first when it is not. */
    fun menu(item: RssItem): PluginPostMenu =
        PluginPostMenu(archive(item), item.link.orEmpty(), author = item.feedId) { app.followIfNeeded(app.feedOf(item)) }


    private fun hostOf(feed: RssFeed?): String? = (feed?.siteUrl ?: feed?.feedUrl)?.toHttpUrlOrNull()?.host?.removePrefix("www.")

    private companion object {
        /** What the app's timelines know [item] by: XTA's interleaved item id. */
        fun idOf(item: RssItem): String = "${Rss.NETWORK}:${item.feedId}:${item.id}"

        /** How many items of each feed join a timeline, as XTA's kRssInterleavedPageSize. */
        const val PER_FEED = 8
    }
}
