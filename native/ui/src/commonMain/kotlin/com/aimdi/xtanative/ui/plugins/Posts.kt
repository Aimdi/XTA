package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.data.plugins.keepable
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.RollingCount
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.pinned
import com.aimdi.xtanative.ui.generated.resources.plugin_post_show
import com.aimdi.xtanative.ui.generated.resources.plugin_stat_replies
import com.aimdi.xtanative.ui.generated.resources.replying_to
import com.aimdi.xtanative.ui.generated.resources.reposted_by
import com.aimdi.xtanative.ui.generated.resources.stat_likes
import com.aimdi.xtanative.ui.generated.resources.stat_quotes
import com.aimdi.xtanative.ui.generated.resources.stat_reposts
import com.aimdi.xtanative.ui.post.AuthorLine
import com.aimdi.xtanative.ui.post.CardView
import com.aimdi.xtanative.ui.saved.KeepableSaveButton
import org.jetbrains.compose.resources.stringResource

/**
 * Text with its links, mentions and tags in the theme's blue, as the app
 * draws X's: a link opens through the app's link handling, a mention calls
 * [onMention] with whom it names, a tag [onTag] with its query.
 */
@Composable
fun LinkedText(
    runs: List<TextRun>,
    onMention: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(runs, linkColor, onMention, onTag) { annotated(runs, linkColor, onMention, onTag) }
    if (text.isEmpty()) return
    Text(text, modifier = modifier, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

private fun annotated(runs: List<TextRun>, linkColor: Color, onMention: (String) -> Unit, onTag: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        val styles = TextLinkStyles(SpanStyle(color = linkColor))
        for (run in runs) {
            when (run) {
                is TextRun.Plain -> append(run.text)
                is TextRun.Link -> withLink(LinkAnnotation.Url(run.url, styles)) { append(run.text) }
                is TextRun.Mention -> withLink(LinkAnnotation.Clickable("@" + run.who, styles) { onMention(run.who) }) { append(run.text) }
                is TextRun.Tag -> withLink(LinkAnnotation.Clickable(run.query, styles) { onTag(run.query) }) { append(run.text) }
            }
        }
    }

/**
 * What a network adds to the shared row beyond its [NetworkPost], each
 * optional: [repostLine] over the row in the network's own words ("Alice
 * boosted"), in place of "Reposted by", opening [onRepostLine]; [timeNote]
 * after the post's time ("Edited"); [above] over its text (a content warning
 * that holds the rest back); [below] under its media, card and quote (a
 * poll's results), held back with them by a warning that covers the post's
 * text ([NetworkPost.coversText]); [footer] in place of the counts (the network's own
 * buttons); [trailing] at the end of the author line (a follow button, see
 * [NetworkHeaderButton]); and what a long press on the row does.
 */
class NetworkPostExtras(
    val trailing: (@Composable () -> Unit)? = null,
    val repostLine: String? = null,
    val onRepostLine: (() -> Unit)? = null,
    val timeNote: String? = null,
    val above: (@Composable () -> Unit)? = null,
    val below: (@Composable () -> Unit)? = null,
    val footer: (@Composable () -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
) {
    companion object {
        val None = NetworkPostExtras()
    }
}

/**
 * A post as a list row, as the app draws X's: who reposted or pinned it, the
 * author, whom it answers, its title and text, media, link card and quoted
 * post, and its counts, ending with the bookmark of a post the archive keeps
 * ([NetworkPostActions.archive]); a long press opens its menu
 * ([NetworkPostActions.more]). [continues] draws it as part of a thread going
 * on below; [indent] moves a reply under the one it answers; with [playing],
 * its first video plays in place. What a network adds to the row (its own
 * glyph on the author line, its own row in place of the counts, a long
 * press) comes in [NetworkPostExtras].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkPostCard(
    post: NetworkPost,
    actions: NetworkPostActions,
    modifier: Modifier = Modifier,
    continues: Boolean = false,
    indent: Int = 0,
    playing: Boolean = false,
) = NetworkPostCard(post, actions, NetworkPostExtras.None, modifier, continues, indent, playing)

/**
 * [NetworkPostCard] with what the network adds to it ([extras]); [mark] in
 * [markColor] on the avatar says which network it comes from, as a timeline
 * mixing networks shows it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkPostCard(
    post: NetworkPost,
    actions: NetworkPostActions,
    extras: NetworkPostExtras,
    modifier: Modifier = Modifier,
    continues: Boolean = false,
    indent: Int = 0,
    playing: Boolean = false,
    mark: ImageVector? = null,
    markColor: Color = Color.Unspecified,
) = NetworkPostCard(post, actions, extras, modifier, continues, indent, playing, mark, markColor, markLabel = null)

/**
 * [NetworkPostCard] whose [mark] a screen reader hears as [markLabel], the
 * network's name, as XTA's provenance badge is heard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkPostCard(
    post: NetworkPost,
    actions: NetworkPostActions,
    extras: NetworkPostExtras,
    modifier: Modifier = Modifier,
    continues: Boolean = false,
    indent: Int = 0,
    playing: Boolean = false,
    mark: ImageVector? = null,
    markColor: Color = Color.Unspecified,
    markLabel: String?,
) {
    var revealed by rememberSaveable(post.id) { mutableStateOf(false) }
    val more = actions.more
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { actions.open(post) }, onLongClick = extras.onLongPress ?: more?.let { open -> { open(post) } })
            .padding(start = 16.dp + IndentStep * indent.coerceIn(0, MAX_INDENT), end = 16.dp, top = 12.dp, bottom = if (continues) 4.dp else 12.dp),
    ) {
        val repostedBy = post.repostedBy
        val repostLine = extras.repostLine
        when {
            post.pinned -> ContextLine(XIcons.Pin, stringResource(Res.string.pinned))
            repostLine != null -> ContextLine(XIcons.Repost, repostLine, extras.onRepostLine)
            repostedBy != null -> ContextLine(XIcons.Repost, stringResource(Res.string.reposted_by, repostedBy))
        }
        Row {
            MarkedAvatar(post.avatarUrl, mark, markColor, onClick = { actions.openAuthor(post) }, label = markLabel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    AuthorLine(post.authorName, post.authorHandle, PostLines.time(agoText(post.createdAt), extras.timeNote), verified = false, modifier = Modifier.weight(1f))
                    extras.trailing?.invoke()
                }
                post.replyingTo?.let { handle -> ReplyLine(handle) { actions.openMention(handle) } }
                post.title?.let { PostTitle(it, Modifier.padding(top = 2.dp)) }
                extras.above?.invoke()
                if (!post.textCovered(revealed)) LinkedText(post.text, actions.openMention, actions.openTag, Modifier.padding(top = 2.dp))
                Attachments(post, actions, revealed, onReveal = { revealed = true }, gap = 10.dp, playing = playing)
                if (!post.textCovered(revealed)) extras.below?.invoke()
                val footer = extras.footer
                if (footer != null) footer() else CountsLine(post, rememberArchive(post, actions))
            }
        }
    }
}

/** Whether [this] keeps its text back behind its warning while it is not [revealed]. */
private fun NetworkPost.textCovered(revealed: Boolean): Boolean = coversText && warning != null && !revealed

/**
 * The avatar, [size] across, with the network's [mark] at its foot when the
 * row is among other networks'; its picture asked for with the headers its
 * server wants, as the shared tiles ask for theirs.
 */
@Composable
internal fun MarkedAvatar(url: String?, mark: ImageVector?, color: Color, size: Dp = 40.dp, onClick: (() -> Unit)? = null, label: String? = null) {
    Box {
        Avatar(url, size, onClick = onClick)
        if (mark != null) {
            Icon(
                mark,
                contentDescription = label,
                tint = color.takeOrElse { MaterialTheme.colorScheme.primary },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun PostTitle(title: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(title, style = style, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}

/** How the archive keeps [post], as its plugin says; null when it does not. */
@Composable
private fun rememberArchive(post: NetworkPost, actions: NetworkPostActions): PluginPostArchive? =
    remember(post, actions) { runCatching { actions.archive?.invoke(post) }.getOrNull() }

/** The opened post, larger, with its full time and its counts spelled out, as the app's opened X post; the line under it closes it off. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkFocalPost(post: NetworkPost, actions: NetworkPostActions, modifier: Modifier = Modifier) {
    var revealed by rememberSaveable(post.id) { mutableStateOf(false) }
    val archive = rememberArchive(post, actions)
    val more = actions.more
    val pressable = if (more == null) modifier else modifier.combinedClickable(onClick = {}, onLongClick = { more(post) })
    Column(pressable.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.avatarUrl, 44.dp) { actions.openAuthor(post) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { actions.openAuthor(post) }) {
                Text(post.authorName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@" + post.authorHandle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        post.replyingTo?.let { handle -> ReplyLine(handle) { actions.openMention(handle) } }
        post.title?.let { PostTitle(it, Modifier.padding(top = 12.dp), MaterialTheme.typography.titleLarge) }
        if (!post.textCovered(revealed)) {
            LinkedText(post.text, actions.openMention, actions.openTag, Modifier.padding(top = 12.dp), style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp))
        }
        Attachments(post, actions, revealed, onReveal = { revealed = true }, gap = 12.dp, playing = false)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fullTimeText(post.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (archive != null) NetworkBookmark(archive)
        }
        FocalCounts(post)
    }
    Hairline()
}

/** A quoted post, framed, opening on its own. */
@Composable
fun QuotedNetworkPost(post: NetworkPost, actions: NetworkPostActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { actions.open(post) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.avatarUrl, 20.dp)
            Spacer(Modifier.width(6.dp))
            AuthorLine(post.authorName, post.authorHandle, agoText(post.createdAt), verified = false, modifier = Modifier.weight(1f))
        }
        post.title?.let { PostTitle(it, Modifier.padding(top = 4.dp)) }
        val warning = post.warning
        if (post.coversText && warning != null) {
            // Its own screen is where the reader opens it.
            Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        } else {
            LinkedText(post.text, actions.openMention, actions.openTag, Modifier.padding(top = 4.dp), maxLines = 8)
            if (post.media.isNotEmpty()) {
                MediaTiles(post.media, { index -> actions.openMedia(post, index) }, Modifier.padding(top = 8.dp), hidden = warning != null)
            }
            post.card?.let { CardView(it.asXCard(), Modifier.padding(top = 8.dp)) }
        }
    }
}

/**
 * The media (behind its warning until revealed), the link card and the quoted
 * post, [gap] below what is above; a warning that [NetworkPost.coversText]
 * keeps all of them back until revealed.
 */
@Composable
private fun Attachments(post: NetworkPost, actions: NetworkPostActions, revealed: Boolean, onReveal: () -> Unit, gap: Dp, playing: Boolean) {
    val warning = post.warning?.takeIf { !revealed && (post.media.isNotEmpty() || post.coversText) }
    if (warning != null) WarningLine(warning, onReveal)
    if (post.textCovered(revealed)) return
    if (post.media.isNotEmpty()) {
        MediaTiles(
            post.media,
            { index -> if (warning != null) onReveal() else actions.openMedia(post, index) },
            Modifier.padding(top = gap),
            playing = playing,
            hidden = warning != null,
        )
    }
    post.card?.let { CardView(it.asXCard(), Modifier.padding(top = gap)) }
    post.quoted?.let { QuotedNetworkPost(it, actions, Modifier.padding(top = gap)) }
}

/** Why the media under it is hidden, and Show. */
@Composable
private fun WarningLine(warning: String, onReveal: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onReveal) { Text(stringResource(Res.string.plugin_post_show)) }
    }
}

/** "Reposted by …" or "Pinned" above a row; with [onClick], a tap on it goes there. */
@Composable
private fun ContextLine(icon: ImageVector, text: String, onClick: (() -> Unit)? = null) {
    val tappable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(tappable.padding(start = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** "Replying to @handle", the handle leading to its profile. */
@Composable
private fun ReplyLine(handle: String, onClick: () -> Unit) {
    Row(Modifier.padding(top = 2.dp)) {
        Text(stringResource(Res.string.replying_to) + " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "@$handle",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

/**
 * Replies, reposts and quotes, likes, and views where the network counts
 * them: numbers to read, nothing is sent. The bookmark of a post the archive
 * keeps ends them.
 */
@Composable
private fun CountsLine(post: NetworkPost, archive: PluginPostArchive?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, end = if (archive == null) 24.dp else 0.dp).height(if (archive == null) 20.dp else 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Count(XIcons.Reply, post.replies)
        Count(XIcons.Repost, post.reposts + post.quotes)
        Count(XIcons.Like, post.likes)
        post.views?.let { Count(XIcons.Views, it) }
        if (archive != null) NetworkBookmark(archive)
    }
}

/**
 * A post's bookmark, as the app's footers draw it: a tap saves [archive] on
 * this device where plain taps file, or takes it out; a long press opens the
 * save sheet with its folders. For a network that draws its own footer
 * ([NetworkPostExtras.footer]), as XTA's PluginPostBookmark.
 */
@Composable
fun NetworkBookmark(archive: PluginPostArchive, modifier: Modifier = Modifier) {
    val item = remember(archive) { archive.keepable() }
    KeepableSaveButton(item, modifier, Color.Unspecified)
}

@Composable
private fun Count(icon: ImageVector, n: Long) = NetworkCount(icon, n)

/**
 * A glyph and its count under a post, as the app's X footer draws one:
 * [onClick] (and [onLongClick]) make it a button that [label] names, [tint]
 * colours it (the theme's primary for what the reader marked).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkCount(
    icon: ImageVector,
    n: Long,
    label: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = CountButton({ Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp)) }, n, label, tint, onLongClick, onClick)

/** [NetworkCount] with a [glyph] of its own, as the device's like draws its heart burst; its number rolls as it changes ([RollingCount]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CountButton(
    glyph: @Composable () -> Unit,
    n: Long,
    label: String?,
    tint: Color,
    onLongClick: (() -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val clickable = if (onClick != null) {
        Modifier.clip(CircleShape).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick).padding(4.dp)
    } else {
        Modifier
    }
    Row(
        clickable.let { if (label != null) it.semantics { contentDescription = label } else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        glyph()
        if (n > 0) {
            RollingCount(countText(n), n, MaterialTheme.typography.labelMedium, tint, Modifier.padding(start = 4.dp))
        }
    }
}

/** A glyph at the end of a post's author line, where an X post has ⋯: a plugin's own follow or menu; [tint] marks one that is on. */
@Composable
fun NetworkHeaderButton(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Box(
        Modifier
            .offset(y = (-4).dp)
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Replies, reposts, quotes and likes spelled out under an opened post. */
@Composable
private fun FocalCounts(post: NetworkPost) {
    val stats = listOf(
        post.replies to Res.string.plugin_stat_replies,
        post.reposts to Res.string.stat_reposts,
        post.quotes to Res.string.stat_quotes,
        post.likes to Res.string.stat_likes,
    ).filter { it.first > 0 }
    if (stats.isEmpty()) return
    Hairline(Modifier.padding(top = 12.dp))
    val strong = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        stats.forEach { (n, label) ->
            val number = countText(n)
            val word = stringResource(label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                    append(" ")
                    append(word)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A network's link card drawn as X's, by the rule every network's preview follows (PostView.drawsLargeCard). */
private fun LinkCard.asXCard(): XCard.Link = XCard.Link(
    url = url,
    title = title?.takeIf { it.isNotBlank() } ?: domain ?: url,
    description = description?.takeIf { it.isNotBlank() },
    domain = domain,
    imageUrl = imageUrl,
    imageWidth = null,
    imageHeight = null,
    large = imageUrl != null,
    kind = "link",
)

private val IndentStep = 20.dp
private const val MAX_INDENT = 4
