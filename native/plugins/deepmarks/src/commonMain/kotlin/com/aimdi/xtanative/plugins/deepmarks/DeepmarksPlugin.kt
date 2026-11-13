package com.aimdi.xtanative.plugins.deepmarks

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostAction
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.plugins.XtaPlugin

/**
 * Deepmarks, the Nostr-backed bookmarking service, as a downloadable plugin,
 * as XTA's (deepmarks_plugin.dart): no tab of its own, but "Save to
 * Deepmarks" in every post's sheet, which signs the post's link and title
 * as a web bookmark on this device (the signing key never leaves it) and
 * hands the finished event to the reader's Deepmarks, configured or not (an
 * unconfigured one says what is missing); and its settings. That bookmark is
 * the one write it makes; nothing reaches the network a post came from.
 */
class DeepmarksPlugin : XtaPlugin() {
    private var attached: DeepmarksApp? = null
    private val app: DeepmarksApp get() = checkNotNull(attached) { "Deepmarks was asked for something before it was attached" }

    override val id: String = "deepmarks"

    /** Nostr-adjacent purple, as XTA's. */
    override val brandColor: Color = Color(0xFF8B5CF6)
    override val icon: ImageVector get() = DeepmarksIcons.Mark
    override val category: Category = Category.BOOKMARKS

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        attached = DeepmarksApp(host)
    }

    override fun detach() {
        attached = null
    }

    override val settings: PluginBody = { frame -> DeepmarksSettingsBody(app, frame) }

    override fun postSheetActions(post: PluginSharedPost): List<PluginPostAction> {
        val running = app
        return listOf(PluginPostAction(DeepmarksIcons.Save, { language -> Strings.save_action.get(language) }) { shared -> running.save(shared) })
    }
}
