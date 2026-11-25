package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.saved.ProfileFiltersStore
import com.aimdi.xtanative.ui.common.LocalProfileFilters
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_hide_from
import com.aimdi.xtanative.ui.generated.resources.posts_show_from
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PostMenuHandlers
import com.aimdi.xtanative.ui.plugins.PostMenuRows
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.LocalKeeper
import com.aimdi.xtanative.ui.saved.NoteSheet
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.isSaved
import org.jetbrains.compose.resources.stringResource

/** What an X post's ⋯ sheet opens once it has closed: the note on the post, its folders, its author's groups; or nothing more. */
private enum class MenuStep { MENU, NOTE, FOLDERS, GROUPS, CLOSED }

/**
 * An X post's ⋯ sheet, in XTA's order (plugin_post_actions.dart, as
 * tweet_post_menu.dart opens it for an X post): save it on this device or
 * take it out, the note on it in Saved, Add to group for its author, Share
 * link, Save to folder…, Reposted by, Quotes, Open in new window (on the
 * desktop) and Open in browser; then, XTA Native's own, hiding its author's
 * posts in the feeds or showing them again. None of it
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
    val windows = LocalPostWindows.current
    val inWindow = remember(windows, shown.id) { windows?.let { open -> { open(shown.id) } } }
    PostMenuSurface(onDismiss) {
        PostMenuRows(menu, saved, handlers, onDone = { onStep(MenuStep.CLOSED) }, newWindow = inWindow)
        LocalProfileFilters.current?.let { filters -> HideAuthorRow(post, filters, onDone = { onStep(MenuStep.CLOSED) }) }
    }
}

/**
 * "Hide posts from @handle", or "Show posts from @handle" once hidden
 * ([HideAuthor]): kept in [filters] on this device and never told to X, and
 * written to its end although the sheet closes first.
 */
@Composable
private fun HideAuthorRow(post: XPost, filters: ProfileFiltersStore, onDone: () -> Unit) {
    val keeper = LocalKeeper.current
    LaunchedEffect(filters) { filters.filters() }
    val now by filters.state.collectAsState()
    val entry = HideAuthor.of(post, now) ?: return
    PluginMenuRow(
        PostIcons.VisibilityOff,
        stringResource(if (entry.hidden) Res.string.posts_show_from else Res.string.posts_hide_from, entry.screenName),
        onClick = {
            onDone()
            keeper.keep { filters.setAllHidden(entry.userId, entry.screenName, !entry.hidden) }
        },
    )
}

@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 24.dp))
    }
}
