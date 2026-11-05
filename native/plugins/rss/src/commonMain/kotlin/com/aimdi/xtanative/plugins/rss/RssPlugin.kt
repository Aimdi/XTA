package com.aimdi.xtanative.plugins.rss

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.rss.Rss
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.ui.plugins.AddToGroupButton
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFollowable
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.RssOffline
import com.aimdi.xtanative.ui.plugins.RssReaderScreen
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import kotlinx.coroutines.flow.Flow

/**
 * RSS as a downloadable plugin, as XTA's RSS plugin reads it: public RSS and
 * Atom feeds, found from a site's address, followed here; their items
 * newest first, filtered by unread and by tag; each feed on its own; the
 * reader, with articles kept offline; and the follows to and from OPML. No
 * login, and nothing sent anywhere: follows, read marks and tags stay on
 * the device, in the app's settings under XTA's keys.
 */
class RssPlugin : XtaPlugin() {
    private lateinit var app: RssApp

    override val id: String = "rss"
    override val brandColor: Color = Color(0xFFEE802F)
    override val icon: ImageVector get() = RssIcons.Feed
    override val category: Category = Category.NEWSLETTERS

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = RssApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> RssHome(app, frame) }

    override val settings: PluginBody = { frame -> RssSettingsBody(app, frame) }

    override val feed: PluginFeed get() = app.shared

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    /** A feed's or a site's address pasted into a group's add box, followed here as XTA's box follows it. */
    override fun followable(text: String): PluginFollowable? = app.followable(text)

    override val postActions: NetworkPostActions get() = app.shared.actions

    /** Its items in the app's timelines as RSS's own article rows, read or not, as XTA's timelines draw them. */
    override fun postCard(post: NetworkPost): (@Composable () -> Unit)? = app.shared.card(post)

    /** A feed in a group, opened on its own page, as XTA opens an RSS member. */
    override fun routeForAccount(account: String): String = RssRoutes.feed(app.feedFor(account) ?: RssFeed(account, account, ""))

    /** An article kept offline from one of its feeds (their ids are their addresses), in its reader, as XTA's offline library opens it. */
    override fun routeForOffline(article: OfflineArticle): String? = RssOffline.itemOf(article)?.takeIf { Rss.isFeedId(it.feedId) }?.let(RssRoutes::item)

    override fun screen(route: String): PluginScreen? = when (val opened = RssRoutes.parse(route)) {
        is RssRoute.Item -> readerScreen(opened)
        is RssRoute.Feed -> feedScreen(opened)
        RssRoute.Add -> addScreen()
        null -> null
    }

    private fun readerScreen(route: RssRoute.Item): PluginScreen = {
        val item = app.itemFor(route.id) ?: route.stub
        val feed = app.feedOf(item)
        RssReaderScreen(app.host, item, read = item.id in app.read.value, scope = app.scope, actions = { AddToGroupButton(app.host, feed.id) { app.followIfNeeded(feed) } }) {
            app.markRead(listOf(item.id))
        }
    }

    private fun feedScreen(route: RssRoute.Feed): PluginScreen = { RssFeedScreen(app, app.feedFor(route.id) ?: route.stub) }

    private fun addScreen(): PluginScreen = { RssAddScreen(app) }

    /** The follows as XTA's backup holds them: its `rssSubscriptions` rows. The rest of what it keeps is in the app's settings. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection(RssBackup.SECTION, export = { RssBackup.export(app.host.settings) }, import = { RssBackup.import(app.host.settings, it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.loadedFeeds().size)
}
