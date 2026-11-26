package com.aimdi.xtanative.ui.plugins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_open_profile
import com.aimdi.xtanative.ui.generated.resources.posts_add_to_group
import com.aimdi.xtanative.ui.generated.resources.posts_copy_text
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_new_window
import com.aimdi.xtanative.ui.generated.resources.posts_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_reposted_by
import com.aimdi.xtanative.ui.generated.resources.reader_link_copy
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.saved_add_note
import com.aimdi.xtanative.ui.generated.resources.saved_save_here
import com.aimdi.xtanative.ui.generated.resources.saved_save_to_folder
import com.aimdi.xtanative.ui.generated.resources.saved_unsave
import com.aimdi.xtanative.ui.home.HomeIcons
import com.aimdi.xtanative.ui.post.HintedMenuRow
import com.aimdi.xtanative.ui.post.MenuGroupDivider
import com.aimdi.xtanative.ui.post.MenuHint
import com.aimdi.xtanative.ui.post.NewWindowIcon
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
 * an X post's author's profile ([openAuthor]), sharing its link (copying it
 * where there is no share sheet), an X post's words ([copyText]), its folder, who reposted and quoted it where the plugin
 * lists them, a window of its own where the app opens one ([newWindow]:
 * an X post's on the desktop), and the browser.
 */
@Composable
internal fun PostMenuRows(
    menu: PluginPostMenu,
    saved: Boolean,
    handlers: PostMenuHandlers,
    onDone: () -> Unit,
    row: PluginMenuEntry = SheetEntry,
    newWindow: (() -> Unit)? = null,
    hints: Boolean = false,
    openAuthor: (() -> Unit)? = null,
    copyText: (() -> Unit)? = null,
) {
    val outside = rememberOpenOutside()
    val share = rememberShareText()
    val then = { action: () -> Unit -> { onDone(); action() } }
    // The keys an X post's entries have on the desktop ([hints]), shown in a pointer menu; a plugin's rows draw none.
    val hinted: @Composable (ImageVector, String, MenuHint, () -> Unit) -> Unit = { icon, label, hint, onClick ->
        if (hints) HintedMenuRow(icon, label, hint, onClick) else row(icon, label, onClick)
    }
    // Keeping it on this device and sharing it, then the lists of who passed it on, then opening it elsewhere: lines between them in a pointer menu.
    hinted(if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder, stringResource(if (saved) Res.string.saved_unsave else Res.string.saved_save_here), MenuHint.SAVE, then(handlers.toggleSaved))
    row(SavedIcons.EditNote, stringResource(Res.string.saved_add_note), then(handlers.editNote))
    menu.author?.let { author -> row(HomeIcons.PeopleOutlined, stringResource(Res.string.posts_add_to_group), then { handlers.addToGroup(author) }) }
    openAuthor?.let { open -> row(PostIcons.PersonOutline, stringResource(Res.string.groups_open_profile), then(open)) }
    // Where there is no share sheet (the desktop) the link is copied, and the entry says so.
    val copies = pluginsOnDesktop
    hinted(
        if (copies) PostIcons.ContentCopy else Icons.Outlined.Share,
        stringResource(if (copies) Res.string.reader_link_copy else Res.string.reader_link_share),
        MenuHint.COPY_LINK,
        then { share(menu.url) },
    )
    copyText?.let { copy -> row(PostIcons.ContentCopy, stringResource(Res.string.posts_copy_text), then(copy)) }
    row(SavedIcons.Folder, stringResource(Res.string.saved_save_to_folder), then(handlers.chooseFolder))
    if (menu.reposts != null || menu.quotes != null) MenuGroupDivider()
    menu.reposts?.let { open -> row(XIcons.Repost, stringResource(Res.string.posts_reposted_by), then(open)) }
    menu.quotes?.let { open -> row(PostIcons.Quote, stringResource(Res.string.posts_quotes), then(open)) }
    MenuGroupDivider()
    newWindow?.let { open -> row(NewWindowIcon, stringResource(Res.string.posts_open_in_new_window), then(open)) }
    row(PostIcons.OpenInNew, stringResource(Res.string.posts_open_in_browser), then { outside(menu.url) })
}

