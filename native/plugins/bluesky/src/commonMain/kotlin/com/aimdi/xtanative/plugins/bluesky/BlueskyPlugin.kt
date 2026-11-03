package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.XtaPlugin

/**
 * Bluesky as a downloadable plugin, read through the public AppView with no
 * account, as XTA's Bluesky plugin reads it: the Discover feed and the
 * accounts followed here as its home tab, threads, profiles with their tabs,
 * and search. Nothing is posted, liked, reposted or followed on Bluesky:
 * following stays on the device, in the plugin's own folder.
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

    override val homeTab: PluginBody = { frame -> BlueskyHome(app, frame) }

    override val settings: PluginBody = { frame -> BlueskySettingsBody(app, frame) }

    override val search: PluginSearch by lazy { BlueskySearch(app) }

    override fun routeFor(url: String): String? = BlueskyRoutes.forUrl(url)

    override fun screen(route: String): PluginScreen? = when (val opened = BlueskyRoutes.parse(route)) {
        is BlueskyRoute.Post -> threadScreen(opened.target)
        is BlueskyRoute.Profile -> profileScreen(opened.actor)
        null -> null
    }

    private fun threadScreen(target: PostTarget): PluginScreen = { BlueskyThreadScreen(app, target) }

    private fun profileScreen(actor: String): PluginScreen = { BlueskyProfileScreen(app, actor) }

    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection("bluesky.follows", export = { app.follows.export() }, import = { app.follows.import(it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size)

    private companion object {
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
