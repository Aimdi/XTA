package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.PluginNoteEditor
import com.aimdi.xtanative.ui.plugins.PluginNoteMenuRow
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.Keeping
import com.aimdi.xtanative.ui.saved.PostKeep
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.saved.isSaved

/**
 * A story in a list (hn_story_card.dart): its rank, site, points and age over
 * its title, the one target; then its author, its comments, and the heart and
 * bookmark kept on this device. A long press offers Saved's actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HnStoryRow(app: HnApp, story: HnStory, rank: Int? = null) {
    val kept by app.kept.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { app.openStory(story) }, onLongClick = { menu = true })
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        ) {
            val meta = listOfNotNull(rank?.toString(), story.host, pluginPlural(Strings.points, story.score, story.score), agoText(story.createdAt).ifEmpty { null })
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                story.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            story.author?.let { author -> TextButton(onClick = { app.openUser(author) }) { Text(pluginString(Strings.by, author)) } }
            TextButton(onClick = { app.openStory(story) }) {
                Icon(XIcons.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(pluginPlural(Strings.comment_count, story.commentCount, story.commentCount))
            }
            val liked = kept.isLiked(story)
            IconButton(onClick = { app.toggleLike(story) }) {
                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, pluginString(if (liked) Strings.unlike else Strings.like), modifier = Modifier.size(20.dp))
            }
            val saved = kept.isSaved(story)
            IconButton(onClick = { app.toggleSaved(story) }) {
                Icon(if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder, pluginString(if (saved) Strings.unsave else Strings.save), modifier = Modifier.size(20.dp))
            }
        }
        Hairline()
    }
    if (menu) StoryActionsSheet(story) { menu = false }
}

/**
 * What a long press on a story offers (plugin_link_post.dart): keep it in
 * Saved on this device or take it out, a note on it there, share its page,
 * file it in a folder, or open it in the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryActionsSheet(story: HnStory, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val openOutside = rememberOpenOutside()
    val share = rememberShareText()
    val item = remember(story) { Keepable(HnArchive.id(story), story.author, HnArchive.content(story)) }
    val saved = PostKeep().isSaved(item.id)
    var folders by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    if (folders) {
        SaveSheet(item, onDismiss)
        return
    }
    if (noting) {
        PluginNoteEditor(item, onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            val then = { action: () -> Unit -> { onDismiss(); action() } }
            PluginMenuRow(if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder, pluginString(if (saved) Strings.unsave else Strings.save), then { Keeping.toggleSave(graph, item) })
            PluginNoteMenuRow({ noting = true })
            PluginMenuRow(Icons.Filled.Share, pluginString(Strings.share_link), then { share(story.hnUrl) })
            PluginMenuRow(SavedIcons.Folder, pluginString(Strings.save_to_folder), { folders = true })
            PluginMenuRow(PostIcons.OpenInNew, pluginString(Strings.open_in_browser), then { openOutside(story.hnUrl) })
        }
    }
}
