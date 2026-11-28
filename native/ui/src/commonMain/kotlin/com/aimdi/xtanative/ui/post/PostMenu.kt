package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PostMenuHandlers
import com.aimdi.xtanative.ui.plugins.PostMenuRows
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import com.aimdi.xtanative.ui.saved.ExportFormat
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.LocalKeeper
import com.aimdi.xtanative.ui.saved.NoteSheet
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.isSaved
import com.aimdi.xtanative.ui.saved.Keeper
import com.aimdi.xtanative.ui.settings.isDesktop
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_mute_account
import com.aimdi.xtanative.ui.generated.resources.posts_mute_word
import com.aimdi.xtanative.ui.generated.resources.posts_unmute_account
import java.time.Instant
import org.jetbrains.compose.resources.stringResource

/** What an X post's ⋯ sheet opens once it has closed: the note on the post, its folders, its author's groups; or nothing more. */
private enum class MenuStep { MENU, NOTE, FOLDERS, GROUPS, MUTE_WORD, EXPORT_MARKDOWN, EXPORT_HTML, CLOSED }

/**
 * An X post's ⋯ sheet, in XTA's order (plugin_post_actions.dart, as
 * tweet_post_menu.dart opens it for an X post): save it on this device or
 * take it out, the note on it in Saved, Add to group for its author, Open
 * profile, Share link (Copy link on the desktop), Copy text, Save to folder…, Reposted by, Quotes, Open in new window (on the
 * desktop) and Open in browser; then, on the desktop, saving it as Markdown or a
 * printable page, and muting. None of it
 * writes to X; what the running plugins add is in the post's share sheet,
 * where XTA has it.
 */
@Composable
fun PostMenu(post: XPost, actions: PostActions, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(MenuStep.MENU) }
    when (step) {
        MenuStep.MENU -> MenuSheet(post, actions, onDismiss, onStep = { step = it })
        MenuStep.NOTE -> actions.keep.NoteSheet(post, onDismiss)
        MenuStep.FOLDERS -> actions.keep.SaveSheet(post, onDismiss)
        MenuStep.GROUPS -> AuthorGroupsSheet(post.shown.author, onDismiss)
        MenuStep.MUTE_WORD -> {
            val keeper = LocalKeeper.current
            MuteWordDialog("", onDismiss) { term, duration ->
                onDismiss()
                Mutes.change(keeper, offerUndo = true) { it.withWord(term, duration?.let(Instant.now()::plus)) }
            }
        }
        MenuStep.EXPORT_MARKDOWN -> ExportPost(post, ExportFormat.MARKDOWN, onDismiss)
        MenuStep.EXPORT_HTML -> ExportPost(post, ExportFormat.HTML, onDismiss)
        MenuStep.CLOSED -> LaunchedEffect(Unit) { onDismiss() }
    }
}

/** The sheet's entries, the shared post sheet's ([PostMenuRows]), each closing it before it acts; a menu at the pointer on the desktop ([PostMenuSurface]). */
@Composable
private fun MenuSheet(post: XPost, actions: PostActions, onDismiss: () -> Unit, onStep: (MenuStep) -> Unit) {
    val keeper = LocalKeeper.current
    val shown = post.shown
    val url = PostView.url(post, postScope().prefs)
    val saved = actions.keep.isSaved(shown.id)
    val menu = remember(post, url, actions) {
        val kept = Keepable.of(post)
        PluginPostMenu(
            post = PluginPostArchive(kept.id, kept.authorId, kept.content),
            url = url,
            author = shown.author.id.takeIf { it.isNotEmpty() && shown.author.screenName.isNotEmpty() },
            reposts = { actions.openReposters(shown.id) },
            quotes = { actions.openQuotes(shown.id) },
        )
    }
    val handlers = remember(post, actions, keeper) {
        PostMenuHandlers(
            toggleSaved = { actions.keep.toggleSave(keeper, post) },
            editNote = { onStep(MenuStep.NOTE) },
            addToGroup = { onStep(MenuStep.GROUPS) },
            chooseFolder = { onStep(MenuStep.FOLDERS) },
        )
    }
    val copy = rememberCopyText()
    val words = remember(shown) { PostView.copiedText(shown).takeIf { it.isNotBlank() } }
    val copyText = words?.let { { copy(it) } }
    val openAuthor = remember(shown, actions) {
        if (shown.author.screenName.isEmpty()) null else { { actions.openUser(shown.author.screenName, shown.author.id) } }
    }
    val windows = LocalWindowOpens.current
    val inWindow = remember(windows, shown.id) { windows?.let { open -> { open.post(shown.id) } } }
    PostMenuSurface(onDismiss) {
        PostMenuRows(menu, saved, handlers, onDone = { onStep(MenuStep.CLOSED) }, newWindow = inWindow, hints = true, openAuthor = openAuthor, copyText = copyText)
        if (isDesktop) {
            MenuGroupDivider()
            ExportRows(onStep)
        }
        MenuGroupDivider()
        MuteRows(shown, keeper, onStep)
    }
}

/** Saving the post, with its thread, as a file to read or print ([ExportPost]). */
@Composable
private fun ExportRows(onStep: (MenuStep) -> Unit) {
    MenuRow(GroupIcons.FileDownloadOutlined, stringResource(PostExports.label(ExportFormat.MARKDOWN))) { onStep(MenuStep.EXPORT_MARKDOWN) }
    MenuRow(GroupIcons.PrintOutlined, stringResource(PostExports.label(ExportFormat.HTML))) { onStep(MenuStep.EXPORT_HTML) }
}

/** Muting the post's author on this device, or no longer, and muting a word ([Mutes]). */
@Composable
private fun MuteRows(shown: XPost, keeper: Keeper, onStep: (MenuStep) -> Unit) {
    val author = shown.author
    if (author.screenName.isNotEmpty()) {
        val muted = muteList().mutes(author.screenName, author.id, Instant.now())
        MenuRow(GroupIcons.VisibilityOffOutlined, stringResource(if (muted) Res.string.posts_unmute_account else Res.string.posts_mute_account, author.screenName)) {
            onStep(MenuStep.CLOSED)
            Mutes.change(keeper, offerUndo = !muted) { if (muted) it.unmuting(author.screenName, author.id) else it.withAccount(author.screenName, author.id) }
        }
    }
    MenuRow(GroupIcons.SpeakerNotesOffOutlined, stringResource(Res.string.posts_mute_word)) { onStep(MenuStep.MUTE_WORD) }
}

/** A row of a post's menu or sheet, laid out as [PluginMenuRow] lays one out (denser in a desktop's pointer menu). */
@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) = PluginMenuRow(icon, label, onClick)
