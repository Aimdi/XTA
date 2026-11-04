package com.aimdi.xtanative.plugins.immich

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginFiledPost
import com.aimdi.xtanative.ui.plugins.PluginFolderOption
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.XtaPlugin

/**
 * Immich as a downloadable plugin, as XTA's (immich_plugin.dart): no tab of
 * its own, but "Upload media to Immich" in Saved's folder dialog, which sends
 * the photos and videos of each post filed in such a folder to the reader's
 * own Immich (an album per folder, videos too, as its settings say), and
 * its settings. That upload is the one write it makes; it asks the server
 * nothing about the rest of the reader's library, and nothing reaches the
 * network a post came from.
 */
class ImmichPlugin : XtaPlugin() {
    private var attached: ImmichApp? = null
    private val app: ImmichApp get() = checkNotNull(attached) { "Immich was asked for something before it was attached" }

    override val id: String = "immich"
    override val brandColor: Color = Color(0xFF4250AF)
    override val icon: ImageVector get() = ImmichIcons.Mark
    override val category: Category = Category.MEDIA

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        attached = ImmichApp(host)
    }

    override fun detach() {
        attached = null
    }

    override val settings: PluginBody = { frame -> ImmichSettingsBody(app, frame) }

    override val folderOption: PluginFolderOption = PluginFolderOption(
        title = { language -> Strings.auto_upload.get(language) },
        description = { language -> Strings.auto_upload_description.get(language) },
    )

    override suspend fun filed(post: PluginFiledPost) = app.filed(post)

    /** The files sent to the reader's Immich from this device, which the upload log keeps. */
    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.log.count())
}
