package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.format.Formats
import com.aimdi.xtanative.services.links.LinkPostContext
import com.aimdi.xtanative.services.links.NetworkLink
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_link_back_to_post
import com.aimdi.xtanative.ui.generated.resources.reader_link_likes
import com.aimdi.xtanative.ui.generated.resources.reader_link_replies
import com.aimdi.xtanative.ui.generated.resources.reader_link_reposts
import com.aimdi.xtanative.ui.generated.resources.reader_link_share_post
import com.aimdi.xtanative.ui.generated.resources.reader_network_bluesky
import com.aimdi.xtanative.ui.generated.resources.reader_network_hackernews
import com.aimdi.xtanative.ui.generated.resources.reader_network_instagram
import com.aimdi.xtanative.ui.generated.resources.reader_network_mastodon
import com.aimdi.xtanative.ui.generated.resources.reader_network_pixiv
import com.aimdi.xtanative.ui.generated.resources.reader_network_reddit
import com.aimdi.xtanative.ui.generated.resources.reader_network_substack
import com.aimdi.xtanative.ui.generated.resources.reader_network_threads
import com.aimdi.xtanative.ui.generated.resources.reader_network_tiktok
import com.aimdi.xtanative.ui.generated.resources.reader_network_x
import com.aimdi.xtanative.ui.media.ReaderIcons
import java.text.DecimalFormatSymbols
import org.jetbrains.compose.resources.stringResource

private val AvatarSize = 32.dp

/**
 * The post a page came from, floating over the page (link_post_context_bar.dart):
 * its author, its counts and its network. Tapping it goes back to the post;
 * [onSharePost], when there is one, shares the post's own link. The counts
 * are for reading only, as on the cards: nothing here posts, likes or
 * reposts anywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkPostContextBar(post: LinkPostContext, onBackToPost: () -> Unit, onSharePost: (() -> Unit)?, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val decimal = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val counts = LinkPostBar.counts(post, LinkPostBar.countsHidden(settings)) { Formats.count(it, decimal) }
    val back = stringResource(Res.string.reader_link_back_to_post)
    val spoken = counts.filter { it.shown.isNotEmpty() }.map { stringResource(countLabel(it.kind), it.shown) }
    val label = (listOf(back) + listOfNotNull(post.author.ifEmpty { null }) + spoken).joinToString(", ")
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = colors.primary.copy(alpha = 0.06f).compositeOver(colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onBackToPost)
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                        onClick {
                            onBackToPost()
                            true
                        }
                    }
                    .padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarAvatar(post)
                Spacer(Modifier.width(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    counts.forEach { count -> BarCountItem(countIcon(count.kind), count.shown) }
                }
            }
            if (onSharePost != null) {
                IconButton(onClick = onSharePost) {
                    Icon(ReaderIcons.SendOutlined, stringResource(Res.string.reader_link_share_post), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            NetworkMark(post.network)
        }
    }
}

@Composable
private fun BarCountItem(icon: ImageVector, shown: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        if (shown.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The author's picture, over a letter that stands in until it loads or when there is none. */
@Composable
private fun BarAvatar(post: LinkPostContext) {
    Box(
        Modifier.size(AvatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(LinkPostBar.monogram(post), color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        val url = post.avatarUrl?.trim().orEmpty()
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(AvatarSize).clip(CircleShape))
        }
    }
}

/** The network's mark at the bar's end: X's own; the others leave the room empty until their screens bring one. */
@Composable
private fun NetworkMark(network: String) {
    if (network == LinkPostContext.X) {
        Icon(ReaderIcons.XMark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 14.dp).size(22.dp))
    } else {
        Spacer(Modifier.width(12.dp))
    }
}

private fun countIcon(kind: BarCount.Kind): ImageVector = when (kind) {
    BarCount.Kind.LIKES -> XIcons.Like
    BarCount.Kind.REPLIES -> XIcons.Reply
    BarCount.Kind.REPOSTS -> XIcons.Repost
}

private fun countLabel(kind: BarCount.Kind) = when (kind) {
    BarCount.Kind.LIKES -> Res.string.reader_link_likes
    BarCount.Kind.REPLIES -> Res.string.reader_link_replies
    BarCount.Kind.REPOSTS -> Res.string.reader_link_reposts
}

/** A network's name, the way the browser screen's bar names where a page came from; null for one it does not know. */
@Composable
internal fun networkName(network: String): String? {
    val name = when (network) {
        LinkPostContext.X -> Res.string.reader_network_x
        NetworkLink.THREADS -> Res.string.reader_network_threads
        NetworkLink.BLUESKY -> Res.string.reader_network_bluesky
        NetworkLink.MASTODON -> Res.string.reader_network_mastodon
        NetworkLink.REDDIT -> Res.string.reader_network_reddit
        NetworkLink.HACKERNEWS -> Res.string.reader_network_hackernews
        NetworkLink.SUBSTACK -> Res.string.reader_network_substack
        NetworkLink.TIKTOK -> Res.string.reader_network_tiktok
        NetworkLink.INSTAGRAM -> Res.string.reader_network_instagram
        NetworkLink.PIXIV -> Res.string.reader_network_pixiv
        else -> null
    }
    return name?.let { stringResource(it) }
}
