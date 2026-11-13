package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XCommunityNote
import com.aimdi.xtanative.core.x.model.XGrokShare
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_article_on_x
import com.aimdi.xtanative.ui.generated.resources.posts_community_notes
import com.aimdi.xtanative.ui.generated.resources.posts_filter_broadcasts
import com.aimdi.xtanative.ui.generated.resources.posts_open_on_web_archive
import com.aimdi.xtanative.ui.generated.resources.posts_possibly_sensitive
import com.aimdi.xtanative.ui.generated.resources.posts_possibly_sensitive_post
import com.aimdi.xtanative.ui.generated.resources.posts_sensitive_always
import com.aimdi.xtanative.ui.generated.resources.posts_sensitive_show
import com.aimdi.xtanative.ui.generated.resources.posts_spaces
import com.aimdi.xtanative.ui.generated.resources.posts_subscribers_only
import com.aimdi.xtanative.ui.generated.resources.posts_subscribers_only_of_author
import com.aimdi.xtanative.ui.links.LocalLinks
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal val EmbedShape = RoundedCornerShape(16.dp)

/** A frame around something a post carries, as XTA's TweetEmbedSurface draws it; [onClick] opens it. */
@Composable
internal fun Embed(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(EmbedShape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, EmbedShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) { content() }
}

/**
 * Media X marks as possibly sensitive, behind XTA's gate: "Show" opens every
 * gate of the screen, "Always show sensitive media" for good. [message]
 * says what is hidden: a post's media, or a whole profile.
 */
@Composable
fun SensitiveGate(reveal: SensitiveReveal, message: String? = null, framed: Boolean = true, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!reveal.hidden) {
        content()
        return
    }
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(PostIcons.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(stringResource(Res.string.posts_possibly_sensitive), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(
                message ?: stringResource(Res.string.posts_possibly_sensitive_post),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { reveal.hidden = false }) { Text(stringResource(Res.string.posts_sensitive_show)) }
                TextButton(onClick = { scope.launch { alwaysShowSensitive(graph, reveal) } }) {
                    Text(stringResource(Res.string.posts_sensitive_always), textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (framed) Embed(modifier) { body() } else Box(modifier.fillMaxWidth()) { body() }
}

/**
 * A post X will not show (deleted, withheld, its account private or
 * suspended): one tap finds it in the Wayback Machine when its author and
 * id are known, opened as a link in a post is, so XTA's browser screen shows
 * it when the reader keeps links in the app (unavailable_post.dart).
 */
@Composable
fun UnavailablePost(message: String, archiveUrl: String?, actions: PostActions, modifier: Modifier = Modifier) {
    val links = LocalLinks.current
    Embed(modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (archiveUrl != null) {
                TextButton(onClick = { links?.openPostLink(archiveUrl) ?: actions.openLink(archiveUrl) }) {
                    Icon(PostIcons.History, null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.posts_open_on_web_archive), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** A plain line in a frame standing in for a post: "View quoted tweet", opening it. */
@Composable
internal fun StateTile(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Embed(modifier, onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The community note under a post: "Users added context" and the note, its links live. */
@Composable
internal fun CommunityNoteBlock(note: XCommunityNote, actions: PostActions, modifier: Modifier = Modifier) {
    Embed(modifier, onClick = note.url?.let { url -> { actions.openLink(url) } }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PostIcons.People, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(Res.string.posts_community_notes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            RunsText(XText.note(note), actions)
        }
    }
}

/**
 * A link to an X article the post carries without the article: X sends
 * nothing to build a preview from, so the card says what the link is
 * (article_link_card.dart).
 */
@Composable
internal fun ArticleLinkCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Embed(modifier, onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PostIcons.Article, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Text(
                stringResource(Res.string.posts_article_on_x),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Why a post its author keeps for paid subscribers stops short: X sends only its start. */
@Composable
internal fun SubscriberPreviewLine(screenName: String?, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PostIcons.LockOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            if (screenName.isNullOrEmpty()) stringResource(Res.string.posts_subscribers_only_of_author) else stringResource(Res.string.posts_subscribers_only, screenName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * A broadcast or Space the post carries: its picture, played on a tap
 * (broadcasts.dart). [onOpen] plays it where the platform can, else opens
 * its page.
 */
@Composable
internal fun LiveBlock(live: XLive, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (live.isSpace) Res.string.posts_spaces else Res.string.posts_filter_broadcasts)
    Embed(modifier, onOpen) {
        Box(Modifier.fillMaxWidth().aspectRatio(live.aspectRatio.coerceIn(1.2f, 2.4f)).background(Color.Black)) {
            live.imageUrl?.let { image ->
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            PlayBadge(Modifier.align(Alignment.Center))
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (live.isSpace) PostIcons.Mic else PostIcons.Sensors, contentDescription = label, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * A shared Grok conversation: the question asked, and the start of the
 * answer beside Grok's picture (grok_share_card.dart); a tap opens it in
 * XTA's browser, as a card of [post] does.
 */
@Composable
internal fun GrokShareCard(share: XGrokShare, post: XPost, actions: PostActions, modifier: Modifier = Modifier) {
    val open = rememberCardOpener(post, actions)
    Embed(modifier, onClick = { open(share.url, null) }) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(0.2f))
                Text(
                    share.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(0.8f, fill = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (share.answer.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp)) {
                    val image = share.grokImageUrl
                    if (image != null) {
                        Avatar(image, 32.dp) { actions.openProfile(share.grokScreenName) }
                    } else {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer)
                                .clickable { actions.openProfile(share.grokScreenName) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PostIcons.Sparkle, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(share.answer, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A line with an icon above a card's author, as XTA's _TweetTileLeading draws it. */
@Composable
internal fun ContextRow(icon: ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .padding(start = 24.dp, bottom = 4.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
