package com.aimdi.xtanative.ui.plugins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_add_to_group
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.posts_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_reposted_by
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.saved_add_note
import com.aimdi.xtanative.ui.generated.resources.saved_save_here
import com.aimdi.xtanative.ui.generated.resources.saved_save_to_folder
import com.aimdi.xtanative.ui.generated.resources.saved_unsave
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import org.jetbrains.compose.resources.stringResource

/** How a sheet draws one of its entries: its icon, its words, and what a tap does. */
typealias PluginMenuEntry = @Composable (icon: ImageVector, label: String, onClick: () -> Unit) -> Unit

private val SheetEntry: PluginMenuEntry = { icon, label, onClick -> PluginMenuRow(icon, label, onClick) }

/**
 * The shared entries of a post's sheet, for a plugin that draws its own
 * sheet with its own entries above them, as XTA's Pixiv does: what
 * [PluginHost.openPostMenu] shows of [menu], each entry closing the sheet
 * ([onDone]) before it acts, drawn as [row] draws the sheet's own entries.
 */
@Composable
fun PluginPostMenuRows(host: PluginHost, menu: PluginPostMenu, onDone: () -> Unit, row: PluginMenuEntry = SheetEntry) {
    val saved by host.saved.collectAsState()
    val handlers = remember(host, menu) {
        PostMenuHandlers(
            toggleSaved = { host.toggleSaved(menu.post) },
            editNote = { host.editNote(menu.post) },
            addToGroup = { author -> host.addToGroup(author, menu.follow) },
            chooseFolder = { host.chooseFolder(menu.post) },
        )
    }
    PostMenuRows(menu, menu.post.id in saved, handlers, onDone, row)
}

/** What the shared entries of a post's sheet do, in the app's sheet and in a plugin's alike. */
internal class PostMenuHandlers(
    val toggleSaved: () -> Unit,
    val editNote: () -> Unit,
    val addToGroup: (author: String) -> Unit,
    val chooseFolder: () -> Unit,
)

/**
 * A plugin post's entries in XTA's order (showPluginPostActions): saving it
 * on this device or taking it out, the note on it, its author's groups,
 * sharing its link, its folder, who reposted and quoted it where the plugin
 * lists them, and the browser.
 */
@Composable
internal fun PostMenuRows(
    menu: PluginPostMenu,
    saved: Boolean,
    handlers: PostMenuHandlers,
    onDone: () -> Unit,
    row: PluginMenuEntry = SheetEntry,
) {
    val outside = rememberOpenOutside()
    val share = rememberShareText()
    val then = { action: () -> Unit -> { onDone(); action() } }
    row(if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder, stringResource(if (saved) Res.string.saved_unsave else Res.string.saved_save_here), then(handlers.toggleSaved))
    row(SavedIcons.EditNote, stringResource(Res.string.saved_add_note), then(handlers.editNote))
    menu.author?.let { author -> row(PostIcons.People, stringResource(Res.string.posts_add_to_group), then { handlers.addToGroup(author) }) }
    row(Icons.Filled.Share, stringResource(Res.string.reader_link_share), then { share(menu.url) })
    row(SavedIcons.Folder, stringResource(Res.string.saved_save_to_folder), then(handlers.chooseFolder))
    menu.reposts?.let { open -> row(XIcons.Repost, stringResource(Res.string.posts_reposted_by), then(open)) }
    menu.quotes?.let { open -> row(PostIcons.Quote, stringResource(Res.string.posts_quotes), then(open)) }
    row(PostIcons.OpenInNew, stringResource(Res.string.posts_open_in_browser), then { outside(menu.url) })
}
