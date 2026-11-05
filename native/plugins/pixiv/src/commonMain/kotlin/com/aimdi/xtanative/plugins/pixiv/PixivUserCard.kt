package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivFollowDetail
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.ui.plugins.addToGroupText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.CancellationException

/** How many works or novels a creator's card shows. */
private const val PREVIEWS = 3

/** What Follow takes at least beside Add to group, and the group button's own icon, gap and padding around its name. */
private val FOLLOW_ROOM = 136.dp
private val GROUP_CHROME = 82.dp

/**
 * A profile's Follow across the row and Add to group beside it, named while
 * the name fits beside Follow and its icon otherwise; the reader's own
 * profile has Add to group alone (pixiv_user_header.dart's _ProfileButtons).
 */
@Composable
internal fun ProfileButtons(user: PixivUser) {
    val runtime = LocalPixiv.current
    val self = user.id == runtime.ownId
    val label = addToGroupText()
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val named = self || maxWidth >= FOLLOW_ROOM + GROUP_CHROME + with(density) { measurer.measure(label, style).size.width.toDp() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!self) FollowButton(user, Modifier.weight(1f), prominent = true)
            ProfileGroupButton(label, named) { runtime.addToGroup(user) }
        }
    }
}

/** Add to group as the profile draws it, outlined with an edge: [label] beside its icon when [named], its icon alone otherwise. */
@Composable
private fun ProfileGroupButton(label: String, named: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (named) {
        OutlinedButton(onClick = onClick, shape = shape, border = border, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(PostIcons.People, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedIconButton(onClick = onClick, shape = shape, border = border, modifier = Modifier.size(48.dp)) { Icon(PostIcons.People, label) }
    }
}

/** Add to group in a creator's row (pixiv_user_list_screen.dart), followed here once a group is picked. */
@Composable
internal fun GroupIconButton(user: PixivUser) {
    val runtime = LocalPixiv.current
    IconButton(onClick = { runtime.addToGroup(user) }) { Icon(PostIcons.People, addToGroupText()) }
}

/**
 * Follow or Unfollow on Pixiv (pixiv_user_card.dart's PixivFollowButton): a
 * tap follows publicly or unfollows, a long press opens the follow dialog to
 * follow privately; a ring while Pixiv answers. [prominent] is the profile's
 * full-width button, outlined once followed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FollowButton(user: PixivUser, modifier: Modifier = Modifier, prominent: Boolean = false) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val marks by runtime.following.marks.state.collectAsState()
    val followed = marks.on(user.id, user.isFollowed)
    val busy = marks.isBusy(user.id)
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when {
        !prominent -> scheme.secondaryContainer to scheme.onSecondaryContainer
        followed -> Color.Transparent to scheme.primary
        else -> scheme.primary to scheme.onPrimary
    }
    val shape = if (prominent) RoundedCornerShape(8.dp) else CircleShape
    Surface(
        shape = shape,
        color = background,
        contentColor = content,
        border = if (prominent && followed) BorderStroke(1.dp, scheme.outline) else null,
        modifier = modifier.heightIn(min = 48.dp).clip(shape).combinedClickable(
            enabled = !busy,
            role = Role.Button,
            onLongClickLabel = pluginString(Strings.follow_privately),
            onClick = { sheets.toggleFollow(user, haptics) },
            onLongClick = {
                haptics.medium()
                sheets.show(PixivSheet.Follow(user))
            },
        ),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
            } else {
                Icon(if (followed) GroupIcons.PersonRemove else GroupIcons.PersonAddOutlined, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(pluginString(if (followed) Strings.unfollow else Strings.follow), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
internal fun UserPreviewCard(preview: PixivUserPreview, novels: Boolean = false, grouped: Boolean = false) {
    val runtime = LocalPixiv.current
    val user = preview.user
    OutlinedCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runtime.openUser(user.id) }) {
        Column(Modifier.padding(12.dp)) {
            UserHeader(user, grouped)
            PreviewRow(preview, novels)
        }
    }
}

/** Who the card is about beside Follow and, when [grouped], Add to group: side by side, or the buttons under them on a narrow card. */
@Composable
private fun UserHeader(user: PixivUser, grouped: Boolean) {
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            PixivAvatar(user.avatarUrl, user.name)
            Spacer(Modifier.width(12.dp))
            UserNames(user.name, user.account, Modifier.weight(1f))
        }
    }
    val self = user.id == LocalPixiv.current.ownId
    if (self && !grouped) return identity(Modifier.fillMaxWidth())
    val buttons: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!self) FollowButton(user)
            if (grouped) GroupIconButton(user)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 300.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                identity(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                buttons()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                identity(Modifier.fillMaxWidth())
                buttons()
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
internal fun PixivUsersFeed(list: PixivList<PixivUserPreview>, empty: String, padding: PaddingValues = PaddingValues(), toTop: Int = 0, novels: Boolean = false, grouped: Boolean = false) {
    val mutes = pixivMutes()
    PixivRows(list, { it.user.id }, empty, padding, toTop, shown = { previews -> mutes.withoutMutedAuthors(previews) { it.user.id } }, divided = false) { preview ->
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) { UserPreviewCard(preview, novels, grouped) }
    }
}

/**
 * The follow dialog (pixiv_follow_dialog.dart): how the reader follows [user]
 * on Pixiv, read first; Follow privately, which starts on for a creator not
 * followed yet (a tap already follows publicly), then Unfollow while
 * followed, Cancel, and Follow or Save.
 */
@Composable
internal fun FollowDialog(user: PixivUser, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    val haptics = rememberHaptics()
    var attempt by remember { mutableIntStateOf(0) }
    var detail by remember(user.id) { mutableStateOf<PixivFollowDetail?>(null) }
    var failure by remember(user.id) { mutableStateOf<Throwable?>(null) }
    var private by remember(user.id) { mutableStateOf(true) }
    LaunchedEffect(user.id, attempt) {
        failure = null
        try {
            val read = runtime.social.followDetail(user.id)
            private = if (read.isFollowed) read.isPrivate else true
            detail = read
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
        }
    }
    val read = detail
    AlertDialog(
        onDismissRequest = sheets::close,
        title = { Text(user.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            when {
                read != null -> SwitchRow(pluginString(Strings.follow_privately), pluginString(Strings.follow_privately_hint), private) { private = it }
                failure != null -> Column {
                    Text(pixivErrorText(failure))
                    TextButton(onClick = { attempt++ }) { Text(pluginString(Strings.retry)) }
                }
                else -> Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        },
        confirmButton = {
            Button(onClick = { sheets.answerFollow(user, if (private) PixivRestrict.PRIVATE else PixivRestrict.PUBLIC, haptics) }, enabled = read != null) {
                Text(pluginString(if (read?.isFollowed == true) Strings.save else Strings.follow))
            }
        },
        dismissButton = {
            if (read?.isFollowed == true) {
                TextButton(onClick = { sheets.answerFollow(user, null, haptics) }) { Text(pluginString(Strings.unfollow), color = MaterialTheme.colorScheme.error) }
            }
            TextButton(onClick = sheets::close) { Text(pluginString(Strings.cancel)) }
        },
    )
}
