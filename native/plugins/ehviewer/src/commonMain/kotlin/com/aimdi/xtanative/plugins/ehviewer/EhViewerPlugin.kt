package com.aimdi.xtanative.plugins.ehviewer

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
 * EH as a downloadable plugin (XTA's eh_plugin.dart): E-Hentai's galleries,
 * or ExHentai's with the reader's pasted cookies, read as XTA reads them:
 * Popular, the front page, the toplists and Watched; the search with the
 * site's tag suggestions and filters; a gallery's page with its tags,
 * comments and previews; the page reader in three modes. Private, as in
 * XTA: the store offers it only with private plugins shown. Read-only:
 * nothing is favourited, rated or commented on at the site; favourites and
 * the reading history stay in the plugin's own folder.
 */
class EhViewerPlugin : XtaPlugin() {
    private lateinit var app: EhApp

    override val id: String = "ehviewer"
    override val brandColor: Color = Color(0xFF5C6BC0)
    override val icon: ImageVector get() = EhIcons.Mark
    override val category: Category = Category.ART
    override val isPrivate: Boolean = true

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = EhApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> EhHome(app, frame) }

    /** Opened on its own, the app's bar has the search and the settings, so the tab's row leaves them out. */
    override val clientTab: PluginBody = { frame -> EhHome(app, frame, ownActions = false) }

    override val settings: PluginBody = { frame -> EhSettingsBody(app, frame) }

    override val search: PluginSearch by lazy { EhAppSearch(app) }

    override fun screen(route: String): PluginScreen? = EhRoutes.parse(route)?.let { opened -> { EhRouteScreen(app, opened) } }

    /** The favourites and the history kept here, under XTA's own keys for them and in its rows. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(
            PluginBackupSection(FAVORITES, export = { app.favorites.export() }, import = { app.favorites.import(it) }),
            PluginBackupSection(HISTORY, export = { app.history.export() }, import = { app.history.import(it) }),
        )

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.favorites.load().size + app.history.load().size)

    private companion object {
        /** XTA's keys for these rows (eh_plugin.dart): load-bearing, as a reader's file restores only while they stay put. */
        const val FAVORITES = "ehFavorites"
        const val HISTORY = "ehHistory"
    }
}

/** The plugin's own screen for [route]. */
@Composable
private fun EhRouteScreen(app: EhApp, route: EhRoute) {
    when (route) {
        is EhRoute.Gallery -> EhGalleryScreen(app, route)
        is EhRoute.Reader -> EhReaderScreen(app, route)
        is EhRoute.Search -> EhSearchScreen(app, route.query)
    }
}
