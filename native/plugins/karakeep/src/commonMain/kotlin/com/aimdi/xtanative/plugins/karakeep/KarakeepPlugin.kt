package com.aimdi.xtanative.plugins.karakeep

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostAction
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.plugins.XtaPlugin

/**
 * Karakeep as a downloadable plugin, as XTA's (karakeep_plugin.dart): no
 * tab of its own, but "Save to Karakeep" in every post's sheet, which keeps
 * the post's link and title in the reader's own Karakeep, configured or not
 * (an unconfigured one says what is missing), and its settings. Saving a
 * bookmark on the reader's own server is the one write it makes; nothing
 * reaches the network a post came from.
 */
class KarakeepPlugin : XtaPlugin() {
    private var attached: KarakeepApp? = null
    private val app: KarakeepApp get() = checkNotNull(attached) { "Karakeep was asked for something before it was attached" }

    override val id: String = "karakeep"
    override val brandColor: Color = Color(0xFF0F766E)
    override val icon: ImageVector get() = KarakeepIcons.Mark
    override val category: Category = Category.BOOKMARKS

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        attached = KarakeepApp(host)
    }

    override fun detach() {
        attached = null
    }

    override val settings: PluginBody = { frame -> KarakeepSettingsBody(app, frame) }

    override fun postSheetActions(post: PluginSharedPost): List<PluginPostAction> {
        val running = app
        return listOf(PluginPostAction(KarakeepIcons.Save, { language -> Strings.save_action.get(language) }) { shared -> running.save(shared) })
    }
}
