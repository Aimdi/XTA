package com.aimdi.xtanative.ui.plugins

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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
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
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.compose.resources.stringResource

/** A piece of a post's text as the shared text draws it, whatever network it comes from. */
sealed interface TextRun {
    val text: String

    data class Plain(override val text: String) : TextRun

    /** Opens [url]: in a plugin that opens such links, else in the browser. */
    data class Link(override val text: String, val url: String) : TextRun

    /** Names an account: [who] is what its network finds it by (a handle, a DID). */
    data class Mention(override val text: String, val who: String) : TextRun

    /** A hashtag or cashtag: [query] is what a search for it asks ("#kotlin", "$TSLA"). */
    data class Tag(override val text: String, val query: String) : TextRun
}

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

/** A link preview under a post: the page's picture, title and description, opening [url]. */
data class LinkCard(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
) {
    /** The site, as the card names it: "example.com". */
    val domain: String? get() = url.toHttpUrlOrNull()?.host?.removePrefix("www.")
}

/**
 * A post from any network as the shared row and the shared opened post draw
 * it. [id] tells rows apart in a list and is what the plugin opens it by;
 * [authorId] is what it opens the author by (a DID, a user id), and
 * [authorHandle] is shown as "@handle".
 */
data class NetworkPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorHandle: String,
    val avatarUrl: String? = null,
    val createdAt: Instant? = null,
    val text: List<TextRun> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val card: LinkCard? = null,
    val quoted: NetworkPost? = null,
    val replies: Long = 0,
    val reposts: Long = 0,
    val quotes: Long = 0,
    val likes: Long = 0,
    /** Views, where the network counts them. */
    val views: Long? = null,
    /** Who reposted it into the list it is shown in. */
    val repostedBy: String? = null,
    /** Pinned by its author, at the top of their profile. */
    val pinned: Boolean = false,
    /** The handle of who it answers, for "Replying to @…". */
    val replyingTo: String? = null,
    /** Why its media is hidden until the reader opens it: a content warning, in the reader's language. */
    val warning: String? = null,
    /** Where it lives on the web. */
    val url: String? = null,
)

/** Where a network post's parts lead: its plugin decides. */
class NetworkPostActions(
    val open: (NetworkPost) -> Unit,
    val openAuthor: (NetworkPost) -> Unit,
    /** A mention, with whom it names ([TextRun.Mention.who]); "Replying to" gives the handle. */
    val openMention: (String) -> Unit,
    /** A tag, with its query ([TextRun.Tag.query]). */
    val openTag: (String) -> Unit,
    val openMedia: (NetworkPost, Int) -> Unit,
)

/**
 * A post as a list row, as the app draws X's: who reposted or pinned it, the
 * author, whom it answers, its text, media, link card and quoted post, and its
 * counts. [continues] draws it as part of a thread going on below; [indent]
 * moves a reply under the one it answers; with [playing], its first video
 * plays in place.
 */
@Composable
fun NetworkPostCard(
    post: NetworkPost,
    actions: NetworkPostActions,
    modifier: Modifier = Modifier,
    continues: Boolean = false,
    indent: Int = 0,
    playing: Boolean = false,
) {
    var revealed by rememberSaveable(post.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clickable { actions.open(post) }
            .padding(start = 16.dp + IndentStep * indent.coerceIn(0, MAX_INDENT), end = 16.dp, top = 12.dp, bottom = if (continues) 4.dp else 12.dp),
    ) {
        val repostedBy = post.repostedBy
        when {
            post.pinned -> ContextLine(XIcons.Pin, stringResource(Res.string.pinned))
            repostedBy != null -> ContextLine(XIcons.Repost, stringResource(Res.string.reposted_by, repostedBy))
        }
        Row {
            Avatar(post.avatarUrl, 40.dp) { actions.openAuthor(post) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(post.authorName, post.authorHandle, agoText(post.createdAt), verified = false)
                post.replyingTo?.let { handle -> ReplyLine(handle) { actions.openMention(handle) } }
                LinkedText(post.text, actions.openMention, actions.openTag, Modifier.padding(top = 2.dp))
                Attachments(post, actions, revealed, onReveal = { revealed = true }, gap = 10.dp, playing = playing)
                CountsLine(post)
            }
        }
    }
}

/** The opened post, larger, with its full time and its counts spelled out, as the app's opened X post; the line under it closes it off. */
@Composable
fun NetworkFocalPost(post: NetworkPost, actions: NetworkPostActions, modifier: Modifier = Modifier) {
    var revealed by rememberSaveable(post.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.avatarUrl, 44.dp) { actions.openAuthor(post) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { actions.openAuthor(post) }) {
                Text(post.authorName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@" + post.authorHandle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        post.replyingTo?.let { handle -> ReplyLine(handle) { actions.openMention(handle) } }
        LinkedText(post.text, actions.openMention, actions.openTag, Modifier.padding(top = 12.dp), style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp))
        Attachments(post, actions, revealed, onReveal = { revealed = true }, gap = 12.dp, playing = false)
        Text(
            fullTimeText(post.createdAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
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
        LinkedText(post.text, actions.openMention, actions.openTag, Modifier.padding(top = 4.dp), maxLines = 8)
        if (post.media.isNotEmpty()) {
            MediaTiles(post.media, { index -> actions.openMedia(post, index) }, Modifier.padding(top = 8.dp), hidden = post.warning != null)
        }
        post.card?.let { CardView(it.asXCard(), Modifier.padding(top = 8.dp)) }
    }
}

/** The media (behind its warning until revealed), the link card and the quoted post, [gap] below what is above. */
@Composable
private fun Attachments(post: NetworkPost, actions: NetworkPostActions, revealed: Boolean, onReveal: () -> Unit, gap: Dp, playing: Boolean) {
    val warning = post.warning?.takeIf { !revealed && post.media.isNotEmpty() }
    if (warning != null) WarningLine(warning, onReveal)
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

/** "Reposted by …" or "Pinned" above a row. */
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

/** Replies, reposts and quotes, likes, and views where the network counts them: numbers to read, nothing is sent. */
@Composable
private fun CountsLine(post: NetworkPost) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, end = 24.dp).height(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Count(XIcons.Reply, post.replies)
        Count(XIcons.Repost, post.reposts + post.quotes)
        Count(XIcons.Like, post.likes)
        post.views?.let { Count(XIcons.Views, it) }
    }
}

@Composable
private fun Count(icon: ImageVector, n: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        if (n > 0) {
            Text(countText(n), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
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

/** A network's link card drawn as X's: large with its picture, small without. */
private fun LinkCard.asXCard(): XCard.Link = XCard.Link(
    url = url,
    title = title?.takeIf { it.isNotBlank() } ?: domain ?: url,
    description = description?.takeIf { it.isNotBlank() },
    domain = domain,
    imageUrl = imageUrl,
    imageWidth = null,
    imageHeight = null,
    large = imageUrl != null,
)

private val IndentStep = 20.dp
private const val MAX_INDENT = 4
