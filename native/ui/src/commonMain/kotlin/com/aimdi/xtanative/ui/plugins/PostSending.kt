package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.store.saved.SavedMedia

private val WHITESPACE = Regex("(?U)\\s+")

/**
 * A post as the post sheets hand it to the plugins' entries
 * ([XtaPlugin.postSheetActions]): where it lives on the web ([url]), the line
 * it goes by elsewhere ([title], made with [titleOf]; empty when its plugin
 * gave none) and its [media] as downloads and uploads take them.
 */
data class PluginSharedPost(val url: String, val title: String = "", val media: List<SavedMedia> = emptyList()) {
    companion object {
        /**
         * The line a post goes by in the reader's own bookmarks, as XTA titles
         * one it sends there (tweet_footer.dart's share sheet): its author by
         * [name] and [handle], then as much of [text] as fits on one line, so
         * it is recognisable before the bookmark's own page is read.
         */
        fun titleOf(name: String?, handle: String?, text: String): String {
            val author = name?.trim().orEmpty()
            val at = handle?.trim().orEmpty()
            val byline = when {
                author.isNotEmpty() && at.isNotEmpty() -> "$author (@$at)"
                author.isNotEmpty() -> author
                at.isNotEmpty() -> "@$at"
                else -> null
            }
            val line = text.replace(WHITESPACE, " ").trim()
            return when {
                byline == null -> line
                line.isEmpty() -> byline
                else -> "$byline: $line"
            }
        }
    }
}

/**
 * A switch a plugin adds to Saved's folder dialog, under the app's own
 * (XTA's "Upload media to Immich"): its [title] and [description] in a
 * language. It is the folder's upload switch (`SavedFolder.autoUpload`, XTA's
 * `auto_upload`), which the folder keeps whether or not the plugin is
 * installed, as XTA's backup carries it; a folder keeps one, so the first
 * running plugin that offers one has it ([XtaPlugin.folderOption]).
 */
class PluginFolderOption(val title: (language: String) -> String, val description: (language: String) -> String)

/**
 * A post the reader filed in one of Saved's folders whose switch is on
 * ([PluginFolderOption], [XtaPlugin.filed]): its [id] in the archive, the
 * [folder]'s name, and its [media] as downloads and uploads take them (every
 * picture, video and GIF of an X post; another network's pictures), as XTA's
 * filing hands them on.
 */
data class PluginFiledPost(val id: String, val folder: String, val media: List<SavedMedia>)
