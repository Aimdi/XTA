package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_unread
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's mark in the top corner and the menu (XTA's ChromeAvatarMark):
 * the picture chosen in Settings › Accounts' Your avatar, else the first X
 * account's initial on its colour; a neutral glyph with no account. X
 * accounts carry a name but no picture here, so it never poses as a photo.
 */
@Composable
internal fun AccountMark(account: XAccount?, size: Dp, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val revision by graph.settings.observe(AccountPrefs.avatarRevision).collectAsStateWithLifecycle(graph.settings.state.value?.get(AccountPrefs.avatarRevision) ?: 0)
    val picture = remember(revision) { graph.avatar.pictureAt(revision) }
    var unreadable by remember(picture) { mutableStateOf(false) }
    if (picture != null && !unreadable) {
        val context = LocalPlatformContext.current
        val request = remember(context, picture, revision) {
            ImageRequest.Builder(context).data(picture).memoryCacheKey("chrome-avatar-$revision").diskCachePolicy(CachePolicy.DISABLED).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { if (it is AsyncImagePainter.State.Error) unreadable = true },
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    MonogramMark(account, size, modifier)
}

@Composable
private fun MonogramMark(account: XAccount?, size: Dp, modifier: Modifier) {
    val name = account?.screenName
    if (account == null || name.isNullOrEmpty()) {
        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.size(size))
        return
    }
    LetterMark(Monogram.initial(name, account.id), Color(Monogram.colour(account.id)), size, modifier)
}

/** A group's mark: its emoji, else its initial on its colour. */
@Composable
internal fun GroupMark(group: FollowGroup, size: Dp, modifier: Modifier = Modifier) {
    val emoji = group.emoji?.takeIf { it.isNotBlank() }
    if (emoji == null) {
        LetterMark(Monogram.initial(group.name, group.id), Color(Monogram.colour(group.id)), size, modifier)
        return
    }
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }, maxLines = 1)
    }
}

/** One letter on a disc; the letter keeps its size whatever the text size, or it would leave the disc (as in XTA). */
@Composable
internal fun LetterMark(letter: String, colour: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(colour), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { (size * 0.46f).toSp() }
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
    }
}

/** [content] with a dot while [off] accounts or groups are turned off, XTA's mark on Home feed accounts; said as how many. */
@Composable
internal fun OffDot(off: Int, content: @Composable () -> Unit) {
    if (off <= 0) {
        content()
        return
    }
    val count = off.toString()
    BadgedBox(badge = { Badge(Modifier.semantics { contentDescription = count }) }) { content() }
}

/** [content] with the unread dot at its corner when [unread], said as "Unread". */
@Composable
internal fun UnreadDot(unread: Boolean, content: @Composable () -> Unit) {
    if (!unread) {
        content()
        return
    }
    val label = stringResource(Res.string.home_unread)
    BadgedBox(badge = { Badge(Modifier.semantics { contentDescription = label }) }) { content() }
}
