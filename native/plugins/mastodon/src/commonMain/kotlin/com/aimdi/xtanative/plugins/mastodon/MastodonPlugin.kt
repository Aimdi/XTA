package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.mastodon.MastodonFollowRows
import com.aimdi.xtanative.networks.mastodon.MastodonSnapshot
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkPostExtras
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
import kotlinx.serialization.json.JsonElement

/**
 * Mastodon and the Fediverse as a downloadable plugin, read without an
 * account through Mastodon-compatible instances, as XTA's Mastodon plugin
 * reads them (docs/specs/mastodon-plugin.md): Explore, Local, Federated and
 * the accounts followed here, on Home and on its own; profiles, threads,
 * hashtags, search, who boosted and quoted a post, and what was saved from
 * it; the accounts followed here in Home's timelines when the reader asks,
 * its accounts in the app's groups, and its posts in the app's Saved with
 * their folders and notes. Nothing is posted, boosted, favourited, voted on
 * or followed on any instance: following and saving stay on the device.
 */
class MastodonPlugin : XtaPlugin() {
    private lateinit var app: MastodonApp

    override val id: String = "mastodon"
    override val brandColor: Color = Color(0xFF6364FF)
    override val icon: ImageVector get() = MastodonIcons.Public
    override val category: Category = Category.SOCIAL

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = MastodonApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> MastodonHome(app, frame, MastodonSurface.HOME) }

    override val clientTab: PluginBody = { frame -> MastodonHome(app, frame, MastodonSurface.CLIENT) }

    override val settings: PluginBody = { frame -> MastodonSettingsBody(app, frame) }

    override val search: PluginSearch by lazy { MastodonSearch(app) }

    override fun routeFor(url: String): String? = MastodonRoutes.forUrl(url, app.hosts())

    override fun screen(route: String): PluginScreen? {
        val opened = MastodonRoutes.parse(route) ?: return null
        return { MastodonScreen(app, opened) }
    }

    override val feed: PluginFeed get() = app.shared

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    override val postActions: NetworkPostActions get() = app.actions

    /** A poll's results under one of its posts in the app's shared rows, as XTA's card shows them there. */
    override fun postExtras(post: NetworkPost): NetworkPostExtras? = app.extrasOf(post)

    /** A post kept in XTA's record, as Saved draws it; one of another network is none of its. */
    override fun archived(content: JsonElement): NetworkPost? = MastodonSnapshot.fromArchive(content)?.let(app::shown)

    /** The accounts followed here, as XTA's backup keeps them: its `mastodonSubscriptions` rows. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection(MastodonFollowRows.BACKUP_KEY, export = { app.follows.exportRows() }, import = { app.restoreFollows(it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size)
}

@Composable
private fun MastodonScreen(app: MastodonApp, route: MastodonRoute) = when (route) {
    is MastodonRoute.Thread -> MastodonThreadScreen(app, route.target)
    is MastodonRoute.Profile -> MastodonProfileScreen(app, route.acct)
    is MastodonRoute.Tag -> MastodonTagScreen(app, route.name)
    is MastodonRoute.Reposts -> MastodonRepostersScreen(app, route.target)
    is MastodonRoute.Quotes -> MastodonQuotesScreen(app, route.target)
    MastodonRoute.Saved -> MastodonSavedScreen(app)
}
