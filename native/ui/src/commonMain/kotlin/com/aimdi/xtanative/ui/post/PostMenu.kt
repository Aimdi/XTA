package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PostMenuHandlers
import com.aimdi.xtanative.ui.plugins.PostMenuRows
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.NoteSheet
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.isSaved

/** What an X post's ⋯ sheet opens once it has closed: the note on the post, its folders, its author's groups; or nothing more. */
private enum class MenuStep { MENU, NOTE, FOLDERS, GROUPS, CLOSED }

/**
 * An X post's ⋯ sheet, in XTA's order (plugin_post_actions.dart, as
 * tweet_post_menu.dart opens it for an X post): save it on this device or
 * take it out, the note on it in Saved, Add to group for its author, Share
 * link, Save to folder…, Reposted by, Quotes and Open in browser. None of it
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

/** The sheet's entries, the shared post sheet's ([PostMenuRows]), each closing it before it acts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(post: XPost, actions: PostActions, onDismiss: () -> Unit, onStep: (MenuStep) -> Unit) {
    val graph = LocalGraph.current
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
    val handlers = remember(post, actions, graph) {
        PostMenuHandlers(
            toggleSaved = { actions.keep.toggleSave(graph, post) },
            editNote = { onStep(MenuStep.NOTE) },
            addToGroup = { onStep(MenuStep.GROUPS) },
            chooseFolder = { onStep(MenuStep.FOLDERS) },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            PostMenuRows(menu, saved, handlers, onDone = { onStep(MenuStep.CLOSED) })
        }
    }
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
