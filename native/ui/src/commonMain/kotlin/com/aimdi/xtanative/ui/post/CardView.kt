package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.common.rememberLoadsManually
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.card_from
import com.aimdi.xtanative.ui.generated.resources.poll_ended_days
import com.aimdi.xtanative.ui.generated.resources.poll_ended_hours
import com.aimdi.xtanative.ui.generated.resources.poll_ended_minutes
import com.aimdi.xtanative.ui.generated.resources.poll_ended_months
import com.aimdi.xtanative.ui.generated.resources.poll_ended_years
import com.aimdi.xtanative.ui.generated.resources.poll_ends_days
import com.aimdi.xtanative.ui.generated.resources.poll_ends_hours
import com.aimdi.xtanative.ui.generated.resources.poll_ends_minutes
import com.aimdi.xtanative.ui.generated.resources.poll_no_votes
import com.aimdi.xtanative.ui.generated.resources.poll_votes
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.links.LinkFrom
import com.aimdi.xtanative.ui.links.LinkPreviewCard
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.media.LocalGalleryOpener
import com.aimdi.xtanative.ui.media.MediaGallery
import com.aimdi.xtanative.ui.media.MediaIcons
import com.aimdi.xtanative.ui.media.ViewerMedia
import com.aimdi.xtanative.ui.media.rememberPhotoSize
import java.time.Instant
import java.util.Locale
import org.jetbrains.compose.resources.PluralStringResource
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
    CardBody(card, post, modifier) { link -> open(link.url, link.title.ifBlank { null }) }
}

/** A card drawn without its post: its page opens in XTA's browser on its own. */
@Composable
fun CardView(card: XCard, modifier: Modifier = Modifier) {
    val links = LocalLinks.current
    CardBody(card, null, modifier) { link -> links?.openPostLink(link.url, title = link.title.ifBlank { null }) }
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

/** [card] as XTA draws its kind; [post], when there is one, is whose its media are, which a saved picture is named after. */
@Composable
private fun CardBody(card: XCard, post: XPost?, modifier: Modifier, open: (XCard.Link) -> Unit) {
    when (card) {
        is XCard.Link -> when {
            card.media.isNotEmpty() -> MediaCard(card, post, { open(card) }, modifier)
            PostView.drawsLargeCard(card) -> LargeLinkCard(card, { open(card) }, modifier)
            // XTA draws a small preview as every network's (link_preview_card.dart).
            else -> LinkPreviewCard(card.url, card.title, { open(card) }, modifier, imageUrl = card.imageUrl)
        }
        is XCard.Poll -> PollCard(card, post, modifier)
    }
}

private val CardShape = RoundedCornerShape(16.dp)

/** X's large preview: the picture, at the size the image quality asks for (_card.dart), with the title on it, the site under it. */
@Composable
private fun LargeLinkCard(card: XCard.Link, onOpen: () -> Unit, modifier: Modifier) {
    val picture = card.imageAt(rememberPhotoSize())
    val ratio = picture?.aspectRatio ?: 1.91f
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .clickable(onClick = onOpen),
        ) {
            AsyncImage(
                model = picture?.url,
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
 * A website card with its own media (_card.dart's _createWebsiteCard): the
 * video, which opens the page the card leads to, or the pictures of a
 * carousel along a row, which open in the viewer as a post's do; the title
 * (on the picture when there is one, under the row when there are several)
 * and the site under it lead to the page.
 */
@Composable
private fun MediaCard(card: XCard.Link, post: XPost?, onOpen: () -> Unit, modifier: Modifier) {
    val galleries = LocalGalleryOpener.current
    val username = post?.author?.screenName.orEmpty()
    val postId = post?.id.orEmpty()
    val gallery = remember(card, username, postId) { MediaGallery.of(ViewerMedia.of(card.media, username, postId).pages) }
    val openMedia: (Int) -> Unit = { index -> if (card.media[index].kind != XMedia.Kind.VIDEO && galleries != null) galleries(gallery, index) else onOpen() }
    val several = card.media.size > 1
    Column(modifier.fillMaxWidth()) {
        Box {
            MediaGrid(card.media, openMedia, username = username, postId = postId)
            if (!several && card.title.isNotBlank()) TitleOverPicture(card.title, Modifier.align(Alignment.BottomStart).clickable(onClick = onOpen))
        }
        if (several && card.title.isNotBlank()) {
            Text(
                card.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = 8.dp),
            )
        }
        CardSite(card.domain, onOpen)
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
private fun CardSite(domain: String?, onOpen: (() -> Unit)? = null) {
    domain?.let {
        Text(
            stringResource(Res.string.card_from, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = (if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier).padding(top = 4.dp),
        )
    }
}

/**
 * A poll as counted when it was read (poll_results.dart): a bar per choice
 * filled to its share, the choice and its share on it, the leader in bold on
 * the accent, its picture before it opening full screen ([onPicture]), then
 * the votes and when the poll ends or ended. XTA Native never votes.
 */
@Composable
private fun PollCard(poll: XCard.Poll, post: XPost?, modifier: Modifier) {
    val pictures = rememberPollPictures(poll, post)
    val locale = currentLocale()
    // The opened post's poll grows its bars as it opens; anywhere else, and with reduced motion, it is full at once.
    val reduced = LocalReducedMotion.current
    val fill = LocalFocalPollFill.current?.takeIf { post != null && post.id == postScope().focalId }
    if (fill != null) LaunchedEffect(fill, reduced) { fill.start(reduced) }
    val t = if (fill == null || reduced) 1f else fill.progress
    Column(modifier.fillMaxWidth()) {
        poll.choices.forEachIndexed { index, choice -> PollChoiceBar(poll, choice, locale, pictures, PollFill.growth(t, index)) }
        Text(
            pollLine(poll, locale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** What a poll's pictures do (_card.dart's _openPollImages): load unless media loads manually, and open every choice's full picture in the viewer. */
private class PollPictures(val load: Boolean, private val choices: List<XCard.Poll.Choice>, private val gallery: MediaGallery, private val open: ((MediaGallery, Int) -> Unit)?) {
    fun opener(choice: XCard.Poll.Choice): (() -> Unit)? = open?.let { show -> { show(gallery, choices.indexOf(choice).coerceAtLeast(0)) } }
}

@Composable
private fun rememberPollPictures(poll: XCard.Poll, post: XPost?): PollPictures {
    val load = !rememberLoadsManually()
    val open = LocalGalleryOpener.current
    return remember(poll, post, load, open) {
        val choices = poll.choices.filter { it.imageUrl != null }
        val pages = ViewerMedia.images(choices.mapNotNull { it.fullImageUrl ?: it.imageUrl }, post?.author?.screenName.orEmpty(), post?.id.orEmpty()).pages
        PollPictures(load, choices, MediaGallery.of(pages, asIs = true), open)
    }
}

/** A choice's picture at the start of its bar (poll_results.dart's PollChoiceImage), or only its sign while media loads manually. */
@Composable
private fun PollChoiceImage(url: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(PollBar.IMAGE.dp)
            .clip(RoundedCornerShape(PollBar.RADIUS.dp))
            .background(muted.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(MediaIcons.ImageOutlined, contentDescription = null, tint = muted)
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * One choice's bar, filled to its share as far as it has [grown]: its
 * picture at the start, as far in as from the top, opening them all on a
 * tap; the choice; its share at the end. A screen reader hears it as one,
 * "Media, Yes, 62.5%", a button where it has a picture.
 */
@Composable
private fun PollChoiceBar(poll: XCard.Poll, choice: XCard.Poll.Choice, locale: Locale, pictures: PollPictures, grown: Float) {
    val share = poll.share(choice)
    val ahead = poll.leads(choice)
    val shape = RoundedCornerShape(PollBar.RADIUS.dp)
    val weight = if (ahead) FontWeight.Bold else FontWeight.Normal
    val percent = PollWords.percent(share, locale)
    val media = stringResource(Res.string.reader_media)
    val opener = choice.imageUrl?.let { pictures.opener(choice) }
    val image = choice.imageUrl?.takeIf { opener != null }
    val said = if (image != null) "$media\n${choice.label}\n$percent" else "${choice.label}\n$percent"
    Box(
        Modifier
            .padding(vertical = PollBar.GAP.dp)
            .fillMaxWidth()
            .heightIn(min = PollBar.MIN_HEIGHT.dp)
            .clearAndSetSemantics {
                contentDescription = said
                if (opener != null) {
                    role = Role.Button
                    onClick { opener(); true }
                }
            },
    ) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(PollBar.fill(share, grown))
                    .clip(shape)
                    .background(if (ahead) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PollBar.MIN_HEIGHT.dp)
                .padding(start = PollBar.start(image != null).dp, top = PollBar.INSET.dp, end = PollBar.END.dp, bottom = PollBar.INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (image != null && opener != null) {
                Box(Modifier.clip(RoundedCornerShape(PollBar.RADIUS.dp)).clickable(onClick = opener)) { PollChoiceImage(image.takeIf { pictures.load }) }
                Spacer(Modifier.width(PollBar.END.dp))
            }
            Text(choice.label, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(PollBar.END.dp))
            Text(percent, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        }
    }
}

/** "1,234 votes · Ends in 2 hours", "No votes · Ended 3 days ago": the votes, then when it ends where X says. */
@Composable
private fun pollLine(poll: XCard.Poll, locale: Locale): String {
    val total = poll.total
    val votes = if (total == 0L) {
        stringResource(Res.string.poll_no_votes)
    } else {
        pluralStringResource(Res.plurals.poll_votes, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), PollWords.votes(total, locale))
    }
    val end = PollWords.end(poll.endsAt, Instant.now()) ?: return votes
    return votes + " · " + pluralStringResource(pollPhrase(end.phrase), end.n, end.n)
}

private fun pollPhrase(phrase: PollPhrase): PluralStringResource = when (phrase) {
    PollPhrase.ENDS_MINUTES -> Res.plurals.poll_ends_minutes
    PollPhrase.ENDS_HOURS -> Res.plurals.poll_ends_hours
    PollPhrase.ENDS_DAYS -> Res.plurals.poll_ends_days
    PollPhrase.ENDED_MINUTES -> Res.plurals.poll_ended_minutes
    PollPhrase.ENDED_HOURS -> Res.plurals.poll_ended_hours
    PollPhrase.ENDED_DAYS -> Res.plurals.poll_ended_days
    PollPhrase.ENDED_MONTHS -> Res.plurals.poll_ended_months
    PollPhrase.ENDED_YEARS -> Res.plurals.poll_ended_years
}
