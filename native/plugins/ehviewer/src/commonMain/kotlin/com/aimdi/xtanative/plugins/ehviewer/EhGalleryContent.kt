package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.size.Size as ImageSize
import com.aimdi.xtanative.networks.ehviewer.EhComment
import com.aimdi.xtanative.networks.ehviewer.EhGalleryState
import com.aimdi.xtanative.networks.ehviewer.EhPreview
import com.aimdi.xtanative.networks.ehviewer.EhTag
import com.aimdi.xtanative.networks.ehviewer.EhTagGroup
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberImageRequest

/** How many comments the page shows before Show all. */
private const val COMMENTS_SHOWN = 3

/**
 * The gallery's tags under their namespaces, in the site's order
 * (eh_gallery_tags.dart): from 480 dp the namespaces sit in a column beside
 * their tags, as on the site; narrower, each heads its own run. A tap
 * searches for the tag exactly, a long press copies it.
 */
@Composable
internal fun GalleryTags(app: EhApp, tags: List<String>, weakTags: Set<String>) {
    val groups = remember(tags) { EhTagGroup.of(tags) }
    if (groups.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(pluginString(Strings.tags))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val beside = maxWidth >= 480.dp
            Column(Modifier.fillMaxWidth()) {
                groups.forEach { group -> TagGroup(app, group, weakTags, beside) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(app: EhApp, group: EhTagGroup, weakTags: Set<String>, beside: Boolean) {
    val chips: @Composable (Modifier) -> Unit = { modifier ->
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.tags.forEach { tag -> TagChip(app, tag, group, tag.raw in weakTags) }
        }
    }
    if (beside) {
        Row(Modifier.fillMaxWidth()) {
            NamespaceLabel(group, Modifier.width(104.dp * LocalDensity.current.fontScale).padding(top = 14.dp))
            chips(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            NamespaceLabel(group, Modifier.padding(top = 8.dp))
            chips(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NamespaceLabel(group: EhTagGroup, modifier: Modifier) {
    Text(
        pluginString(EhText.namespace(group.namespace)),
        style = MaterialTheme.typography.labelLarge,
        color = namespaceColor(group.namespace) ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * A tag as XTA's tag chips draw it, tinted with its namespace's colour; a
 * tag not settled by votes yet ([weak]) is outlined in dashes and unfilled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(app: EhApp, tag: EhTag, group: EhTagGroup, weak: Boolean) {
    val copyText = rememberCopyText()
    val color = namespaceColor(group.namespace)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val text = color ?: MaterialTheme.colorScheme.onSurface
    val border = color?.copy(alpha = if (dark) 0.5f else 0.4f) ?: MaterialTheme.colorScheme.outlineVariant
    val fill = if (weak) Color.Transparent else color?.copy(alpha = if (dark) 0.16f else 0.09f) ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val copied = pluginString(Strings.gallery_tag_copied, tag.raw)
    val spoken = if (weak) pluginString(Strings.gallery_weak_tag, tag.name) else tag.name
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.defaultMinSize(minHeight = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            tag.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(shape)
                .background(fill)
                .then(if (weak) Modifier.dashedOutline(border) else Modifier.border(1.dp, border, shape))
                .combinedClickable(
                    onClick = { app.openSearch(tag.query) },
                    onLongClick = {
                        copyText(tag.raw)
                        app.notices.tell(copied)
                    },
                    onLongClickLabel = pluginString(Strings.gallery_copy_tag),
                )
                .semantics { contentDescription = spoken }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** A dashed rounded outline in [color]. */
private fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(10.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** The first comments, and a button that opens all of them in a sheet (eh_gallery_comments.dart). */
@Composable
internal fun GalleryComments(comments: List<EhComment>, onShowAll: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(pluginString(Strings.comments))
        if (comments.isEmpty()) {
            Text(pluginString(Strings.empty_comments), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
        }
        comments.take(COMMENTS_SHOWN).forEach { CommentTile(it, maxLines = 4) }
        if (comments.size > COMMENTS_SHOWN) {
            TextButton(onClick = onShowAll, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                Text(pluginPlural(Strings.gallery_all_comments, comments.size, comments.size))
            }
        }
    }
}

/** Every comment, in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllComments(comments: List<EhComment>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    pluginString(Strings.comments),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() },
                )
            }
            items(comments) { CommentTile(it, maxLines = Int.MAX_VALUE) }
        }
    }
}

/** Who wrote it (marked when it is the uploader), when and its score, then the text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentTile(comment: EhComment, maxLines: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(comment.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (comment.uploader) {
                Text(
                    pluginString(Strings.gallery_uploader_comment),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (comment.posted.isNotEmpty()) Text(comment.posted, style = MaterialTheme.typography.labelMedium, color = muted)
            comment.score?.takeIf(String::isNotEmpty)?.let { score ->
                val spoken = pluginString(Strings.gallery_comment_score, score)
                Text(score, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.semantics { contentDescription = spoken })
            }
        }
        Text(comment.body, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A page's tile, numbered, opening the reader there (eh_gallery_previews.dart). */
@Composable
internal fun PreviewTile(app: EhApp, preview: EhPreview, total: Int, onOpen: () -> Unit) {
    val spoken = pluginString(Strings.page_of, preview.page, total)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            SpriteThumb(app, preview, Modifier.fillMaxSize())
            Text(
                "${preview.page}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Color.Black.copy(alpha = 0.54f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * One page's tile cut from the site's preview sprite sheet and fitted whole
 * into its box. The sheet is decoded at its own size: the site gives the
 * tile's place on it in sheet pixels.
 */
@Composable
private fun SpriteThumb(app: EhApp, preview: EhPreview, modifier: Modifier) {
    val url = preview.thumbUrl ?: return
    val asked = rememberImageRequest(url, app.client.imageHeaders(url))
    val request = remember(asked) { asked.newBuilder().size(ImageSize.ORIGINAL).build() }
    val painter = rememberAsyncImagePainter(request)
    val loaded by painter.state.collectAsState()
    Canvas(modifier) {
        if (loaded !is AsyncImagePainter.State.Success) return@Canvas
        val sheet = painter.intrinsicSize
        if (sheet.width.isNaN() || sheet.height.isNaN() || sheet.width <= 0f || sheet.height <= 0f) return@Canvas
        val crop = preview.spriteCrop(sheet.width.toDouble(), sheet.height.toDouble())
        if (crop.width <= 0.0 || crop.height <= 0.0) return@Canvas
        val scale = minOf(size.width / crop.width.toFloat(), size.height / crop.height.toFloat())
        val width = crop.width.toFloat() * scale
        val height = crop.height.toFloat() * scale
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        clipRect(left, top, left + width, top + height) {
            translate(left - crop.left.toFloat() * scale, top - crop.top.toFloat() * scale) {
                with(painter) { draw(Size(sheet.width * scale, sheet.height * scale)) }
            }
        }
    }
}

/** Under the previews: a spinner while the next sheet loads, else the button that reads it, and tries a failed one again. */
@Composable
internal fun PreviewFooter(state: EhGalleryState, onMore: () -> Unit) {
    when {
        state.loadingMore -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        state.hasMorePreviews -> OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).heightIn(min = 48.dp)) {
            Icon(if (state.moreFailed) Icons.Filled.Refresh else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            Text(pluginString(Strings.load_more_previews), Modifier.padding(start = 8.dp))
        }
    }
}
