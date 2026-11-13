package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.ui.plugins.PluginFiledPost
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.saved.Keepable

/**
 * What the plugins that send a post elsewhere are handed: a post of a sheet
 * (XTA's share sheet), or one filed in a folder of Saved whose switch is on
 * (XTA's folder_picker.dart). Its media are what downloads and uploads take
 * of the post as the archive keeps it, so a sheet and a filing hand the same.
 */
internal object PluginSaves {
    /** An X post of the app's post sheet, at [url] (the link its Share gives): its author and its words as one line. */
    fun shared(post: XPost, url: String): PluginSharedPost {
        val shown = post.shown
        val title = PluginSharedPost.titleOf(shown.author.name, shown.author.screenName, XText.plain(shown))
        return PluginSharedPost(url, title, SavedContent.of(Keepable.of(post).content).media)
    }

    /** A plugin's post of the shared post menu, by what its plugin gave. */
    fun shared(menu: PluginPostMenu): PluginSharedPost =
        PluginSharedPost(menu.url, menu.title.orEmpty(), SavedContent.of(menu.post.content).media)

    /** [item] filed in [folder], for the plugin that has the folder's switch; null while the switch is off. */
    fun filed(folder: SavedFolder, item: Keepable): PluginFiledPost? =
        if (folder.autoUpload) PluginFiledPost(item.id, folder.name, SavedContent.of(item.content).media) else null
}
