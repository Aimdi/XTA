package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** How many works or novels a creator's card shows. */
private const val PREVIEWS = 3

/**
 * Follow or Unfollow on this device: the creator's works join Following's
 * "On this device" and Pixiv is never told. [prominent] is the profile's
 * full-width button, outlined once followed. Whether the reader follows them
 * on Pixiv itself shows under it.
 */
@Composable
internal fun FollowButton(user: PixivUser, modifier: Modifier = Modifier, prominent: Boolean = false) {
    val runtime = LocalPixiv.current
    val haptics = rememberHaptics()
    val followed = PixivFollows.isFollowed(pixivSettings(), user.id)
    val toggle = {
        runtime.toggleFollow(user)
        haptics.light()
    }
    val label: @Composable () -> Unit = {
        Icon(if (followed) GroupIcons.PersonRemove else GroupIcons.PersonAddOutlined, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluginString(if (followed) Strings.unfollow else Strings.follow), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val shape = RoundedCornerShape(8.dp)
    val size = if (prominent) Modifier.fillMaxWidth().heightIn(min = 48.dp) else Modifier.heightIn(min = 48.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            !prominent -> FilledTonalButton(onClick = toggle, modifier = size) { label() }
            followed -> OutlinedButton(onClick = toggle, shape = shape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = size) { label() }
            else -> Button(onClick = toggle, shape = shape, modifier = size, colors = ButtonDefaults.buttonColors()) { label() }
        }
        if (user.isFollowed) {
            Text(pluginString(Strings.followed_on_pixiv), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A creator's name over their @account. */
@Composable
internal fun UserNames(name: String, account: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (account.isNotEmpty()) {
            Text("@$account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A creator, their Follow button beside them while there is room (under them when not), then [novels] or works of theirs. */
@Composable
internal fun UserPreviewCard(preview: PixivUserPreview, novels: Boolean = false) {
    val runtime = LocalPixiv.current
    val user = preview.user
    OutlinedCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runtime.openUser(user.id) }) {
        Column(Modifier.padding(12.dp)) {
            UserHeader(user)
            PreviewRow(preview, novels)
        }
    }
}

@Composable
private fun UserHeader(user: PixivUser) {
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            PixivAvatar(user.avatarUrl, user.name)
            Spacer(Modifier.width(12.dp))
            UserNames(user.name, user.account, Modifier.weight(1f))
        }
    }
    if (user.id == LocalPixiv.current.ownId) return identity(Modifier.fillMaxWidth())
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 300.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                identity(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FollowButton(user)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                identity(Modifier.fillMaxWidth())
                FollowButton(user)
            }
        }
    }
}

@Composable
private fun PreviewRow(preview: PixivUserPreview, novels: Boolean) {
    val runtime = LocalPixiv.current
    val mutes = pixivMutes()
    val shownNovels = if (novels) mutes.filterNovels(preview.novels).take(PREVIEWS) else emptyList()
    val works = if (shownNovels.isEmpty()) mutes.filter(preview.illusts).take(PREVIEWS) else emptyList()
    if (shownNovels.isEmpty() && works.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        repeat(PREVIEWS) { index ->
            Column(Modifier.weight(1f)) {
                when {
                    shownNovels.isNotEmpty() -> shownNovels.getOrNull(index)?.let { PreviewNovel(it) }
                    else -> works.getOrNull(index)?.let { PreviewWork(it) { runtime.openWork(works, index) } }
                }
            }
        }
    }
}

@Composable
private fun PreviewWork(work: PixivIllust, onOpen: () -> Unit) {
    PixivPicture(
        work.thumbnailUrl,
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpen),
        contentDescription = work.title,
    )
}

@Composable
private fun PreviewNovel(novel: PixivNovel) {
    val runtime = LocalPixiv.current
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { runtime.openNovel(novel) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) { NovelCover(novel.coverUrl, maxWidth) }
        Text(novel.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A paged list of creators as cards, without those the reader muted. */
@Composable
internal fun PixivUsersFeed(list: PixivList<PixivUserPreview>, empty: String, padding: PaddingValues = PaddingValues(), toTop: Int = 0, novels: Boolean = false) {
    val mutes = pixivMutes()
    PixivRows(list, { it.user.id }, empty, padding, toTop, shown = { previews -> mutes.withoutMutedAuthors(previews) { it.user.id } }, divided = false) { preview ->
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) { UserPreviewCard(preview, novels) }
    }
}
