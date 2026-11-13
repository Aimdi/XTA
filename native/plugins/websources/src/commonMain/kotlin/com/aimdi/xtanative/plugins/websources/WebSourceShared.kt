package com.aimdi.xtanative.plugins.websources

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceItem
import com.aimdi.xtanative.networks.websources.WebSources
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.FeedReads
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Web sources in the app's timelines, as XTA's loadWebSourceInterleaved
 * mixes them: the newest posts of the followed lists (eight of each) in
 * Home's Following and For you while its switch is on, and of the lists a
 * group holds (`websources:lobsters/hottest`). Each is drawn as its source's
 * layout draws it, naming the source ([card]), and is a link post, filed in
 * Saved as XTA's link record (plugin_link_post.dart); a long press opens the
 * shared post menu, here and on the source's own lists.
 */
internal class WebSourceShared(private val app: WebSourcesApp) : PluginFeed() {
    private val drawn = object : LinkedHashMap<String, Pair<WebSourceDefinition, WebSourceItem>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<WebSourceDefinition, WebSourceItem>>?) = size > KEPT
    }

    override suspend fun followed(): List<String> = app.follows.filterNotNull().first().map { it.key }

    /** One list that cannot be read leaves the others' posts; only when none answers does this fail. */
    override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
        val installed = app.installed.filterNotNull().first()
        val lists = app.follows.filterNotNull().first().filter { it.key in accounts }.mapNotNull { follow ->
            val source = installed.firstOrNull { it.source.id == follow.sourceId }?.source
            source?.list(follow.listId)?.let { list -> Triple(source, list, follow.key) }
        }
        return FeedReads.each(lists) { (source, list, key) ->
            app.client.list(source, list).items.take(PER_LIST).filter { it.date != null }.map { shown(source, it, key) }
        }
    }

    /** [item] as the shared row draws it: who wrote it, or its source, over its title, text and pictures. */
    private fun shown(source: WebSourceDefinition, item: WebSourceItem, list: String): NetworkPost {
        val id = "${WebSources.NETWORK}:${item.key}"
        synchronized(drawn) { drawn[id] = source to item }
        return NetworkPost(
            id = id,
            authorId = list,
            authorName = item.author ?: source.name,
            authorHandle = source.homepage.host.removePrefix("www."),
            createdAt = item.date,
            title = item.title,
            text = listOfNotNull(item.text?.let(TextRun::Plain)),
            media = item.pictures.map { MediaItem(it) },
            url = urlOf(source, item),
        )
    }

    /** The card of the post [post] names, as XTA's timelines draw it ([SharedItemCard]); none for one not drawn lately. */
    fun card(post: NetworkPost): (@Composable () -> Unit)? {
        val (source, item) = entry(post) ?: return null
        return { SharedItemCard(app, source, item) }
    }

    /**
     * Where a post the app draws leads, as XTA's card in Home opens it: the
     * reader (with the text its source's article rule names), else its link;
     * its list, its pictures, its bookmark and the shared menu.
     */
    val actions = NetworkPostActions(
        open = { post -> entry(post)?.let { (source, item) -> app.openItem(source, item) } ?: post.url?.let(app.host::openLink) },
        openAuthor = { post -> entry(post)?.let { (source, _) -> app.openSource(source, post.authorId.substringAfter('/', "").ifEmpty { null }) } },
        openMention = {},
        openTag = {},
        openMedia = { post, index -> app.host.openMedia(post.media, index) },
        archive = { post -> entry(post)?.let { (source, item) -> archive(source, item) } },
        more = { post -> entry(post)?.let { (source, item) -> app.host.openPostMenu(menu(source, item)) } },
    )

    /** XTA's link record of [item]: its link (else its discussion's, else its site's), who wrote it, its title over its text, its pictures. */
    fun archive(source: WebSourceDefinition, item: WebSourceItem): PluginPostArchive =
        PluginPostArchive.link(WebSources.NETWORK, urlOf(source, item), item.author ?: source.name, listOfNotNull(item.title, item.text).joinToString("\n"), item.pictures)

    /** The shared post menu for [item] (showWebSourceItemActions): save it, its note, its link shared or opened. */
    fun menu(source: WebSourceDefinition, item: WebSourceItem): PluginPostMenu = PluginPostMenu(archive(source, item), urlOf(source, item))

    private fun entry(post: NetworkPost): Pair<WebSourceDefinition, WebSourceItem>? = synchronized(drawn) { drawn[post.id] }

    private fun urlOf(source: WebSourceDefinition, item: WebSourceItem): String = item.link ?: item.discussion ?: source.homepage.toString()


    private companion object {
        /** How many posts of each followed list join a timeline, as XTA's kWebSourceInterleavedPageSize. */
        const val PER_LIST = 8
        const val KEPT = 256
    }
}
