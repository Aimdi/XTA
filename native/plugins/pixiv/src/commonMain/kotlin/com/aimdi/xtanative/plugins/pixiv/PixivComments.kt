package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivComment
import com.aimdi.xtanative.networks.pixiv.PixivCommentPart
import com.aimdi.xtanative.networks.pixiv.PixivCommentTarget
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** Whether a comment shows: neither it nor its author is muted. */
internal fun PixivMutes.shows(comment: PixivComment): Boolean = comment.id !in commentIds && (comment.user?.id ?: 0L) !in authorIds

/** Whether a thread's screen leaves: the comment it hangs off is muted now, or its author is. */
internal fun PixivMutes.leavesThread(parent: PixivComment?): Boolean = parent != null && !shows(parent)

/**
 * A work's or a novel's comments, newest first, or the replies to [parent]
 * under it: emoji drawn inline, stickers as pictures, and links leaving
 * Pixiv hidden until the reader asks. Muting a comment or its author hides it.
 */
@Composable
internal fun PixivCommentsScreen(kind: PixivKind, id: Long, parent: PixivComment?) {
    val runtime = LocalPixiv.current
    val target = if (kind == PixivKind.NOVEL) PixivCommentTarget.novel(id) else PixivCommentTarget.illust(id)
    val list = rememberKept("pixiv-comments-${kind.stored}-$id-${parent?.id ?: 0}") { scope ->
        PixivList<PixivComment>(scope, { it.id }, { next -> readComments(runtime, target, parent, next) }) { comments -> comments.filter(runtime.mutes.now::shows) }
    }
    val mutes = pixivMutes()
    LaunchedEffect(mutes, parent) { if (mutes.leavesThread(parent)) runtime.host.back() }
    PluginScaffold(title = pluginString(if (parent == null) Strings.comments_title else Strings.comments_replies_title), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PixivRows(list, { it.id }, pluginString(if (parent == null) Strings.comments_empty else Strings.comments_replies_empty), shown = { it.filter(mutes::shows) }, header = {
                parent?.let { item(key = "parent") { CommentTile(it, kind, id, pinned = true) } }
            }) { comment -> CommentTile(comment, kind, id, pinned = false, reply = parent != null) }
        }
    }
}

private suspend fun readComments(runtime: PixivRuntime, target: PixivCommentTarget, parent: PixivComment?, next: String?): Page<PixivComment> =
    if (parent == null) runtime.comments.comments(target, next).comments else runtime.comments.replies(target, parent.id, next).comments

/**
 * One comment: who wrote it, whom it answers and when, then its words with
 * their emoji and its sticker, or the note hiding a link out of Pixiv
 * (pixiv_comment_tile.dart); a [reply] a step in under the comment it answers.
 */
@Composable
private fun CommentTile(comment: PixivComment, kind: PixivKind, workId: Long, pinned: Boolean, reply: Boolean = false) {
    val runtime = LocalPixiv.current
    val revealed by runtime.revealedComments.collectAsState()
    val hidden = comment.linksOutsidePixiv && comment.id !in revealed
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 22.dp else 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        val user = comment.user
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(enabled = user != null) { user?.let { runtime.openUser(it.id) } }, contentAlignment = Alignment.Center) {
            PixivAvatar(user?.avatarUrl, comment.authorName.orEmpty(), size = 36.dp)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CommentByline(comment)
            if (hidden) {
                HiddenLink { runtime.revealComment(comment.id) }
            } else {
                if (comment.parts.isNotEmpty()) SelectionContainer { CommentText(comment.parts) }
                comment.stampUrl?.let { PixivPicture(it, Modifier.size(100.dp), ContentScale.Fit, pluginString(Strings.comment_sticker)) }
            }
            if (!pinned && comment.hasReplies) {
                TextButton(onClick = { runtime.open(PixivRoute.Comments(kind, workId, runtime.shelf.put(comment))) }) {
                    Icon(PostIcons.Forum, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluginString(Strings.comment_view_replies))
                }
            }
        }
        CommentMenu(comment)
    }
}

/** "Name · To someone · 3h", the name set apart, so a thread reads by speaker. */
@Composable
private fun CommentByline(comment: PixivComment) {
    val language = currentLanguage()
    val name = comment.authorName ?: pluginString(Strings.comment_unknown_user)
    val replyTo = comment.replyTo?.let { it.name.ifEmpty { "@" + it.account } }
    val date = comment.date?.let { agoText(it) }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(name) }
            replyTo?.let { append(" · " + Strings.comment_reply_to.get(language, it)) }
            date?.let { append(" · $it") }
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A comment's words with Pixiv's emoji drawn where their codes were. */
@Composable
private fun CommentText(parts: List<PixivCommentPart>) {
    val emoji = parts.filterIsInstance<PixivCommentPart.Emoji>().distinctBy { it.id }
    val inline = emoji.associate { part ->
        part.code to InlineTextContent(Placeholder(1.4.em, 1.4.em, PlaceholderVerticalAlign.TextCenter)) {
            PixivPicture(part.url, Modifier.fillMaxSize(), ContentScale.Fit, part.code)
        }
    }
    val text = buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is PixivCommentPart.Text -> append(part.text)
                is PixivCommentPart.Emoji -> appendInlineContent(part.code, part.code)
            }
        }
    }
    Text(text, inlineContent = inline, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun HiddenLink(onShow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(GroupIcons.LinkOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(pluginString(Strings.comment_hidden_link), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = onShow) { Text(pluginString(Strings.comment_show)) }
    }
}

/** Mute the comment, or everything its author writes; each asks first. */
@Composable
private fun CommentMenu(comment: PixivComment) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val language = currentLanguage()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.comment_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.comment_mute)) }, leadingIcon = { Icon(PixivIcons.VolumeOff, null) }, onClick = {
                open = false
                sheets.ask.ask(Strings.comment_mute.get(language), Strings.comment_mute.get(language)) { runtime.mutes.muteComment(comment.id) }
            })
            comment.user?.let { user ->
                val label = Strings.comment_mute_user.get(language, comment.authorName ?: user.account)
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(GroupIcons.PersonRemove, null) }, onClick = {
                    open = false
                    sheets.ask.ask(label, label) { runtime.mutes.muteAuthor(user.id, user.name) }
                })
            }
        }
    }
}
