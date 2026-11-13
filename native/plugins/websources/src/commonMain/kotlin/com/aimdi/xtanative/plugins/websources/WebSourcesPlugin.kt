package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceFollows
import com.aimdi.xtanative.networks.websources.WebSourceReading
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.RssOffline
import com.aimdi.xtanative.ui.plugins.RssReaderScreen
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.flow.Flow

/**
 * Web sources as a downloadable plugin, as XTA's web sources read them:
 * simple sites added from the list this repository publishes or by a source
 * file's link, each read with its file's rules over HTTPS from the sites
 * the reader agreed to, and nothing else. A source's lists, its search, its
 * posts in the RSS reader, and follows kept on the device, in the app's
 * settings under XTA's keys, so XTA's backup restores them. Nothing is
 * posted anywhere.
 */
class WebSourcesPlugin : XtaPlugin() {
    private lateinit var app: WebSourcesApp

    override val id: String = "websources"
    override val brandColor: Color = Color(0xFF0E7C86)
    override val icon: ImageVector get() = WebSourcesIcons.TravelExplore
    override val category: Category = Category.SITES

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = WebSourcesApp(host)
        // Off the start's path: at most once a day, each installed source's file is read again for an update.
        app.checkForUpdates()
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> WebSourcesHome(app, frame) }

    /** XTA's web sources settings: the followed lists' posts in Home's timeline. */
    override val settings: PluginBody = { frame -> LazyColumn(contentPadding = frame.padding) { item { HomeTimelineSwitch(app.host.settings, pluginString(Strings.in_home_feed), pluginString(Strings.in_home_feed_description)) } } }

    override val feed: PluginFeed get() = app.shared

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    override val postActions: NetworkPostActions get() = app.shared.actions

    /** Its posts in the app's timelines as their source's layout draws them, as XTA's timelines draw them. */
    override fun postCard(post: NetworkPost): (@Composable () -> Unit)? = app.shared.card(post)

    /** A followed list in a group (`lobsters/hottest`), opened on its source's screen at that list, as XTA opens it. */
    override fun routeForAccount(account: String): String? = WebSourceFollows.parts(account)?.let { (source, list) -> WebSourceRoutes.source(source, list) }

    /** A post of one of its sources kept offline (`websources:<source>` is the feed it reads in), in the RSS reader, as XTA's offline library opens it. */
    override fun routeForOffline(article: OfflineArticle): String? {
        val item = RssOffline.itemOf(article) ?: return null
        return WebSourceReading.sourceOf(item.feedId)?.let { source -> WebSourceRoutes.reader(source, item) }
    }

    override fun screen(route: String): PluginScreen? = when (val opened = WebSourceRoutes.parse(route)) {
        is WebSourceRoute.Source -> sourceScreen(opened)
        is WebSourceRoute.Search -> searchScreen(opened)
        is WebSourceRoute.Reader -> readerScreen(opened)
        WebSourceRoute.Add -> addScreen()
        null -> null
    }

    private fun sourceScreen(route: WebSourceRoute.Source): PluginScreen = { Installed(route.sourceId) { WebSourceScreen(app, it, route.listId) } }

    private fun searchScreen(route: WebSourceRoute.Search): PluginScreen = { Installed(route.sourceId) { WebSourceSearchScreen(app, it) } }

    /** [content] for the source [id] once the settings are read, or a word that it is gone. */
    @Composable
    private fun Installed(id: String, content: @Composable (WebSourceDefinition) -> Unit) {
        val installed by app.installed.collectAsState()
        val source = installed?.firstOrNull { it.source.id == id }?.source
        when {
            installed == null -> LoadingRow()
            source == null -> Gone()
            else -> content(source)
        }
    }

    /** A post in the RSS reader, finished there as it was before and marked read once read to its end, as XTA's reader marks it. */
    private fun readerScreen(route: WebSourceRoute.Reader): PluginScreen = {
        val item = app.openedItem(route.key) ?: route.stub(app.source(route.sourceId)?.source)
        RssReaderScreen(app.host, item, read = item.id in app.read.value, scope = app.scope) { app.markRead(item.id) }
    }

    private fun addScreen(): PluginScreen = { WebSourceAddScreen(app) }

    /** A source removed since its screen was opened: nothing to read there. */
    @Composable
    private fun Gone() {
        PluginScaffold(title = pluginString(Strings.plugin_name), onBack = app.host::back) { padding ->
            MessageText(pluginString(Strings.list_empty), Modifier.fillMaxSize().padding(padding))
        }
    }

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.installed.value.orEmpty().size + app.follows.value.orEmpty().size)
}
