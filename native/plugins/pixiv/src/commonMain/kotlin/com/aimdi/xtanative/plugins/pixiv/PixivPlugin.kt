package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.pixiv.PixivComment
import com.aimdi.xtanative.networks.pixiv.PixivLinkRef
import com.aimdi.xtanative.networks.pixiv.PixivLinks
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginDiscovery
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Pixiv as a downloadable plugin: XTA's Pixiv screens, reading as the
 * signed-in account; its creators in the app's groups, its works and novels
 * in the app's Saved, its pages through the app's downloads. Bookmarks,
 * follows and history stay on this device; nothing is written to Pixiv.
 */
class PixivPlugin : XtaPlugin() {
    private var attached: PixivRuntime? = null
    private val runtime: PixivRuntime get() = checkNotNull(attached) { "Pixiv was asked for something before it was attached" }

    override val id: String = "pixiv"
    override val brandColor: Color = Color(0xFF0096FA)
    override val icon: ImageVector get() = PixivIcons.Brush
    override val category: Category = Category.ART
    override val isPrivate: Boolean = true

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        attached = PixivRuntime(host)
    }

    override fun detach() {
        attached?.close()
        attached = null
    }

    override val homeTab: PluginBody = { frame -> PixivHome(runtime, frame) }

    override val settings: PluginBody = { frame -> PixivSettingsBody(runtime, frame) }

    override val search: PluginSearch by lazy { PixivAppSearch { runtime } }

    override fun routeFor(url: String): String? = PixivRoutes.forUrl(url)

    /** A creator in the app's groups opens on their profile, as XTA's destinationFor opens them. */
    override fun routeForAccount(account: String): String? = PixivRoutes.forAccount(account)

    override fun screen(route: String): PluginScreen? {
        val opened = PixivRoutes.parse(route) ?: return null
        return { PixivRoot(runtime) { PixivScreenOf(opened) } }
    }

    /** The creators followed here, in the groups that hold them; XTA has no switch for Home's timelines. */
    override val feed: PluginFeed get() = runtime.feed

    override val subscriptions: Flow<List<PluginSubscription>> get() = runtime.subscriptions

    /** The creators Pixiv relates to a group's creators, for the group's Discover. */
    override val discovery: PluginDiscovery get() = PixivDiscover(runtime)

    override val postActions: NetworkPostActions get() = runtime.actions

    /** A work or novel kept as XTA's link record, as Saved draws it; another record is none of its. */
    override fun archived(content: JsonElement): NetworkPost? = runtime.shownSaved(content)

    /** The bookmarks kept here; the settings are the app's, which its backup carries. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection("pixiv.bookmarks", export = { runtime.bookmarks.export() }, import = { runtime.bookmarks.import(it) }))

    /** The creators followed and the works and novels bookmarked on this device. */
    override suspend fun footprint(): PluginFootprint = PluginFootprint(PixivFollows.of(runtime.settings.load()).size + runtime.bookmarks.load().size)
}

/** The screen [route] names. */
@Composable
internal fun PixivScreenOf(route: PixivRoute) {
    val runtime = LocalPixiv.current
    when (route) {
        is PixivRoute.Work -> PixivWorkScreen(route)
        is PixivRoute.Reader -> PixivReaderScreen(route)
        is PixivRoute.User -> PixivUserScreen(route.id, route.tab)
        is PixivRoute.Users -> PixivUsersScreen(route.kind, route.userId)
        PixivRoute.RecommendedUsers -> PixivRecommendedUsersScreen()
        is PixivRoute.Comments -> PixivCommentsScreen(route.kind, route.id, runtime.shelf.get(route.parent) as? PixivComment)
        is PixivRoute.Search -> PixivSearchScreen(route.query, route.kind)
        is PixivRoute.Series -> PixivSeriesScreen(route.id)
        is PixivRoute.Novel -> PixivNovelScreen(route.id, runtime.shelf.get(route.shelf) as? PixivNovel)
        is PixivRoute.NovelSeries -> PixivNovelSeriesScreen(route.id)
        PixivRoute.PixivisionList -> PixivisionListScreen()
        is PixivRoute.Pixivision -> PixivisionArticleScreen(route.id, route.language)
        is PixivRoute.Short -> PixivShortLinkScreen(route.name)
        is PixivRoute.History -> PixivHistoryScreen(route.kind)
        PixivRoute.FavoriteTags -> PixivFavoriteTagsScreen()
        PixivRoute.Mute -> PixivMuteScreen()
        PixivRoute.CopyTemplate -> PixivCopyTemplateScreen()
        PixivRoute.SignIn -> PixivSignInScreen()
        PixivRoute.Settings -> PixivSettingsScreen()
        is PixivRoute.Picture -> (runtime.shelf.get(route.shelf) as? String)?.let { PixivPictureScreen(it) } ?: LostScreen()
    }
}

/** A pixiv.me link, followed to where it goes, then that screen in its place. */
@Composable
private fun PixivShortLinkScreen(name: String) {
    val runtime = LocalPixiv.current
    val fetch = rememberKept("pixiv-short-$name") { scope ->
        PixivFetch<PixivLinkRef>(scope) { PixivLinks.resolve(runtime.host.http, PixivLinkRef.ShortLink(name)) ?: throw NoSuchElementException(name) }.also { it.ensure() }
    }
    val state by fetch.state.collectAsState()
    val target = state.value
    if (target != null) return PixivScreenOf(PixivRoutes.routeOf(target))
    PluginScaffold(title = "", onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.error != null && !state.loading) PixivEmpty(pluginString(Strings.open_link_failed), onRetry = fetch::load) else LoadingRow()
        }
    }
}

/** What a screen was handed is gone, as after the app was restarted. */
@Composable
private fun LostScreen() {
    val runtime = LocalPixiv.current
    PluginScaffold(title = "", onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { PixivEmpty(pluginString(Strings.open_link_failed)) }
    }
}
