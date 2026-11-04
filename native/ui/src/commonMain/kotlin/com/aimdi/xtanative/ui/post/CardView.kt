package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.card_from
import com.aimdi.xtanative.ui.generated.resources.poll_days_left
import com.aimdi.xtanative.ui.generated.resources.poll_final
import com.aimdi.xtanative.ui.generated.resources.poll_hours_left
import com.aimdi.xtanative.ui.generated.resources.poll_minutes_left
import com.aimdi.xtanative.ui.generated.resources.poll_votes
import com.aimdi.xtanative.ui.links.LinkFrom
import com.aimdi.xtanative.ui.links.LinkPreviewCard
import com.aimdi.xtanative.ui.links.LocalLinks
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A post's link preview, the website card X builds itself (its video or
 * carousel), or a poll (_card.dart). Its page opens in XTA's browser, with
 * [post] in reach in the browser's bar.
 */
@Composable
fun CardView(card: XCard, post: XPost, actions: PostActions, modifier: Modifier = Modifier) {
    val open = rememberCardOpener(post, actions)
    CardBody(card, modifier) { link -> open(link.url, link.title.ifBlank { null }) }
}

/** A card drawn without its post: its page opens in XTA's browser on its own. */
@Composable
fun CardView(card: XCard, modifier: Modifier = Modifier) {
    val links = LocalLinks.current
    CardBody(card, modifier) { link -> links?.openPostLink(link.url, title = link.title.ifBlank { null }) }
}

/**
 * How a post's card opens the page it leads to (_card.dart's _openLink): in
 * XTA's browser, with [post] in its bar and the title the card gives shown
 * while the page loads.
 */
@Composable
internal fun rememberCardOpener(post: XPost, actions: PostActions): (url: String, title: String?) -> Unit {
    val links = LocalLinks.current
    val settings = LocalGraph.current.settings
    val onPostScreen = postScope().focalId != null
    return remember(links, settings, post, actions, onPostScreen) {
        { url, title -> links?.openPostLink(url, LinkFrom.card(post, settings.state.value, onPostScreen) { actions.openPost(post.id) }, title) }
    }
}

@Composable
private fun CardBody(card: XCard, modifier: Modifier, open: (XCard.Link) -> Unit) {
    when (card) {
        is XCard.Link -> when {
            card.media.isNotEmpty() -> MediaCard(card, { open(card) }, modifier)
            card.large && card.imageUrl != null -> LargeLinkCard(card, { open(card) }, modifier)
            // XTA draws a small preview as every network's (link_preview_card.dart).
            else -> LinkPreviewCard(card.url, card.title, { open(card) }, modifier, imageUrl = card.imageUrl)
        }
        is XCard.Poll -> PollCard(card, modifier)
    }
}

private val CardShape = RoundedCornerShape(16.dp)

/** X's large preview: the picture with the title on it, the site under it. */
@Composable
private fun LargeLinkCard(card: XCard.Link, onOpen: () -> Unit, modifier: Modifier) {
    val ratio = card.imageWidth?.let { w -> card.imageHeight?.takeIf { it > 0 }?.let { h -> w.toFloat() / h } } ?: 1.91f
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .clickable(onClick = onOpen),
        ) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(1f, 2.2f)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            TitleOverPicture(card.title, Modifier.align(Alignment.BottomStart))
        }
        CardSite(card.domain)
    }
}

/**
 * A website card with its own media: the video, or the pictures of a
 * carousel, each opening the page the card leads to, with its title on it.
 */
@Composable
private fun MediaCard(card: XCard.Link, onOpen: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box {
            MediaGrid(card.media, onOpen = { onOpen() })
            if (card.title.isNotBlank()) TitleOverPicture(card.title, Modifier.align(Alignment.BottomStart))
        }
        CardSite(card.domain)
    }
}

@Composable
private fun TitleOverPicture(title: String, modifier: Modifier) {
    Text(
        title,
        color = Color.White,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun CardSite(domain: String?) {
    domain?.let {
        Text(
            stringResource(Res.string.card_from, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A poll as counted when it was read, each choice's picture beside it: XTA Native never votes. */
@Composable
private fun PollCard(poll: XCard.Poll, modifier: Modifier) {
    val total = poll.total
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.choices.forEach { choice ->
            val share = poll.share(choice).toFloat()
            val ahead = poll.leads(choice)
            Row(verticalAlignment = Alignment.CenterVertically) {
                choice.imageUrl?.let { image ->
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(end = 8.dp).size(34.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(6.dp))) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(share.coerceAtLeast(0.015f))
                            .background(if (ahead) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            choice.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (ahead) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = if (ahead) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Text(
            pluralStringResource(Res.plurals.poll_votes, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), total) + " · " + pollState(poll),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun pollState(poll: XCard.Poll): String {
    val ends = poll.endsAt
    val left = ends?.let { Duration.between(Instant.now(), it) }
    return when {
        poll.final || left == null || left.isNegative -> stringResource(Res.string.poll_final)
        left.toDays() >= 1 -> pluralStringResource(Res.plurals.poll_days_left, left.toDays().toInt(), left.toDays())
        left.toHours() >= 1 -> pluralStringResource(Res.plurals.poll_hours_left, left.toHours().toInt(), left.toHours())
        else -> pluralStringResource(Res.plurals.poll_minutes_left, left.toMinutes().toInt().coerceAtLeast(1), left.toMinutes().coerceAtLeast(1))
    }
}
