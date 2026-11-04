package com.aimdi.xtanative.plugins.threads

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
 * Threads as a downloadable plugin, read as XTA's Threads plugin reads it:
 * Meta's public pages with no account, and a pasted session, an RSSHub
 * instance or an Xy server only when the reader names one. Its tab (the
 * accounts followed here, newest first, and the posts liked here), the
 * conversation, profiles with their tabs, search and every setting XTA has.
 * Nothing is posted, liked, reposted or followed on Threads: following and
 * likes stay on the device.
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
        null -> null
    }

    /** What XTA's backup holds of Threads as its rows: the accounts followed and the likes. Its preferences travel with the app's settings. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(
            PluginBackupSection("threadsSubscriptions", export = { app.accounts.export() }, import = { app.restoreAccounts(it) }),
            PluginBackupSection("threadsLocalLikes", export = { app.likes.export() }, import = { app.likes.import(it) }),
        )

    /** The accounts followed and the posts liked on this device. */
    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.accounts.load().size + app.likes.load().size)
}
