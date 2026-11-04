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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.saved.SavedIcons

/**
 * A story in a list (hn_story_card.dart): its rank, site, points and age over
 * its title, the one target; then its author, its comments, and the heart and
 * bookmark kept on this device. A long press opens the shared post sheet
 * (plugin_link_post.dart): Saved, its note, its page shared or opened.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HnStoryRow(app: HnApp, story: HnStory, rank: Int? = null) {
    val kept by app.kept.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { app.openStory(story) }, onLongClick = { app.openMenu(story) })
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
}
