package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * Hacker News as a downloadable plugin, read as a guest as XTA's plugin reads
 * it: its six lists, the stories saved and the accounts followed here,
 * stories with their comments, accounts, and search. No login, vote, comment
 * or submission; hearts, saves and follows stay on the device, in the app's
 * settings under XTA's keys, so XTA's backup and the app's carry them.
 */
class HackerNewsPlugin : XtaPlugin() {
    private lateinit var app: HnApp

    override val id: String = "hackernews"
    override val brandColor: Color = HnBrand
    override val icon: ImageVector get() = PostIcons.Forum
    override val category: Category = Category.COMMUNITIES

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = HnApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> HnHome(app, frame) }

    override val settings: PluginBody = { frame -> HnSettingsBody(frame) }

    override val search: PluginSearch by lazy { HnSearch(app) }

    override fun routeFor(url: String): String? = HnRoutes.forUrl(url)

    override fun screen(route: String): PluginScreen? {
        val opened = HnRoutes.parse(route) ?: return null
        return { HnScreen(app, opened) }
    }

    override suspend fun footprint(): PluginFootprint = app.kept.value.let { PluginFootprint(it.saved.size + it.liked.size + it.follows.size) }
}

@Composable
private fun HnScreen(app: HnApp, route: HnRoute) = when (route) {
    is HnRoute.Story -> HnStoryScreen(app, route.id)
    is HnRoute.User -> HnUserScreen(app, route.id)
}
