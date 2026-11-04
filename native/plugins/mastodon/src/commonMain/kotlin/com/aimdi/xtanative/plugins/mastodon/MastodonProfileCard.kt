package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.mastodon.MastodonField
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.networks.mastodon.MastodonUrls
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.monthText
import com.aimdi.xtanative.ui.plugins.LinkedText
import com.aimdi.xtanative.ui.plugins.PluginCounts
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * A profile's card, as XTA's MastodonProfileCard: its header, picture, name,
 * address, Bot and Locked, bio, fields (a verified link marked), when it
 * joined, its counts, and Follow, which follows it here only.
 */
@Composable
internal fun ProfileCard(app: MastodonApp, profile: MastodonProfile, following: Boolean, onFollow: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        MastodonUrls.web(profile.headerUrl)?.let { header ->
            AsyncImage(
                model = header,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.height(16.dp))
        }
        Names(profile)
        if (profile.note.isNotBlank()) {
            val note = remember(profile) { runs(profile) }
            SelectionContainer(Modifier.padding(top = 14.dp)) { LinkedText(note, app::openProfile, app::openTag) }
        }
        if (profile.fields.isNotEmpty()) Spacer(Modifier.height(12.dp))
        profile.fields.forEach { FieldRow(it) }
        profile.createdAt?.let { joined ->
            Text(
                pluginString(Strings.joined, monthText(joined)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        PluginCounts(
            listOf(
                profile.followersCount to pluginString(Strings.followers),
                profile.followingCount to pluginString(Strings.following),
                profile.statusesCount to pluginString(Strings.posts),
            ),
            Modifier.padding(top = 14.dp),
        )
        FilledTonalButton(onClick = onFollow, modifier = Modifier.padding(top = 18.dp)) {
            Icon(if (following) MastodonIcons.PersonRemove else MastodonIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(if (following) Strings.unfollow else Strings.follow))
        }
    }
}

@Composable
private fun Names(profile: MastodonProfile) {
    Row(verticalAlignment = Alignment.Top) {
        Avatar(MastodonUrls.web(profile.avatarUrl), 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SelectionContainer { Text("@" + profile.acct, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val marks = listOfNotNull(Strings.bot.takeIf { profile.bot }, Strings.locked.takeIf { profile.locked }).map { pluginString(it) }
            if (marks.isNotEmpty()) {
                Text(
                    marks.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A field of the profile: its name over its value, a link opening in the browser, a verified one marked. */
@Composable
private fun FieldRow(field: MastodonField) {
    val openOutside = rememberOpenOutside()
    val url = field.url
    val verified = url != null && field.verifiedAt != null
    val shape = RoundedCornerShape(8.dp)
    val ground = if (verified) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape)
            .background(ground)
            .let { if (url != null) it.clickable { openOutside(url) } else it }
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(field.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                field.value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (url == null) Color.Unspecified else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        when {
            verified -> Icon(XIcons.Verified, pluginString(Strings.verified_link), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            url != null -> Icon(PostIcons.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** A row of the Media grid: [posts] as tiles, [columns] to a row. */
@Composable
internal fun MediaGridRow(app: MastodonApp, posts: List<MastodonPost>, columns: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        posts.forEach { MediaTile(app, it, Modifier.weight(1f).aspectRatio(1f)) }
        repeat(columns - posts.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * A post's first picture, as XTA's MastodonMediaTile: behind its warning
 * when it has one; a video, or a warned post, opens the post, a picture the
 * viewer. Its description is a tap away, and more pictures are marked.
 */
@Composable
private fun MediaTile(app: MastodonApp, post: MastodonPost, modifier: Modifier) {
    val first = post.media.first()
    val hidden = post.sensitive || post.hasSpoiler
    var describing by remember { mutableStateOf(false) }
    val open = { if (hidden || first.isVideo) app.openThread(post) else app.host.openMedia(post.media.map { it.toMediaItem(post.url) }, 0) }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = open)) {
        if (hidden) {
            Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(PostIcons.VisibilityOff, contentDescription = null)
                Text(pluginString(Strings.content_warning), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            AsyncImage(model = first.url, contentDescription = first.description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (first.isVideo) PlayBadge(Modifier.align(Alignment.Center))
            if (!first.description.isNullOrBlank()) {
                TextButton(onClick = { describing = true }, modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(pluginString(Strings.alt_badge), color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.87f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp))
                }
            }
        }
        if (post.media.size > 1) {
            Icon(
                MastodonIcons.Collections,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(4.dp)).padding(4.dp).size(18.dp),
            )
        }
    }
    if (describing) {
        AlertDialog(
            onDismissRequest = { describing = false },
            title = { Text(pluginString(Strings.alt_text)) },
            text = { Text(first.description.orEmpty()) },
            confirmButton = { TextButton(onClick = { describing = false }) { Text(pluginString(Strings.close)) } },
        )
    }
}

@Composable
private fun PlayBadge(modifier: Modifier) {
    Box(modifier.size(44.dp).background(Color.Black.copy(alpha = 0.54f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
