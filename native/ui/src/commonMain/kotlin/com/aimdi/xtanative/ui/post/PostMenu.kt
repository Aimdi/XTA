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
import com.aimdi.xtanative.data.plugins.PluginSaves
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_add_to_group
import com.aimdi.xtanative.ui.generated.resources.posts_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.posts_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_reposted_by
import com.aimdi.xtanative.ui.pluginstore.PluginSheetEntries
import com.aimdi.xtanative.ui.profile.AuthorGroupsSheet
import org.jetbrains.compose.resources.stringResource

/** What a post's ⋯ menu offers, in XTA's order (plugin_post_actions.dart); none of it writes to X. */
enum class PostMenuEntry { ADD_TO_GROUP, REPOSTED_BY, QUOTES, OPEN_IN_BROWSER }

/**
 * The post's ⋯ menu as a sheet: file its author in groups here, see who
 * reposted it and what quotes it, or open it in the browser; then what the
 * running plugins add, as XTA's share sheet ends with them (sending its link
 * to the reader's own bookmarks). Saving, notes and sharing join it from the
 * screens that keep them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostMenu(post: XPost, actions: PostActions, onDismiss: () -> Unit) {
    val shown = post.shown
    var groups by rememberSaveable { mutableStateOf(false) }
    if (groups) {
        AuthorGroupsSheet(shown.author, onDismiss = onDismiss)
        return
    }
    val prefs = postScope().prefs
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            PostMenuEntry.entries.forEach { entry ->
                when (entry) {
                    PostMenuEntry.ADD_TO_GROUP -> MenuRow(PostIcons.People, stringResource(Res.string.posts_add_to_group)) { groups = true }
                    PostMenuEntry.REPOSTED_BY -> MenuRow(XIcons.Repost, stringResource(Res.string.posts_reposted_by)) {
                        onDismiss()
                        actions.openReposters(shown.id)
                    }
                    PostMenuEntry.QUOTES -> MenuRow(PostIcons.Quote, stringResource(Res.string.posts_quotes)) {
                        onDismiss()
                        actions.openQuotes(shown.id)
                    }
                    PostMenuEntry.OPEN_IN_BROWSER -> MenuRow(PostIcons.OpenInNew, stringResource(Res.string.posts_open_in_browser)) {
                        onDismiss()
                        actions.openInBrowser(PostView.url(post, prefs))
                    }
                }
            }
            PluginSheetEntries(remember(post, prefs) { PluginSaves.shared(post, PostView.url(post, prefs)) }, onDismiss)
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
