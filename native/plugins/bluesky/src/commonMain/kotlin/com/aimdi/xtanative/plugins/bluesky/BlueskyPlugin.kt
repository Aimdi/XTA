package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyXtaRows
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginDiscovery
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFollowable
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Bluesky as a downloadable plugin, read through the public AppView with no
 * account, as XTA's Bluesky plugin reads it: Following (the accounts followed
 * here), custom feeds, lists and the posts liked here on its home tab;
 * threads, profiles with their tabs, people, reposts and quotes; search;
 * imports of follows; its settings; the accounts followed here in Home's
 * timelines when the reader asks, its accounts in the app's groups, and its
 * posts in the app's Saved with their folders and notes. Nothing is posted,
 * liked, reposted or followed on Bluesky: follows and likes stay on the
 * device (in the plugin's folder, and in XTA's backup rows), its preferences
 * in the app's settings.
 */
class BlueskyPlugin : XtaPlugin() {
    private lateinit var app: BlueskyApp

    override val id: String = "bluesky"
    override val brandColor: Color = Color(0xFF0085FF)
    override val icon: ImageVector get() = Butterfly
    override val category: Category = Category.SOCIAL

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = BlueskyApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> BlueskyHome(app, frame, onHome = true) }

    /** Opened on its own, as XTA's Bluesky screen outside Home: the people its feed brings stay open above the posts. */
    override val clientTab: PluginBody = { frame -> BlueskyHome(app, frame, onHome = false) }

    override val settings: PluginBody = { frame -> BlueskySettingsBody(app, frame) }

    override val search: PluginSearch by lazy { BlueskySearch(app) }

    override fun routeFor(url: String): String? = BlueskyRoutes.forUrl(url)

    override fun screen(route: String): PluginScreen? = BlueskyRoutes.parse(route)?.let(::screenOf)

    private fun screenOf(route: BlueskyRoute): PluginScreen = when (route) {
        is BlueskyRoute.Post -> screen { BlueskyThreadScreen(app, route.target) }
        is BlueskyRoute.Profile -> screen { BlueskyProfileScreen(app, route.actor) }
        is BlueskyRoute.People -> screen { PeopleScreen(app, route.actor, route.kind) }
        is BlueskyRoute.Activity -> screen { ActivityScreen(app, route.uri, route.url, route.quotes) }
        is BlueskyRoute.Search -> screen { BlueskySearchScreen(app, route.query, route.author, route.kind) }
        is BlueskyRoute.Import -> screen { ImportScreen(app, route.kind, route.value) }
        BlueskyRoute.Feeds -> screen { FeedCatalogScreen(app) }
        BlueskyRoute.Saved -> screen { BlueskySavedScreen(app) }
    }

    private fun screen(content: PluginScreen): PluginScreen = content

    override val feed: PluginFeed get() = app.feed

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    /** A handle or DID typed into a group's add box, followed here as XTA's box follows it. */
    override fun followable(text: String): PluginFollowable? = app.followable(text)

    override val discovery: PluginDiscovery by lazy { BlueskyDiscovery(app) }

    override val postActions: NetworkPostActions get() = app.actions

    /** Its posts in the app's timelines with Bluesky's own footer and Follow, as XTA's Bluesky card there. */
    override fun postExtras(post: NetworkPost): NetworkPostExtras? = app.extrasFor(post)

    /** A post kept in XTA's record, as Saved draws it; one of another network is none of its. */
    override fun archived(content: JsonElement): NetworkPost? = BlueskyArchive.post(content)?.let { app.shown(it) }

    /**
     * What it keeps, as XTA's backup carries it: the accounts followed here
     * (`blueskySubscriptions`) and the posts liked here (`blueskyLocalLikes`),
     * XTA's own rows, which the app writes where XTA reads them. Its
     * preferences (the liked posts to show among them) travel in the app's
     * settings. An earlier build's own section is still taken back.
     */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(
            PluginBackupSection(BlueskyXtaRows.FOLLOWS, export = { app.follows.exportRows() }, import = { app.follows.importRows(it, Instant.now()) }),
            PluginBackupSection(BlueskyXtaRows.LIKES, export = { app.likes.exportRows() }, import = { app.likes.importRows(it, Instant.now()) }),
            PluginBackupSection(EARLIER_FOLLOWS, export = { null }, import = { app.follows.importOwn(it) }),
        )

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size + app.likes.load().size)

    private companion object {
        /** The section an earlier build of this plugin kept its follows in. */
        const val EARLIER_FOLLOWS = "bluesky.follows"

        /** Bluesky's butterfly, from the Bootstrap Icons glyph XTA draws it with (16 × 16). */
        val Butterfly: ImageVector by lazy {
            val path = "M3.468,1.948C5.303,3.325 7.276,6.118 8,7.616C8.725,6.118 10.698,3.326 12.532,1.948" +
                "C13.855,0.955 16,0.186 16,2.632C16,3.121 15.72,6.737 15.556,7.324C14.984,9.364 12.903,9.885 11.052,9.57" +
                "C14.288,10.121 15.112,11.945 13.333,13.77C9.957,17.234 8.481,12.9 8.103,11.79C8.033,11.586 8,11.49 8,11.572" +
                "C8,11.491 7.967,11.586 7.898,11.79C7.519,12.9 6.043,17.234 2.667,13.77C0.889,11.945 1.712,10.12 4.947,9.57" +
                "C3.097,9.885 1.015,9.365 0.444,7.324C0.28,6.737 0,3.12 0,2.632C0,0.186 2.145,0.955 3.468,1.948Z"
            ImageVector.Builder(name = "Bluesky", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 16f, viewportHeight = 16f)
                .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
                .build()
        }
    }
}
