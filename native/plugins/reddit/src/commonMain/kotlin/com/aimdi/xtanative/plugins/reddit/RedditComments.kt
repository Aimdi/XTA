package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditCommentSort
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside

/** Reddit's orange for an upvote. */
private val Upvote = Color(0xFFFF8B4D)

/** How far a reply sits in per level ([RedditThreadRules.LINE_COLUMN_DP]). */
private val LineColumn = RedditThreadRules.LINE_COLUMN_DP.dp

/** Every target in a thread ([RedditThreadRules.TOUCH_TARGET_DP]). */
private val Target = RedditThreadRules.TOUCH_TARGET_DP.dp

/** A thread's body text: 15 on 22. */
@Composable
internal fun threadBodyStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)

/** The lines of the levels above a reply, a column each, with [content] after them. */
@Composable
private fun ThreadLines(depth: Int, content: @Composable () -> Unit) {
    val lines = RedditThreadRules.lines(depth)
    val page = MaterialTheme.colorScheme.background.toArgb()
    val colours = (0 until lines).map { Color(RedditThreadRules.lineColour(it, page)) }
    Box(
        Modifier.fillMaxWidth().drawBehind {
            colours.forEachIndexed { level, colour ->
                val centre = (16.dp + LineColumn * level + LineColumn / 2).toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - centre else centre
                drawRoundRect(colour, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), CornerRadius(1.dp.toPx()))
            }
        }.padding(start = 16.dp + LineColumn * lines, end = 8.dp),
    ) { content() }
}

/**
 * A comment in its thread (XTA's `RedditCommentRow`): its header, which
 * folds it, and unless folded its words, pictures and upvote. A top-level
 * comment after the first has a hairline above it.
 */
@Composable
internal fun CommentRow(app: RedditApp, row: VisibleComment, folded: Boolean, divided: Boolean, isNew: Boolean, onToggle: () -> Unit) {
    val comment = row.entry.comment
    Column(Modifier.fillMaxWidth()) {
        if (divided) HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        ThreadLines(row.entry.depth) {
            Column(Modifier.padding(top = if (row.entry.depth == 0) 8.dp else 0.dp)) {
                CommentHeader(app, comment, folded, isNew, onToggle)
                if (!folded) CommentContent(app, comment)
            }
        }
    }
}

@Composable
private fun CommentContent(app: RedditApp, comment: RedditComment) {
    if (comment.isRemoved) {
        Text(
            removedText(comment),
            style = threadBodyStyle().copy(fontSize = 14.sp, lineHeight = 20.sp),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, bottom = 12.dp),
        )
        return
    }
    if (comment.body.isNotEmpty()) RedditText(comment.body, style = threadBodyStyle())
    CommentImages(app, comment.mediaUrls)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val id = RedditVotes.commentId(comment.id)
        UpvoteButton(app, id, comment.score, filled = false)
    }
}

@Composable
private fun removedText(comment: RedditComment): String = pluginString(
    when (RedditThreadRules.removed(comment)) {
        Removed.BY_MODERATORS -> Strings.comment_removed
        Removed.BY_REDDIT -> Strings.comment_removed_by_reddit
        Removed.UNAVAILABLE -> Strings.comment_unavailable
        Removed.DELETED -> Strings.comment_deleted
    },
)

/** The row that folds and unfolds a comment: avatar, name, OP, flair, age or "new", and folded, how many replies it holds; its menu at the end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentHeader(app: RedditApp, comment: RedditComment, folded: Boolean, isNew: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            Modifier.weight(1f).heightIn(min = Target).clickable(onClick = onToggle).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Name(comment, muted = folded || comment.isRemoved)
            if (comment.isSubmitter) Chip(pluginString(Strings.op), ChipKind.OP)
            comment.authorFlair?.takeIf { !comment.isRemoved }?.let { Chip(it, ChipKind.FLAIR) }
            Age(comment, isNew)
            if (folded && comment.replyCount > 0) Chip(pluginPlural(Strings.folded_replies, comment.replyCount, comment.replyCount), ChipKind.REPLIES)
        }
        if (!comment.isRemoved) CommentMenu(app, comment)
    }
}

@Composable
private fun Name(comment: RedditComment, muted: Boolean) {
    val author = comment.author
    val colour = when {
        comment.isSubmitter -> MaterialTheme.colorScheme.primary
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 22.dp)) {
        FaceAvatar(author?.takeIf { comment.hasAuthor }, 20.dp, round = comment.isSubmitter)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                author == null -> ""
                comment.hasAuthor -> "u/$author"
                else -> author
            },
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Age(comment: RedditComment, isNew: Boolean) {
    val created = comment.createdAt ?: return
    val age = agoText(created)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 22.dp)) {
        if (!isNew) {
            Text(age, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val accent = MaterialTheme.colorScheme.primary
            Spacer(Modifier.size(7.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(pluginString(Strings.comment_new, age), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent)
        }
    }
}

/** A small label beside a commenter's name: OP, their flair, or how many replies a fold holds. */
private enum class ChipKind { OP, FLAIR, REPLIES }

@Composable
private fun Chip(label: String, kind: ChipKind) {
    val accent = MaterialTheme.colorScheme.primary
    val (fill, ink) = when (kind) {
        ChipKind.OP -> accent.copy(alpha = 0.16f) to accent
        ChipKind.FLAIR -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        ChipKind.REPLIES -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        fontSize = if (kind == ChipKind.OP) 11.sp else 12.sp,
        fontWeight = if (kind == ChipKind.OP) FontWeight.Bold else FontWeight.Medium,
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(if (kind == ChipKind.REPLIES) 10.dp else 6.dp)).background(fill)
            .padding(horizontal = if (kind == ChipKind.REPLIES) 8.dp else 6.dp, vertical = 2.dp),
    )
}

/** A comment's ⋮: its author's posts, its words copied, its link shared or opened. */
@Composable
private fun CommentMenu(app: RedditApp, comment: RedditComment) {
    var open by remember { mutableStateOf(false) }
    val url = RedditThreadRules.commentUrl(comment)
    val clipboard = LocalClipboardManager.current
    val share = rememberShareLink()
    val outside = rememberOpenOutside()
    if (!comment.hasAuthor && comment.body.isEmpty() && url == null) return
    Box {
        IconButton(onClick = { open = true }) {
            Icon(RedditGlyphs.MoreHoriz, contentDescription = pluginString(Strings.more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            comment.author?.takeIf { comment.hasAuthor }?.let { author -> MenuItem(Icons.Outlined.Person, "u/$author", close) { app.openUser(author) } }
            if (comment.body.isNotEmpty()) MenuItem(RedditGlyphs.Copy, pluginString(Strings.copy), close) { clipboard.setText(AnnotatedString(comment.body)) }
            if (url != null) {
                MenuItem(Icons.Outlined.Share, pluginString(Strings.share_link), close) { share(url) }
                MenuItem(RedditGlyphs.OpenInNew, pluginString(Strings.open_in_browser), close) { outside(url) }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = {
        close()
        action()
    })
}

/**
 * The up arrow and the score with this device's vote, in Reddit's orange
 * once upvoted (XTA's `RedditUpvoteButton`); [filled] as a pill under the
 * post, else a quiet button in a comment; [onToggle] turns it. Reddit is never told.
 */
@Composable
internal fun UpvoteButton(app: RedditApp, id: String, score: Int?, filled: Boolean, onToggle: () -> Unit = { app.toggleVote(id) }) {
    val votes by app.votes.state.collectAsState()
    val upvoted = id in votes
    val idle = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val colour = if (upvoted) readable(Upvote) else idle
    Pill(filled = filled, onClick = onToggle, modifier = Modifier.semantics { toggleableState = ToggleableState(upvoted) }) {
        Icon(RedditGlyphs.ArrowUpwardRounded, contentDescription = pluginString(Strings.upvote), tint = if (upvoted) colour else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(if (filled) 20.dp else 18.dp))
        if (score != null) {
            Spacer(Modifier.width(6.dp))
            Text(countText(RedditThreadRules.shownScore(score, upvoted).toLong()), fontSize = if (filled) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = colour)
        }
    }
}

/** A rounded touch target the thread's actions share: filled, a pill under the post; unfilled, a quiet button in a comment. */
@Composable
internal fun Pill(filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        modifier = modifier.heightIn(min = Target).widthIn(min = Target),
    ) {
        Row(
            Modifier.heightIn(min = Target).padding(horizontal = if (filled) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/**
 * Where Reddit held comments back, at their depth: accent words that load
 * them here, or continue the thread on its own page or on Reddit.
 */
@Composable
internal fun HeldBackRow(depth: Int, action: HeldBack, loading: Boolean, onTap: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    ThreadLines(depth) {
        TextButton(onClick = onTap, enabled = !loading && action != HeldBack.NOTHING, modifier = Modifier.heightIn(min = Target)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                pluginString(if (action == HeldBack.LOAD_HERE) Strings.load_more_comments else Strings.continue_thread),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            val trailing = when (action) {
                HeldBack.OPEN_ON_REDDIT -> RedditGlyphs.OpenInNew
                HeldBack.OPEN_SUBTREE -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                else -> null
            }
            if (trailing != null && !loading) Icon(trailing, contentDescription = null, tint = accent, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

/** Above the comments: how many there are, the order they are in, and Collapse all or Expand all when any top-level comment has replies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentsHeader(count: Int, sort: RedditCommentSort, onSort: (RedditCommentSort) -> Unit, allFolded: Boolean?, onToggleAll: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                pluginPlural(Strings.comment_count, count, count),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SortMenu(sort, onSort)
                if (allFolded != null) {
                    TextButton(onClick = onToggleAll) {
                        Text(pluginString(if (allFolded) Strings.expand_all else Strings.collapse_all), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: RedditCommentSort, onSort: (RedditCommentSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(RedditGlyphs.Sort, contentDescription = pluginString(Strings.sort), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(commentSortLabel(sort), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RedditCommentSort.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(commentSortLabel(choice)) },
                    trailingIcon = if (choice == sort) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                    onClick = {
                        open = false
                        if (choice != sort) onSort(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun commentSortLabel(sort: RedditCommentSort): String = pluginString(
    when (sort) {
        RedditCommentSort.BEST -> Strings.sort_best
        RedditCommentSort.TOP -> Strings.sort_top
        RedditCommentSort.NEW -> Strings.sort_new
        RedditCommentSort.CONTROVERSIAL -> Strings.sort_controversial
        RedditCommentSort.OLD -> Strings.sort_old
        RedditCommentSort.QA -> Strings.sort_qa
    },
)

/** The round button that jumps to the next top-level comment. */
@Composable
internal fun JumpButton(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(RedditGlyphs.JumpDown, contentDescription = pluginString(Strings.jump_next_comment), modifier = Modifier.size(22.dp))
        }
    }
}
