package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside

/**
 * A post in a list, built like a post from X (XTA's `RedditPostCard`): the
 * community's picture in a column of its own, then the community, the
 * author and when, the title and the start of the words, the labels, the
 * media and a row of actions. A tap opens the thread; a long press, or the
 * author, everywhere else the post can lead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RedditPostCard(app: RedditApp, post: RedditPost) {
    var sheet by rememberSaveable(post.id) { mutableStateOf(false) }
    val choices by app.choices.collectAsState()
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { app.openThread(post) }, onLongClick = { sheet = true })) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            SubredditAvatar(app, post.subreddit, 40.dp, onClick = { app.openSubreddit(post.subreddit) })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(post, onSubreddit = { app.openSubreddit(post.subreddit) }, onAuthor = { sheet = true })
                if (post.showsTitle) {
                    Text(post.displayTitle, style = bodyStyle(), maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
                }
                // On a discussion community the words are most of the post: a card with the title alone said almost nothing.
                post.displaySelfText?.takeIf { post.isSelf && post.showsSelfText }?.let { words ->
                    RedditText(words, Modifier.padding(top = 6.dp, end = 8.dp), maxLines = 4)
                }
                Labels(post)
                PostMediaView(app, post, choices, Modifier.padding(top = 10.dp, end = 8.dp))
                CardFooter(app, post)
            }
        }
        Hairline()
    }
    if (sheet) PostSheet(app, post) { sheet = false }
}

@Composable
internal fun bodyStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** "r/community  u/author · 3h": the community and its picture are one target, the author the way to everything else. */
@Composable
private fun AuthorLine(post: RedditPost, onSubreddit: () -> Unit, onAuthor: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Text("r/" + post.subreddit, style = style, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable(onClick = onSubreddit))
        post.author?.let { author ->
            Text("u/$author", style = style, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false).clickable(onClick = onAuthor))
        }
        val ago = agoText(post.createdAt)
        if (ago.isNotEmpty()) Text(" · $ago", style = style, color = muted, maxLines = 1)
    }
}

/** Flair, NSFW and spoiler, read before the picture they qualify. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Labels(post: RedditPost) {
    if (post.flair == null && !post.over18 && !post.spoiler) return
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        post.flair?.let { Badge(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
        if (post.over18) Badge(pluginString(Strings.nsfw), MaterialTheme.colorScheme.error.copy(alpha = 0.14f), readable(MaterialTheme.colorScheme.error))
        if (post.spoiler) Badge(pluginString(Strings.spoiler), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), readable(MaterialTheme.colorScheme.tertiary))
    }
}

@Composable
private fun Badge(label: String, fill: Color, ink: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(fill).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The score with this device's upvote, the comments, saving on this device
 * and the way out to the browser, spread across the column as a post's
 * actions are. Nothing is sent to Reddit: the arrow remembers.
 */
@Composable
private fun CardFooter(app: RedditApp, post: RedditPost) {
    val votes by app.votes.state.collectAsState()
    val archived by app.host.saved.collectAsState()
    val outside = rememberOpenOutside()
    val upvoted = post.id in votes
    val isSaved = RedditArchive.id(post) in archived
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        FooterAction(
            if (upvoted) RedditGlyphs.ArrowCircleUp else RedditGlyphs.ArrowUpward,
            countText((post.score + if (upvoted) 1 else 0).toLong()),
            pluginString(Strings.upvote),
            if (upvoted) accent else null,
        ) { app.upvote(post) }
        FooterAction(RedditGlyphs.Comment, countText(post.commentCount.toLong()), pluginPlural(Strings.comment_count, post.commentCount, post.commentCount)) { app.openThread(post) }
        FooterAction(
            if (isSaved) RedditGlyphs.Bookmark else RedditGlyphs.BookmarkBorder,
            null,
            pluginString(if (isSaved) Strings.remove_from_device else Strings.save_on_device),
            if (isSaved) accent else null,
            onLongClick = { app.chooseFolder(post) },
        ) { app.toggleSaved(post) }
        FooterAction(RedditGlyphs.OpenInNew, null, pluginString(Strings.open_in_browser)) { outside(RedditThreadRules.postUrl(post)) }
    }
}

/** One of a card's actions: its icon, its number when it has one, at a touch target's size; [onLongClick] where a long press does more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterAction(icon: ImageVector, label: String?, description: String, tint: Color? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val colour = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = description, tint = colour, modifier = Modifier.size(18.dp))
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = colour,
                fontWeight = if (tint != null) FontWeight.Bold else null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
