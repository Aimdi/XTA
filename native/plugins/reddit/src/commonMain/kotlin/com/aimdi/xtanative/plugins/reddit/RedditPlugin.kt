package com.aimdi.xtanative.plugins.reddit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.XtaPlugin

/**
 * Reddit as a downloadable plugin, read-only as XTA's Reddit plugin reads it:
 * account-free by default, with the reader's own client id or sign-in when
 * they want Reddit's better route, read scopes only. Its tab (the followed
 * communities, Popular, All, one community), communities and accounts, the
 * thread view, search and its settings. Nothing is ever posted, voted on,
 * saved or subscribed to on Reddit: following, upvotes and saved posts stay
 * on the device, in the plugin's own folder.
 */
class RedditPlugin : XtaPlugin() {
    private lateinit var app: RedditApp

    override val id: String = "reddit"
    override val brandColor: Color = Color(0xFFFF4500)
    override val icon: ImageVector get() = RedditGlyphs.Mark
    override val category: Category = Category.COMMUNITIES

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = RedditApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> RedditHome(app, frame) }

    override val settings: PluginBody = { frame -> RedditSettingsBody(app, frame) }

    override val search: PluginSearch by lazy { RedditSearch(app) }

    override fun routeFor(url: String): String? = RedditRoutes.forUrl(url)

    override fun screen(route: String): PluginScreen? = when (val opened = RedditRoutes.parse(route)) {
        is RedditRoute.Thread -> screenOf { ThreadScreen(app, opened) }
        is RedditRoute.Subreddit -> screenOf { ListingScreen(app, Listing.Subreddit(opened.name)) }
        is RedditRoute.User -> screenOf { ListingScreen(app, Listing.User(opened.name)) }
        is RedditRoute.Search -> screenOf { ScopedSearchScreen(app, opened) }
        RedditRoute.Saved -> screenOf { SavedScreen(app) }
        RedditRoute.SignIn -> screenOf { SignInScreen(app) }
        null -> null
    }

    private fun screenOf(screen: PluginScreen): PluginScreen = screen

    /** What XTA's backup holds of Reddit: the followed communities and the upvotes, as its rows, and its Reddit preferences without the credentials. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(
            PluginBackupSection("redditSubscriptions", export = { app.subscriptions.export() }, import = { app.restoreSubscriptions(it) }),
            PluginBackupSection("redditLocalVotes", export = { app.votes.export() }, import = { app.votes.import(it) }),
            PluginBackupSection("reddit.settings", export = { app.prefs.export() }, import = { app.prefs.import(it) }),
        )

    override suspend fun footprint(): PluginFootprint =
        PluginFootprint(app.subscriptions.load().size + app.votes.load().size + RedditSaved.posts(app.prefs.load()).size)
}
