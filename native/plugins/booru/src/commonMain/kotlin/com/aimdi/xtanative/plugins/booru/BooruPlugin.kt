package com.aimdi.xtanative.plugins.booru

import androidx.compose.runtime.Composable
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
 * Booru as a downloadable plugin (XTA's booru_plugin.dart): Danbooru and the
 * boorus that speak Moebooru's, Gelbooru 0.2's and e621's APIs, read as a
 * gallery: Latest, Popular and the tags followed here, the tag search with
 * the host's suggestions, posts with their tags, notes, pools and comments,
 * and the settings with each site's own rating cap, blacklist and login.
 * Read-only: nothing is voted on, favourited, commented on or tagged at any
 * booru. Followed tags and saved searches stay in the plugin's own folder;
 * a post saved here goes to the app's saved posts.
 */
class BooruPlugin : XtaPlugin() {
    private lateinit var app: BooruApp

    override val id: String = "booru"
    override val brandColor: Color = Color(0xFF7E57C2)
    override val icon: ImageVector get() = BooruIcons.Mark
    override val category: Category = Category.ART

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = BooruApp(host).also { it.start() }
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> BooruHome(app, frame) }

    /** Opened on its own, the app's bar has the search and the settings, so the tab's row leaves them out. */
    override val clientTab: PluginBody = { frame -> BooruHome(app, frame, ownActions = false) }

    override val settings: PluginBody = { frame -> BooruSettingsBody(app, frame) }

    override val search: PluginSearch by lazy { BooruFieldSearch(app) }

    override fun routeFor(url: String): String? = BooruRoutes.forUrl(url, BooruSites.known(app.current()))

    override fun screen(route: String): PluginScreen? = BooruRoutes.parse(route)?.let(::screenOf)

    private fun screenOf(route: BooruRoute): PluginScreen = { BooruRouteScreen(app, route) }

    /** The tags followed and the searches saved here, under the name and in the rows XTA's backup gives them. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection(BACKUP_SECTION, export = { app.follows.export() }, import = { app.follows.import(it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size)

    private companion object {
        /** XTA's own key for these rows (plugin_backup.dart): load-bearing, as a reader's file restores only while it stays put. */
        const val BACKUP_SECTION = "booruSubscriptions"
    }
}

/** The plugin's own screen for [route]. */
@Composable
private fun BooruRouteScreen(app: BooruApp, route: BooruRoute) {
    when (route) {
        is BooruRoute.Post -> BooruLinkedPostScreen(app, route.ref)
        is BooruRoute.Viewer -> BooruViewerScreen(app, route)
        is BooruRoute.Tag -> BooruTagScreen(app, route)
        is BooruRoute.Pool -> BooruPoolScreen(app, route)
        is BooruRoute.Search -> BooruSearchScreen(app, route)
    }
}
