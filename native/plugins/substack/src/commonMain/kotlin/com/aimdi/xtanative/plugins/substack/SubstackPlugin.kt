package com.aimdi.xtanative.plugins.substack

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.substack.SubstackRoute
import com.aimdi.xtanative.networks.substack.SubstackRoutes
import com.aimdi.xtanative.ui.plugins.NetworkPost
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
import kotlinx.serialization.json.JsonElement

/**
 * Substack as a downloadable plugin, read without an account as XTA's
 * Substack plugin reads it: the publications followed here as Home, Inbox,
 * public Notes and the Library; publications with their archives; the native
 * reader with its place, contents and find, highlights, Listen and episodes;
 * discussions; search; OPML; its publications in the app's groups, and its
 * articles and notes in the app's Saved as XTA's link records. Nothing is
 * subscribed to, liked, commented on or paid for on Substack: follows, hearts,
 * saves, read marks and highlights stay on the device (the plugin's folder
 * and the app's settings, as XTA keeps them).
 */
class SubstackPlugin : XtaPlugin() {
    private lateinit var app: SubstackApp

    override val id: String = "substack"
    override val brandColor: Color = Color(0xFFFF6719)
    override val icon: ImageVector get() = SubstackIcons.Newspaper
    override val category: Category = Category.NEWSLETTERS

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = SubstackApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> SubstackHome(app, frame) }

    override val settings: PluginBody = { frame -> SubstackSettingsBody(app, frame) }

    override val search: PluginSearch by lazy { SubstackSearchUi(app) }

    override fun routeFor(url: String): String? = SubstackRoutes.forUrl(url, app.publications.value.orEmpty().map { it.baseUrl })

    override fun screen(route: String): PluginScreen? = when (val opened = SubstackRoutes.parse(route)) {
        is SubstackRoute.Post -> readerScreen(opened)
        is SubstackRoute.Comments -> commentsScreen(opened)
        is SubstackRoute.Publication -> publicationScreen(opened)
        is SubstackRoute.Note -> noteScreen(opened)
        SubstackRoute.Add -> addScreen()
        null -> null
    }

    private fun readerScreen(route: SubstackRoute.Post): PluginScreen = { SubstackReaderScreen(app, route.link, route.key) }

    private fun commentsScreen(route: SubstackRoute.Comments): PluginScreen = { SubstackCommentsScreen(app, route.link, route.key) }

    private fun publicationScreen(route: SubstackRoute.Publication): PluginScreen = { SubstackPublicationScreen(app, route.publication) }

    private fun noteScreen(route: SubstackRoute.Note): PluginScreen = { SubstackNoteScreen(app, route.id) }

    private fun addScreen(): PluginScreen = { SubstackAddScreen(app) }

    /** The publications followed here, in the groups that hold them; XTA has no switch for Home's timelines. */
    override val feed: PluginFeed get() = app.feed

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    override val postActions: NetworkPostActions get() = app.actions

    /** Its posts in the groups' timelines as its own card, with its heart, bookmark and read mark, as XTA's timelines draw them. */
    override fun postCard(post: NetworkPost): (@Composable () -> Unit)? {
        val drawn = app.drawnPost(post) ?: return null
        return { SubstackPostCard(app, drawn, rememberMarks(app), logoUrl = post.avatarUrl) }
    }

    /** An article or note kept as XTA's link record, as Saved draws it; another record is none of its. */
    override fun archived(content: JsonElement): NetworkPost? = app.shownSaved(content)

    /** The follows, as XTA's backup holds them: its `substackSubscriptions` rows. The rest of what it keeps is in the app's settings. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection(BACKUP_FOLLOWS, export = { app.follows.export() }, import = { app.follows.import(it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size + app.saves.load().size + app.likes.load().size)

    private companion object {
        /** XTA's backup key for the follows: load-bearing, as a reader's file is read by it. */
        const val BACKUP_FOLLOWS = "substackSubscriptions"
    }
}
