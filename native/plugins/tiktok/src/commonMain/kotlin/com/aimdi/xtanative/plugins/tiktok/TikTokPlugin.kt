package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import kotlinx.coroutines.flow.Flow

/**
 * TikTok as a downloadable plugin (XTA's tiktok_plugin.dart): public
 * profiles watched as a guest, without an account. Following, the videos of
 * the accounts followed on this device; the search with discover's people,
 * the trending searches and TikTok's suggestions; profiles; the player.
 * Private, as in XTA: the store offers it only with private plugins shown.
 * Read-only: nothing is liked, followed or commented on at TikTok; follows
 * stay in the plugin's own folder, hearts in the app's settings.
 */
class TikTokPlugin : XtaPlugin() {
    private lateinit var app: TikTokApp

    override val id: String = "tiktok"
    override val brandColor: Color = Color(0xFFFE2C55)
    override val icon: ImageVector get() = TikTokIcons.Mark
    override val category: Category = Category.SOCIAL
    override val isPrivate: Boolean = true

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = TikTokApp(host, inAppPlayer)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> TikTokHome(app, frame) }

    /** Opened on its own, the app's bar has the search and the settings, so the tab's row leaves them out. */
    override val clientTab: PluginBody = { frame -> TikTokHome(app, frame, ownActions = false) }

    override val settings: PluginBody = { frame -> TikTokSettingsBody(app, frame) }

    override fun settingsTitle(language: String): String = Strings.settings_title.get(language)

    override val search: PluginSearch by lazy { TikTokAppSearch(app) }

    override fun routeFor(url: String): String? = TikTokRoutes.forUrl(url, inAppPlayer)

    /** The accounts followed here in the app's groups, as in XTA; never in Home's timelines, which XTA's TikTok stays out of. */
    override val feed: PluginFeed get() = app.shared

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    override val postActions: NetworkPostActions get() = app.actions

    override fun screen(route: String): PluginScreen? = TikTokRoutes.parse(route)?.let { opened -> { TikTokRouteScreen(app, opened) } }

    /** The accounts followed here, under XTA's own key for them and in its rows. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection(SUBSCRIPTIONS, export = { app.follows.export() }, import = { app.follows.import(it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size)

    private companion object {
        /** XTA's key for these rows (tiktok_plugin.dart): load-bearing, as a reader's file restores only while it stays put. */
        const val SUBSCRIPTIONS = "tiktokSubscriptions"
    }
}

/** The plugin's own screen for [route]. */
@Composable
private fun TikTokRouteScreen(app: TikTokApp, route: TikTokRoute) {
    when (route) {
        is TikTokRoute.Profile -> TikTokProfileScreen(app, route)
        is TikTokRoute.Video -> TikTokPlayerScreen(app, route)
        is TikTokRoute.Search -> TikTokSearchScreen(app, route.query)
    }
}
