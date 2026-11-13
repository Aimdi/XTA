package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.networks.threads.ThreadsLinkCard
import com.aimdi.xtanative.networks.threads.ThreadsPeople
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.NetworkBookmark
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.AuthorLine
import com.aimdi.xtanative.ui.links.LinkFrom
import com.aimdi.xtanative.ui.post.CardView
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.post.PostPrefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A Threads post as a timeline card, laid out as an X post is
 * (threads_post_card.dart): who reposted it or that it is pinned, the
 * author with their community badge, Follow and ⋯ ("Edited" by its time), the topic, whom it
 * answers, whether it is a ghost post, the caption (its spoilers covered, a
 * long one cut short, Meta's translation in its place once asked for), its
 * attached text, its song, its poll's results, Meta's labels (and "Liked
 * by the author"), its pictures and videos, the link card, the quote (or
 * "This post is unavailable" where Threads no longer shows it), its
 * trends and "Show N more posts in this thread"; then the counts Meta gave, the like kept on
 * this device, its bookmark in the app's Saved and the way to the web. A
 * long press, or ⋯, opens its sheet: copying its words and link, the post
 * as a picture to share, hiding it and muting its author on this device,
 * above the app's post sheet. With [focal] it is the opened post: its time and date
 * on a line under it with its quotes and shares, its text selectable. [connectTop] and [connectBottom] join it to
 * the posts above and below it through the avatar, as a thread draws them;
 * without [divider] the line under it is left to the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThreadsPostCard(
    app: ThreadsApp,
    post: ThreadsPost,
    modifier: Modifier = Modifier,
    openOnTap: Boolean = true,
    focal: Boolean = false,
    connectTop: Boolean = false,
    connectBottom: Boolean = false,
    divider: Boolean = true,
    byAuthor: Boolean = false,
) {
    val settings by app.prefs.collectAsState()
    val liked by app.likes.state.collectAsState()
    val doubleTap = settings[AccessibilityPrefs.doubleTapLike]
    val isLiked = liked?.contains(post.id) == true
    val open = { if (openOnTap) app.openPost(post) }
    var sheet by remember { mutableStateOf(false) }
    val menu = { sheet = true }
    val translation = rememberThreadsTranslation(app, post)
    val summary = rememberSummary(translation.applied(post), isLiked, menu) { app.toggleLike(post) }
    val picture = rememberThreadsPostPicture()
    val sharePicture = rememberPictureShare(app, post, picture)
    Column(
        modifier
            .fillMaxWidth()
            .recordedIn(picture)
            .combinedClickable(
                onClickLabel = pluginString(Strings.open_post),
                onClick = open,
                onLongClickLabel = pluginString(Strings.more_options),
                onLongClick = menu,
                onDoubleClick = if (doubleTap) { { if (!isLiked) app.toggleLike(post) } } else null,
            )
            .semantics(properties = summary)
            .padding(start = 16.dp, end = 8.dp),
    ) {
        if (post.isRepost) RepostLine(app, post)
        PinnedLine(post)
        val above = if (post.isRepost || post.facts.pinnedToProfile || post.facts.pinnedToParent) 4.dp else 12.dp
        Row(Modifier.fillMaxWidth().rails(connectTop, connectBottom, above).padding(top = above, bottom = if (connectBottom) 0.dp else 12.dp)) {
            PluginAvatar(post.avatarUrl, post.authorName, post.handle, AvatarSize) { app.openProfile(post.handle) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Header(app, post, focal, byAuthor, PostPrefs.of(settings).absoluteTimestamps, menu)
                Body(app, post, translation, openOnTap, focal)
                Engagement(app, post, isLiked, PostPrefs.of(settings).hideCounts, open)
            }
        }
    }
    if (divider && !connectBottom) Hairline()
    if (sheet) ThreadsPostSheet(app, post, onPicture = sharePicture) { sheet = false }
}

/**
 * The card as a screen reader takes it: one description of the whole post
 * ([ThreadsCardRules.summary]), and the like on this device and the sheet
 * as its actions.
 */
@Composable
private fun rememberSummary(post: ThreadsPost, liked: Boolean, menu: () -> Unit, like: () -> Unit): SemanticsPropertyReceiver.() -> Unit {
    val reposted = if (post.isRepost) pluginString(Strings.reposted, post.reposterDisplayName) else null
    val spoiler = pluginString(Strings.spoiler)
    val ghost = pluginString(Strings.ghost_post)
    val labels = listOfNotNull(
        post.facts.communityBadge?.let { pluginString(badgeLabel(it)) },
        ghost.takeIf { post.facts.ghost != null },
        post.facts.attachment?.let { pluginString(Strings.attached_text) + ": " + (it.title ?: it.text.take(SPOKEN_ATTACHMENT)) },
        pluginString(Strings.ai_info).takeIf { post.facts.aiLabel },
        pluginString(Strings.sharing_friction).takeIf { post.facts.sharingFriction },
        pluginString(Strings.edited).takeIf { post.facts.edited },
        pluginString(Strings.liked_by_author).takeIf { post.facts.likedByAuthor },
        post.facts.music?.let { pluginString(Strings.music) + ": " + it.line },
        post.facts.trends.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.title }?.let { pluginString(Strings.post_trend, it) },
        pluginString(Strings.quote_unavailable).takeIf { post.quoted == null && post.facts.quoteUnavailable },
    )
    val likeLabel = pluginString(if (liked) Strings.unlike_on_device else Strings.like_on_device)
    val moreLabel = pluginString(Strings.more_options)
    val time = fullTimeText(post.publishedAt)
    val said = remember(post, time, reposted, spoiler, labels) { ThreadsCardRules.summary(post, time, reposted, spoiler, labels) }
    return {
        contentDescription = said
        customActions = listOf(
            CustomAccessibilityAction(likeLabel) { like(); true },
            CustomAccessibilityAction(moreLabel) { menu(); true },
        )
    }
}

private val AvatarSize = 40.dp

/** How much of attached text the card's description reads: its start; the block itself holds the rest. */
private const val SPOKEN_ATTACHMENT = 280

/** The rails through the avatar, [above] from the row's top, to the posts above and below, as a conversation draws a thread. */
@Composable
private fun Modifier.rails(top: Boolean, bottom: Boolean, above: Dp): Modifier {
    if (!top && !bottom) return this
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val x = (AvatarSize / 2).toPx()
        val avatarTop = above.toPx()
        val stroke = 2.dp.toPx()
        if (top) drawLine(color, Offset(x, 0f), Offset(x, avatarTop - stroke), stroke)
        if (bottom) drawLine(color, Offset(x, avatarTop + AvatarSize.toPx() + stroke), Offset(x, size.height), stroke)
    }
}

/** "Name reposted", leading to the reposter. */
@Composable
private fun RepostLine(app: ThreadsApp, post: ThreadsPost) {
    val reposter = post.repostedByHandle
    Row(
        Modifier.padding(top = 12.dp, start = 24.dp).let { if (reposter.isNullOrEmpty()) it else it.clickable { app.openProfile(reposter) } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XIcons.Repost, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            pluginString(Strings.reposted, post.reposterDisplayName),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The author line (a long press: its exact time), Follow while the author is not followed here, and ⋯. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(app: ThreadsApp, post: ThreadsPost, focal: Boolean, byAuthor: Boolean, absolute: Boolean, onMore: () -> Unit) {
    val rows by app.accounts.state.collectAsState()
    var exact by rememberSaveable(post.id) { mutableStateOf(false) }
    val edited = pluginString(Strings.edited)
    val time = ThreadsCardRules.timeLine(
        when {
            focal -> ""
            absolute || exact -> fullTimeText(post.publishedAt)
            else -> agoText(post.publishedAt)
        },
        edited.takeIf { post.facts.edited && !focal },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        AuthorLine(
            post.authorName,
            post.handle,
            time,
            verified = post.isVerified,
            modifier = Modifier.weight(1f).combinedClickable(
                onClickLabel = post.authorName,
                onClick = { app.openProfile(post.handle) },
                onLongClickLabel = pluginString(Strings.exact_time).takeIf { !focal && !absolute },
                onLongClick = if (focal || absolute) null else { { exact = !exact } },
            ),
        )
        if (byAuthor) AuthorChip()
        post.facts.communityBadge?.let { AuthorChip(pluginString(badgeLabel(it))) }
        if (rows != null && !ThreadsAccounts.follows(rows, post.handle)) {
            SmallIcon(ThreadsIcons.PersonAdd, pluginString(Strings.follow)) { app.follow(ThreadsAccount.of(post)) }
        }
        SmallIcon(PostIcons.MoreHoriz, pluginString(Strings.more_info), onClick = onMore)
    }
}

/**
 * "Author", beside a reply the opened post's author wrote; "Member" (or the
 * author's other badge in the community the post is in) as threads.com marks it.
 */
@Composable
private fun AuthorChip(text: String = pluginString(Strings.author)) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * The topic, whom it answers, whether it is a ghost post, the caption (or
 * Meta's translation of it) with its translate control, its attached text,
 * the media, the link card, the quote, its trends, the rest of the thread
 * and, opened, the time.
 */
@Composable
private fun Body(app: ThreadsApp, post: ThreadsPost, translation: ThreadsTranslationView, openOnTap: Boolean, focal: Boolean) {
    post.topicTag?.let { TopicTag(app, post, it) }
    post.replyToHandle?.takeIf { it.isNotEmpty() }?.let { ReplyingTo(app, it) }
    GhostLine(post, Modifier.padding(top = 2.dp, bottom = 2.dp))
    ThreadsCaptionText(app, translation.applied(post), Modifier.padding(top = 2.dp), style = if (focal) FocalStyle else threadsBodyStyle(), selectable = focal)
    TranslationLine(translation, focal)
    post.facts.attachment?.let { TextAttachmentBlock(app, post, it, focal, Modifier.partGap()) }
    MusicLine(post, Modifier.padding(top = 8.dp))
    post.facts.poll?.let { ThreadsPollView(it, Modifier.partGap()) }
    FactLines(post)
    ThreadsPostMedia(app, post, Modifier.partGap(), opens = openOnTap)
    post.linkCard?.let { ThreadsLink(app, post, it, Modifier.partGap(), opens = openOnTap) }
    post.quoted?.let { ThreadsQuote(app, it, Modifier.partGap()) }
    if (post.quoted == null && post.facts.quoteUnavailable) QuoteUnavailable(Modifier.partGap())
    TrendChips(app, post, Modifier.padding(top = 4.dp))
    if (post.selfThreadCount > 0 && openOnTap) {
        TextButton(onClick = { app.openPost(post) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(PostIcons.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginPlural(Strings.show_thread, post.selfThreadCount, post.selfThreadCount), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (focal) {
        Text(
            ThreadsCardRules.timeLine(fullTimeText(post.publishedAt), pluginString(Strings.edited).takeIf { post.facts.edited }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        ReplyAudienceLine(post)
        FocalCounts(app, post, PostPrefs.of(app.current()).hideCounts)
    }
}

private val FocalStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp)

@Composable
private fun ReplyingTo(app: ThreadsApp, handle: String) {
    Row(Modifier.padding(top = 2.dp)) {
        Text(pluginString(Strings.replying_to) + " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "@$handle",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { app.openProfile(handle) },
        )
    }
}

/**
 * The link preview Meta attached, as the app draws X's: large with its
 * picture, small without. Its page opens in the app's browser with [post] in
 * the bar under it (threads_post_card.dart's threadsLinkPreview): who wrote
 * it, the counts Meta gave, its link to share, and the way back to it,
 * which opens the post where the card [opens] one and closes the browser
 * on the post's own screen.
 */
@Composable
internal fun ThreadsLink(app: ThreadsApp, post: ThreadsPost, card: ThreadsLinkCard, modifier: Modifier = Modifier, opens: Boolean = true) {
    val from = remember(post, opens) { LinkFrom(ThreadsLinkContext.of(post), if (opens) { { app.openPost(post) } } else null) }
    val domain = card.providerName ?: card.url.toHttpUrlOrNull()?.host?.removePrefix("www.")
    val shown = XCard.Link(
        url = card.url,
        title = card.title?.takeIf { it.isNotBlank() } ?: domain ?: card.url,
        description = card.description?.takeIf { it.isNotBlank() },
        domain = domain,
        imageUrl = card.imageUrl,
        imageWidth = null,
        imageHeight = null,
        large = card.hasImage,
    )
    CardView(shown, from, modifier)
}

/** The post this one quotes, boxed, opening its own conversation; its media and link card under its words. */
@Composable
internal fun ThreadsQuote(app: ThreadsApp, quote: ThreadsPost, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { app.openPost(quote) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PluginAvatar(quote.avatarUrl, quote.authorName, quote.handle, 20.dp)
            Spacer(Modifier.width(8.dp))
            AuthorLine(quote.authorName, quote.handle, agoText(quote.publishedAt), verified = quote.isVerified, modifier = Modifier.weight(1f))
        }
        ThreadsCaptionText(app, quote, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, selectable = false, maxLines = 6)
        quote.facts.poll?.let { ThreadsPollView(it, Modifier.padding(top = 8.dp)) }
        ThreadsPostMedia(app, quote, Modifier.padding(top = 8.dp))
        quote.linkCard?.let { ThreadsLink(app, quote, it, Modifier.padding(top = 8.dp)) }
    }
}

/** Where a quoted post would be boxed, when Threads no longer shows it: "This post is unavailable", as threads.com says. */
@Composable
private fun QuoteUnavailable(modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        pluginString(Strings.quote_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}

/**
 * Replies and reposts as Meta counted them, both opening the post (for a
 * signed-in reader, the reposts open who reposted it); the like kept on
 * this device, its count one more while liked; the bookmark that files it in the app's Saved (a long press:
 * its folder); the post on the web. No count Meta did not give is drawn,
 * and none in zen or calm mode.
 */
@Composable
private fun Engagement(app: ThreadsApp, post: ThreadsPost, liked: Boolean, hideCounts: Boolean, open: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val openPost = pluginString(Strings.open_post)
    val counted = { n: Long? -> n?.takeIf { !hideCounts } }
    val outside = rememberOpenOutside()
    val archive = remember(post) { ThreadsArchive.of(post) }
    val settings by app.prefs.collectAsState()
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        CountButton(XIcons.Reply, counted(post.replyCount), openPost, muted, open)
        val reposters = ThreadsPeople.mediaId(post).takeIf { counted(post.repostCount) != null && ThreadsPeopleView.opensReposters(post, signedIn) }
        if (reposters == null) {
            CountButton(XIcons.Repost, counted(post.repostCount), openPost, muted, open)
        } else {
            CountButton(XIcons.Repost, counted(post.repostCount), pluginString(Strings.show_reposted_by), muted) { app.host.open(ThreadsRoutes.reposters(reposters, post.url)) }
        }
        val heart = if (liked) ThreadsHeart.Filled else ThreadsHeart.Outlined
        val likes = counted(post.likeCount?.let { if (liked) it + 1 else it })
        val likeLabel = pluginString(if (liked) Strings.unlike_on_device else Strings.like_on_device)
        val likeTint = if (liked) MaterialTheme.colorScheme.primary else muted
        val likers = ThreadsPeople.mediaId(post).takeIf { likes != null && ThreadsPeopleView.opensLikers(post, signedIn) }
        if (likers == null) {
            CountButton(heart, likes, likeLabel, likeTint) { app.toggleLike(post) }
        } else {
            // The heart is the like on this device; its count, for a signed-in reader, who liked the post on Threads.
            SmallIcon(heart, likeLabel, likeTint) { app.toggleLike(post) }
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 32.dp)
                    .clickable(role = Role.Button, onClickLabel = pluginString(Strings.show_liked_by)) { app.host.open(ThreadsRoutes.likers(likers, post.url)) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(countText(likes ?: 0), style = MaterialTheme.typography.labelMedium, color = likeTint, modifier = Modifier.padding(end = 8.dp))
            }
        }
        NetworkBookmark(archive)
        Spacer(Modifier.weight(1f))
        post.url?.let { url -> SmallIcon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) { outside(url) } }
    }
}

/** A footer action: its icon, the count beside it when there is one, and its name for screen readers. */
@Composable
private fun CountButton(icon: ImageVector, count: Long?, label: String, tint: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        if (count != null) Text(countText(count), style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
internal fun SmallIcon(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }, modifier = Modifier.size(20.dp))
    }
}

/** The heart the like on this device is drawn with. */
internal object ThreadsHeart {
    val Filled get() = Icons.Filled.Favorite
    val Outlined get() = Icons.Filled.FavoriteBorder
}
