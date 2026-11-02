package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.R
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText

/** One timeline row, or a thread X groups, with the line under it. */
@Composable
fun ThreadRows(thread: XThread, actions: PostActions, pinnedIds: Set<String> = emptySet()) {
    thread.posts.forEachIndexed { index, post ->
        PostCard(post, actions, pinned = post.id in pinnedIds, continues = index < thread.posts.lastIndex)
    }
    Hairline()
}

/**
 * A post as a feed row. A repost shows the post reposted, under a line naming
 * who reposted it, as X does.
 */
@Composable
fun PostCard(post: XPost, actions: PostActions, modifier: Modifier = Modifier, pinned: Boolean = false, continues: Boolean = false) {
    val shown = post.shown
    Column(
        modifier
            .fillMaxWidth()
            .clickable { actions.openPost(shown.id) }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (continues) 4.dp else 12.dp),
    ) {
        when {
            pinned -> ContextLine(XIcons.Pin, stringResource(R.string.pinned))
            post.isRepost -> ContextLine(XIcons.Repost, stringResource(R.string.reposted_by, post.author.name))
        }
        Row {
            Avatar(shown.author.largeAvatarUrl, 40.dp) { actions.openProfile(shown.author.screenName) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(shown.author.name, shown.author.screenName, agoText(shown.createdAt), shown.author.verified)
                ReplyingTo(shown, actions)
                PostText(shown, actions, Modifier.padding(top = 2.dp))
                if (shown.media.isNotEmpty()) {
                    MediaGrid(shown.media, { index -> actions.openMedia(shown.id, index) }, Modifier.padding(top = 10.dp))
                }
                shown.card?.let { CardView(it, Modifier.padding(top = 10.dp)) }
                shown.quoted?.let { QuotedPost(it, actions, Modifier.padding(top = 10.dp)) }
                Counts(shown)
            }
        }
    }
}

@Composable
private fun ContextLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(start = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Replying to @maya", for a reply to someone else. */
@Composable
private fun ReplyingTo(post: XPost, actions: PostActions) {
    val to = post.replyToScreenName ?: return
    if (to.equals(post.author.screenName, ignoreCase = true)) return
    Row(Modifier.padding(top = 2.dp)) {
        Text(stringResource(R.string.replying_to) + " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "@$to",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { actions.openProfile(to) },
        )
    }
}

/** The post a post quotes, framed, opening on its own. */
@Composable
fun QuotedPost(post: XPost, actions: PostActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { actions.openPost(post.id) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.author.avatarUrl, 20.dp)
            Spacer(Modifier.width(6.dp))
            AuthorLine(post.author.name, post.author.screenName, agoText(post.createdAt), post.author.verified, Modifier.weight(1f))
        }
        PostText(post, actions, Modifier.padding(top = 4.dp), maxLines = 8)
        if (post.media.isNotEmpty()) {
            MediaGrid(post.media, { index -> actions.openMedia(post.id, index) }, Modifier.padding(top = 8.dp))
        }
        post.card?.let { CardView(it, Modifier.padding(top = 8.dp)) }
    }
}

/** Replies, reposts, likes and views, as numbers to read: nothing here is sent to X. */
@Composable
private fun Counts(post: XPost) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, end = 24.dp).height(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Count(XIcons.Reply, post.replyCount)
        Count(XIcons.Repost, post.repostCount + post.quoteCount)
        Count(XIcons.Like, post.likeCount)
        Count(XIcons.Views, post.viewCount ?: 0)
    }
}

@Composable
private fun Count(icon: ImageVector, n: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        if (n > 0) {
            Text(
                countText(n),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
