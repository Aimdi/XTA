package com.aimdi.xtanative.plugins.threads

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFollowable
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Threads as a downloadable plugin, read as XTA's Threads plugin reads it:
 * Meta's public pages with no account, and a pasted session, an RSSHub
 * instance or an Xy server only when the reader names one. Its tab (the
 * accounts followed here, newest first, and the posts liked here), the
 * conversation, profiles with their tabs, search and every setting XTA has;
 * the accounts followed here in Home's timelines when the reader asks, its
 * accounts in the app's groups, and its posts in the app's Saved with their
 * folders and notes. Nothing is posted, liked, reposted or followed on
 * Threads: following and likes stay on the device.
 */
class ThreadsPlugin : XtaPlugin() {
    private var attached: ThreadsApp? = null
    private val app: ThreadsApp get() = checkNotNull(attached) { "Threads was asked for something before it was attached" }

    override val id: String = "threads"
    override val brandColor: Color = Color(0xFF101010)
    override val icon: ImageVector get() = ThreadsIcons.Mark
    override val category: Category = Category.SOCIAL

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        attached = ThreadsApp(host)
    }

    override fun detach() {
        attached?.close()
        attached = null
    }

    override val homeTab: PluginBody = { frame -> ThreadsHome(app, frame) }

    override val settings: PluginBody = { frame -> ThreadsSettingsBody(app, frame) }

    override val search: PluginSearch by lazy { ThreadsSearch { app } }

    override fun routeFor(url: String): String? = ThreadsRoutes.forUrl(url)

    override fun screen(route: String): PluginScreen? = when (val opened = ThreadsRoutes.parse(route)) {
        is ThreadsRoute.Post -> { { ThreadsConversationScreen(app, opened) } }
        is ThreadsRoute.Profile -> { { ThreadsProfileScreen(app, opened.handle) } }
        is ThreadsRoute.Search -> { { ThreadsSearchScreen(app, opened.terms) } }
        is ThreadsRoute.Media -> { { ThreadsMediaScreen(app, opened) } }
        ThreadsRoute.Saved -> { { ThreadsSavedScreen(app) } }
        ThreadsRoute.SignIn -> { { ThreadsSignInScreen(app) } }
        null -> null
    }

    /** A conversation opens as XTA's opened post does: a card over the page it came from, dragged or thrown back. */
    override fun opensAsCard(route: String): Boolean = ThreadsRoutes.parse(route) is ThreadsRoute.Post

    override val feed: PluginFeed get() = app.shared

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    /** A handle typed into a group's add box, followed here as XTA's box follows it. */
    override fun followable(text: String): PluginFollowable? = app.followable(text)

    override val postActions: NetworkPostActions get() = app.actions

    /** Its posts in the app's timelines as its own card, with the heart kept here, as XTA's timelines draw them. */
    override fun postCard(post: NetworkPost): (@Composable () -> Unit)? {
        val drawn = app.drawnPost(post) ?: return null
        return { ThreadsPostCard(app, drawn) }
    }

    /** A post kept in XTA's record, as Saved draws it; one of another network, or kept as a link, is none of its. */
    override fun archived(content: JsonElement): NetworkPost? = ThreadsArchive.post(content)?.let(app::shown)

    /** What XTA's backup holds of Threads as its rows: the accounts followed and the likes. Its preferences travel with the app's settings. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(
            PluginBackupSection("threadsSubscriptions", export = { app.accounts.export() }, import = { app.restoreAccounts(it) }),
            PluginBackupSection("threadsLocalLikes", export = { app.likes.export() }, import = { app.likes.import(it) }),
        )

    /** The accounts followed and the posts liked on this device. */
    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.accounts.load().size + app.likes.load().size)
}
