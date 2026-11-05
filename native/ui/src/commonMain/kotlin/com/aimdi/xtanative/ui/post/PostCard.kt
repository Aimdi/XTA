package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.services.links.XLinks
import com.aimdi.xtanative.ui.article.ArticlePreviewCard
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_community
import com.aimdi.xtanative.ui.generated.resources.posts_more
import com.aimdi.xtanative.ui.generated.resources.posts_pinned
import com.aimdi.xtanative.ui.generated.resources.posts_reply_not_found
import com.aimdi.xtanative.ui.generated.resources.posts_replying_to
import com.aimdi.xtanative.ui.generated.resources.posts_reposted
import com.aimdi.xtanative.ui.generated.resources.posts_subscribers_only_of_author
import com.aimdi.xtanative.ui.generated.resources.posts_thread
import com.aimdi.xtanative.ui.generated.resources.posts_unavailable
import com.aimdi.xtanative.ui.generated.resources.posts_no_text
import com.aimdi.xtanative.ui.generated.resources.posts_quote_unavailable
import com.aimdi.xtanative.ui.generated.resources.posts_view_quoted
import com.aimdi.xtanative.ui.media.LiveRooms
import com.aimdi.xtanative.ui.media.LocalLivePlayer
import com.aimdi.xtanative.ui.saved.KeepNotices
import org.jetbrains.compose.resources.stringResource

/**
 * The price strip under a post that names tickers: XTA's stocks plugin draws
 * it (cashtag_quotes.dart). Without one there is no strip, never an error.
 */
val LocalCashtagStrip = staticCompositionLocalOf<(@Composable (symbols: List<String>) -> Unit)?> { null }

/**
 * Where a cashtag tapped in a post goes instead of X's search for it: an
 * installed plugin's screen for the symbol (Stocks' ticker), answering
 * whether one opened. Without one, or when none opens, the tap searches X.
 */
val LocalCashtagOpener = staticCompositionLocalOf<((symbol: String) -> Boolean)?> { null }

/**
 * What a long press on a cashtag in a post opens: the app's cashtag sheet
 * (rich_text.dart's), the cashtag leading where a tap does, with what the
 * running plugins add for it. Without one, a long press is a tap.
 */
val LocalCashtagSheet = staticCompositionLocalOf<((symbol: String) -> Unit)?> { null }

/** One timeline row, or a thread X groups, with the line under it. With [playing], its first video plays in place. */
@Composable
fun ThreadRows(thread: XThread, actions: PostActions, pinnedIds: Set<String> = emptySet(), playing: Boolean = false) {
    val playingPost = if (playing) thread.posts.firstOrNull { it.hasVideo } else null
    val threaded = thread.posts.size > 1
    thread.posts.forEachIndexed { index, post ->
        PostCard(
            post,
            actions,
            pinned = post.id in pinnedIds,
            continues = index < thread.posts.lastIndex,
            playing = post === playingPost,
            firstOfThread = threaded && index == 0,
            midThread = index > 0,
        )
    }
    // A conversation keeps the place of the posts X withheld.
    thread.withheld.forEach { gap ->
        UnavailablePost(
            gap.reason ?: stringResource(if (gap.subscribersOnly) Res.string.posts_subscribers_only_of_author else Res.string.posts_unavailable),
            gap.screenName?.let { XLinks.waybackUrl(it, gap.id) },
            actions,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    Hairline()
}

/** Whether a row has a video or GIF of its own to play in place. */
val XPost.hasVideo: Boolean get() = shown.media.any { it.kind != XMedia.Kind.PHOTO }

val XThread.hasVideo: Boolean get() = posts.any { it.hasVideo }

private val AvatarSize = 40.dp
private val RailStart = 16.dp

/** How far a post's media row runs past its column: the post's own margin, to the screen's edge. */
private val MediaBleed = 16.dp

/**
 * A post as a feed row (tweet.dart): who reposted it, pinned, the thread and
 * the community above; the author with translate and ⋯ at the end; then the
 * reply line, the text and what the post carries, and the counts.
 * [continues] and [midThread] join it to the posts of its thread by a line
 * through the avatars. A tap opens it; with "Double-tap to like" on, a
 * double-tap likes it on this device.
 */
@Composable
fun PostCard(
    post: XPost,
    actions: PostActions,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    continues: Boolean = false,
    playing: Boolean = false,
    firstOfThread: Boolean = false,
    midThread: Boolean = false,
) {
    val scope = postScope()
    val shown = post.shown
    val translation = rememberPostTranslation(shown, scope)
    val drawn = translation?.shown ?: shown
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val picture = rememberPostPicture()
    Column(
        modifier
            .fillMaxWidth()
            .recordedIn(picture)
            .opening(rememberDoubleTapLike(shown, scope.prefs, actions)) { actions.openPost(shown.id) }
            .drawBehind {
                val x = (RailStart + AvatarSize / 2).toPx()
                val width = 2.dp.toPx()
                val top = 12.dp.toPx()
                if (midThread) drawLine(railColor, Offset(x, 0f), Offset(x, top), width)
                if (continues) drawLine(railColor, Offset(x, top + AvatarSize.toPx()), Offset(x, size.height), width)
            }
            .padding(start = RailStart, end = 16.dp, top = 12.dp, bottom = if (continues) 4.dp else 12.dp),
    ) {
        ContextLines(post, actions, pinned, firstOfThread)
        Row {
            PostAvatar(shown, scope, actions, AvatarSize)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PostHeader(post, scope, translation, actions)
                PostView.replyingTo(shown, quoted = false, midThread = midThread)?.let { ReplyingTo(it, shown.replyToPostId, actions) }
                when {
                    PostView.textMissing(shown) -> StateTile(stringResource(Res.string.posts_no_text), null, Modifier.padding(top = 2.dp))
                    shown.article == null -> ExpandablePostText(drawn, actions, Modifier.padding(top = 2.dp))
                }
                TranslationFailure(translation)
                PostAttachments(shown, scope, actions, playing = playing, gap = 10.dp)
                PostFooter(shown, actions, FooterCounts.of(shown, scope.prefs, showViews = true), picture = picture)
            }
        }
    }
}

/**
 * A tap on a post opens it; [like], when the reader asked for it, likes it
 * at a double-tap (tweet.dart's _withDoubleTapToLike). Without it a tap does
 * not wait to see whether a second follows.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.opening(like: (() -> Unit)?, open: () -> Unit): Modifier =
    if (like == null) clickable(onClick = open) else combinedClickable(onDoubleClick = like, onClick = open)

/** What a double-tap on [post] does with "Double-tap to like" on: likes it on this device, unless it is liked already; null while off. */
@Composable
internal fun rememberDoubleTapLike(post: XPost, prefs: PostPrefs, actions: PostActions): (() -> Unit)? {
    val graph = LocalGraph.current
    val like = remember(graph, post, actions) { { actions.keep.likeHere(graph, post) } }
    return like.takeIf { prefs.doubleTapLike }
}

@Composable
private fun ContextLines(post: XPost, actions: PostActions, pinned: Boolean, firstOfThread: Boolean) {
    PostView.contextLines(post, pinned, firstOfThread, quoted = false).forEach { line ->
        when (line) {
            is ContextLine.Reposted -> ContextRow(XIcons.Repost, stringResource(Res.string.posts_reposted, line.by.name, agoText(post.createdAt))) {
                actions.openUser(line.by.screenName, line.by.id)
            }
            ContextLine.Pinned -> ContextRow(XIcons.Pin, stringResource(Res.string.posts_pinned))
            ContextLine.Thread -> ContextRow(PostIcons.Forum, stringResource(Res.string.posts_thread))
            is ContextLine.Community -> ContextRow(PostIcons.People, stringResource(Res.string.posts_community)) { actions.openLink(line.url) }
        }
    }
}

/** The author's picture, opening the profile; a plain face in non-confirmation-bias mode; XTA's follow badge on it. */
@Composable
internal fun PostAvatar(post: XPost, scope: PostScope, actions: PostActions, size: Dp) {
    if (!PostView.showsAuthor(post, scope.prefs, scope.profileScreenName)) {
        Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size))
        return
    }
    val open = { actions.openUser(post.author.screenName, post.author.id) }
    if (!scope.prefs.followBadge) {
        Avatar(post.author.largeAvatarUrl, size, onClick = open)
        return
    }
    FollowBadge(post.author, size) { Avatar(post.author.largeAvatarUrl, size, onClick = open) }
}

/** The author line with the translate control and the ⋯ menu at its end, where X keeps them. */
@Composable
private fun PostHeader(post: XPost, scope: PostScope, translation: PostTranslation?, actions: PostActions) {
    val shown = post.shown
    var menu by rememberSaveable(shown.id) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f).padding(top = 2.dp)) { PostAuthor(shown, scope) }
        translation?.let { TranslateButton(it, scope) }
        HeaderButton(PostIcons.MoreHoriz, stringResource(Res.string.posts_more)) { menu = true }
    }
    if (menu) PostMenu(post, actions, onDismiss = { menu = false })
}

/** "Name ✓ @handle · 3h", or the time alone when the author is not shown. */
@Composable
internal fun PostAuthor(post: XPost, scope: PostScope, modifier: Modifier = Modifier) {
    val time = if (scope.prefs.absoluteTimestamps) fullTimeText(post.createdAt) else agoText(post.createdAt)
    if (PostView.showsAuthor(post, scope.prefs, scope.profileScreenName)) {
        AuthorLine(post.author.name, post.author.screenName, time, post.author.verified, modifier)
    } else {
        Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}

/** "Replying to @maya": the post it answers opens on a tap, or, when X did not say which it is, a notice says it could not be found. */
@Composable
internal fun ReplyingTo(screenName: String, replyToId: String?, actions: PostActions) {
    val missing = stringResource(Res.string.posts_reply_not_found)
    Row(Modifier.padding(top = 2.dp).clickable { if (replyToId != null) actions.openPost(replyToId) else KeepNotices.say(missing) }) {
        Text(
            stringResource(Res.string.posts_replying_to) + " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("@$screenName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * What a post carries under its text, in XTA's order: the paid-subscriber
 * line, an article link, the media (behind the sensitive gate when X marks
 * it), the quote, the card, a Grok share, the community note, the article,
 * a broadcast or Space and the price strip. The media's row starts at
 * [startMedia], the picture a grid's viewer opened the post at, and runs off
 * the screen's edge except inside a quote.
 */
@Composable
internal fun PostAttachments(shown: XPost, scope: PostScope, actions: PostActions, playing: Boolean, gap: Dp, quoted: Boolean = false, startMedia: Int = 0) {
    if (shown.subscriberPreview) SubscriberPreviewLine(shown.author.screenName)
    PostView.articleLink(shown)?.let { ArticleLinkCard({ actions.openArticle(shown.id) }, Modifier.padding(top = gap)) }
    if (shown.media.isNotEmpty()) {
        val media = @Composable {
            MediaGrid(
                shown.media,
                { index -> actions.openMedia(shown.id, index) },
                Modifier.padding(top = gap),
                playing = playing,
                post = shown,
                start = startMedia,
                bleed = if (quoted) 0.dp else MediaBleed,
            )
        }
        if (shown.sensitive) SensitiveGate(scope.sensitive, modifier = Modifier.padding(top = gap)) { media() } else media()
    }
    when (val quote = PostView.quote(shown, quoted)) {
        is QuoteBlock.Shown -> QuotedPost(quote.post, actions, Modifier.padding(top = gap))
        is QuoteBlock.Withheld -> UnavailablePost(
            quote.message ?: stringResource(if (quote.subscribersOnly) Res.string.posts_subscribers_only_of_author else Res.string.posts_unavailable),
            quote.archiveUrl,
            actions,
            Modifier.padding(top = gap),
        )
        is QuoteBlock.Unsent -> StateTile(stringResource(Res.string.posts_view_quoted), { actions.openPost(quote.id) }, Modifier.padding(top = gap))
        QuoteBlock.Missing -> StateTile(stringResource(Res.string.posts_quote_unavailable), null, Modifier.padding(top = gap))
        null -> Unit
    }
    shown.card?.let { CardView(it, shown, actions, Modifier.padding(top = gap)) }
    shown.grokShare?.let { GrokShareCard(it, shown, actions, Modifier.padding(top = gap)) }
    shown.note?.let { CommunityNoteBlock(it, actions, Modifier.padding(top = gap)) }
    // The opened post lays its article out in full; anywhere else it is a card leading there.
    if (shown.article != null && scope.focalId != shown.id) ArticlePreviewCard(shown, { actions.openPost(shown.id) }, Modifier.padding(top = gap))
    if (PostView.showsLive(shown)) LiveOpener(shown, actions, Modifier.padding(top = gap))
    val symbols = PostView.cashtags(shown, quoted)
    if (symbols.isNotEmpty()) LocalCashtagStrip.current?.invoke(symbols)
}

/** A broadcast or Space: the live player, or its page in a browser when X cannot be asked for its stream or no player is at hand. */
@Composable
private fun LiveOpener(post: XPost, actions: PostActions, modifier: Modifier) {
    val live = post.live ?: return
    val player = LocalLivePlayer.current
    LiveBlock(
        live,
        onOpen = { LiveRooms.open(live, player = player ?: { room -> room.watchUrl?.let(actions.openInBrowser) ?: Unit }, browser = actions.openInBrowser) },
        modifier = modifier,
    )
}

/** Why the translation failed, under the text: the translate button turns red as well. */
@Composable
internal fun TranslationFailure(translation: PostTranslation?) {
    if (translation?.status != TranslationStatus.FAILED) return
    Text(
        translationProblemText(translation.problem ?: TranslationProblem.OTHER),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** The post a post quotes, framed and smaller, opening on its own: no counts, translate, menu, nor a quote of its own. */
@Composable
fun QuotedPost(post: XPost, actions: PostActions, modifier: Modifier = Modifier) {
    val scope = postScope()
    Embed(modifier, onClick = { actions.openPost(post.id) }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (PostView.showsAuthor(post, scope.prefs, scope.profileScreenName)) {
                    Avatar(post.author.avatarUrl, 20.dp)
                    Spacer(Modifier.width(6.dp))
                }
                PostAuthor(post, scope, Modifier.weight(1f))
            }
            if (post.article == null) ExpandablePostText(post, actions, Modifier.padding(top = 4.dp))
            PostAttachments(post, scope, actions, playing = false, gap = 8.dp, quoted = true)
        }
    }
}
