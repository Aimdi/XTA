package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Hacker News as a downloadable plugin, read as a guest as XTA's plugin reads
 * it: its six lists, the stories saved and the accounts followed here,
 * stories with their comments, accounts, and search; its accounts in the
 * app's groups, and its stories in the app's Saved with their folders and
 * notes. No login, vote, comment or submission; hearts, saves and follows
 * stay on the device, in the app's settings under XTA's keys, so XTA's
 * backup and the app's carry them.
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

    /** The accounts followed here, in the groups that hold them; XTA has no switch for Home's timelines. */
    override val feed: PluginFeed get() = app.feed

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    override val postActions: NetworkPostActions get() = app.actions

    /** Its stories in a group's feed as its own card, with the heart and the bookmark kept here, as XTA's groups draw them (hn_group.dart). */
    override fun postCard(post: NetworkPost): (@Composable () -> Unit)? {
        val story = app.drawnStory(post) ?: return null
        return { HnStoryRow(app, story) }
    }

    /** A story kept as XTA's link record, as Saved draws it; another record is none of its. */
    override fun archived(content: JsonElement): NetworkPost? = HnShared.story(content)?.let { app.shown(it, words = true) }

    override suspend fun footprint(): PluginFootprint = app.kept.value.let { PluginFootprint(it.saved.size + it.liked.size + it.follows.size) }
}

@Composable
private fun HnScreen(app: HnApp, route: HnRoute) = when (route) {
    is HnRoute.Story -> HnStoryScreen(app, route.id)
    is HnRoute.User -> HnUserScreen(app, route.id)
}
