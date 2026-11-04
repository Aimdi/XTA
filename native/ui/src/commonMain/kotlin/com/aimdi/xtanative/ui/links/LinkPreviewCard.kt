package com.aimdi.xtanative.ui.links

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.services.links.LinkPreview
import com.aimdi.xtanative.services.links.LinkPreviewLayout
import com.aimdi.xtanative.services.links.LinkPreviews
import com.aimdi.xtanative.ui.common.XIcons

private val CardShape = RoundedCornerShape(16.dp)
private val TileSize = 56.dp

/**
 * The one link preview every network's post shows (link_preview_card.dart):
 * compact, a small tile beside the domain and the title, as Threads draws an
 * article; large, the picture across the card with the description under
 * the title, for links whose picture is the point. A large card without a
 * picture is drawn compact.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkPreviewCard(
    url: String,
    title: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    imageUrl: String? = null,
    layout: LinkPreviewLayout = LinkPreviewLayout.COMPACT,
    onLongPress: (() -> Unit)? = null,
) {
    val domain = LinkPreviews.domain(url)
    val shown = LinkPreviews.title(url, title)
    val image = imageUrl?.trim()?.ifEmpty { null }
    Box(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .clearAndSetSemantics {
                contentDescription = "$domain, $shown"
                role = Role.Button
                onClick {
                    onTap()
                    true
                }
            },
    ) {
        if (layout == LinkPreviewLayout.LARGE && image != null) {
            Column {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                PreviewTexts(domain, shown, description, titleLines = 2, Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        } else {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PreviewTile(image)
                Spacer(Modifier.width(12.dp))
                PreviewTexts(domain, shown, null, titleLines = 3, Modifier.weight(1f))
            }
        }
    }
}

/** [preview]'s card, in the layout its kind and picture call for. */
@Composable
fun LinkPreviewCard(preview: LinkPreview, onTap: () -> Unit, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    LinkPreviewCard(preview.url, preview.title, onTap, modifier, preview.description, preview.imageUrl, preview.layout, onLongPress)
}

/**
 * The card of a link whose network sent none: the page's own preview, read
 * once ([LinkPreviewCache]); until it answers, or when it gives none, the
 * address alone. The card opens the link as a post's link.
 */
@Composable
fun FetchedLinkPreviewCard(url: String, modifier: Modifier = Modifier, from: LinkFrom? = null, onLongPress: (() -> Unit)? = null) {
    val links = LocalLinks.current
    val preview by produceState(LinkPreviewCache.shared.cached(url), url) {
        if (!LinkPreviewCache.shared.known(url)) value = LinkPreviewCache.shared.load(url)
    }
    val open = { links?.openPostLink(url, from, preview?.title) ?: Unit }
    val loaded = preview
    if (loaded != null) {
        LinkPreviewCard(loaded.copy(url = url), open, modifier, onLongPress)
    } else {
        LinkPreviewCard(url, null, open, modifier, onLongPress = onLongPress)
    }
}

@Composable
private fun PreviewTile(image: String?) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(TileSize).clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.08f).compositeOver(colors.surface)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(XIcons.Link, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(26.dp))
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PreviewTexts(domain: String, title: String, description: String?, titleLines: Int, modifier: Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(domain, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        val summary = description?.trim().orEmpty()
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
