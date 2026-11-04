package com.aimdi.xtanative.plugins.instagram

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
 * Instagram as a downloadable plugin, private as XTA's is: guest first, a
 * pasted session (or Threads' one) only when Instagram blocks guests. Its
 * tab (For you, the accounts followed here and those accounts), profiles,
 * people search and its settings; its accounts in the app's groups, and its
 * posts in the app's Saved as XTA keeps them, as links. Nothing is liked,
 * followed or commented on Instagram: follows and likes stay on the device.
 */
class InstagramPlugin : XtaPlugin() {
    private var attached: InstagramApp? = null
    private val app: InstagramApp get() = checkNotNull(attached) { "Instagram was asked for something before it was attached" }

    override val id: String = "instagram"
    override val brandColor: Color = Color(0xFFE1306C)
    override val icon: ImageVector get() = InstagramIcons.Mark
    override val category: Category = Category.SOCIAL
    override val isPrivate: Boolean = true

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        attached = InstagramApp(host)
    }

    override fun detach() {
        attached?.close()
        attached = null
    }

    override val homeTab: PluginBody = { frame -> InstagramHome(app, frame) }

    override val settings: PluginBody = { frame -> InstagramSettingsBody(app, frame) }

    override fun settingsTitle(language: String): String = Strings.settings_title.get(language)

    override val search: PluginSearch by lazy { InstagramSearch { app } }

    override fun routeFor(url: String): String? = InstagramRoutes.forUrl(url)

    override fun screen(route: String): PluginScreen? {
        val handle = InstagramRoutes.profileOf(route) ?: return null
        return { InstagramProfileScreen(app, handle) }
    }

    override val feed: PluginFeed get() = app.shared

    override val subscriptions: Flow<List<PluginSubscription>> get() = app.subscriptions

    override val postActions: NetworkPostActions get() = app.actions

    /** What XTA's backup holds of Instagram as its rows: the accounts followed. Its preferences travel with the app's settings. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection("instagramSubscriptions", export = { app.follows.export() }, import = { app.restoreFollows(it) }))

    /** The accounts followed and the posts liked on this device. */
    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.follows.load().size + InstagramLikes.ids(app.settings()).size)
}
