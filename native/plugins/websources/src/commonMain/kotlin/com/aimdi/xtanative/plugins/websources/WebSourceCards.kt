package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.SubcomposeAsyncImage
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.networks.websources.WebSourceItem
import com.aimdi.xtanative.networks.websources.WebSourceLayout
import com.aimdi.xtanative.networks.websources.WebSourceReading
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.MediaTiles
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a source's post does when tapped and what its pictures open: the screen's opener decides. */
internal class ItemActions(
    val open: (WebSourceItem) -> Unit,
    val openLink: (String) -> Unit,
    val openPictures: (WebSourceItem, Int) -> Unit,
    /** The shared post menu (showWebSourceItemActions): save it, its note, its link shared or opened. */
    val more: (WebSourceItem) -> Unit,
)

/**
 * A source's post (web_source_item_card.dart) as its layout draws it: a
 * link, an article, or a post with its pictures. [showSource] names the
 * source above it, where several sources' posts mix. A long press opens
 * the shared post menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WebSourceItemCard(source: WebSourceDefinition, item: WebSourceItem, actions: ItemActions, showSource: Boolean = false) {
    Box {
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = { actions.open(item) }, onLongClick = { actions.more(item) }).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            when (source.layout) {
                WebSourceLayout.LINKS -> LinkBody(source, item, showSource, actions)
                WebSourceLayout.ARTICLES -> ArticleBody(source, item, showSource)
                WebSourceLayout.POSTS, WebSourceLayout.GALLERY -> PostBody(source, item, showSource, actions)
            }
        }
    }
    Hairline()
}

/**
 * A source's post the app draws among others' (Home's timelines, the
 * groups), as XTA's draws it there: its card naming the source, opening as
 * the source's own lists open it.
 */
@Composable
internal fun SharedItemCard(app: WebSourcesApp, source: WebSourceDefinition, item: WebSourceItem) =
    WebSourceItemCard(source, item, rememberItemActions(app, source), showSource = true)

private fun hostOf(link: String?): String? = link?.toHttpUrlOrNull()?.host

@Composable
private fun MetaLine(parts: List<String?>) {
    val shown = parts.filterNotNull().filter { it.isNotEmpty() }
    if (shown.isEmpty()) return
    Text(shown.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun LinkBody(source: WebSourceDefinition, item: WebSourceItem, showSource: Boolean, actions: ItemActions) {
    MetaLine(
        listOf(
            source.name.takeIf { showSource },
            hostOf(item.link),
            item.score?.let { pluginPlural(Strings.points, it.toInt(), it) },
            item.author,
            item.date?.let { agoText(it) },
        ),
    )
    Spacer(Modifier.height(6.dp))
    Text(item.title ?: item.text.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
    item.discussion?.let { discussion ->
        TextButton(onClick = { actions.openLink(discussion) }) {
            Icon(WebSourcesIcons.ChatBubble, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val comments = item.comments
            Text(if (comments == null) pluginString(Strings.discussion) else pluginPlural(Strings.comments, comments.toInt(), comments))
        }
    }
}

@Composable
private fun ArticleBody(source: WebSourceDefinition, item: WebSourceItem, showSource: Boolean) {
    val cover = item.image
    val largeText = LocalDensity.current.fontScale * 14 > 20
    MetaLine(listOf(source.name.takeIf { showSource }, item.author, item.date?.let { agoText(it) }))
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            item.title?.let { Text(it, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 1.3.em), fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            item.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        }
        if (cover != null && !largeText) {
            Spacer(Modifier.width(16.dp))
            Cover(cover, Modifier.size(88.dp))
        }
    }
    if (cover != null && largeText) Cover(cover, Modifier.padding(top = 12.dp).fillMaxWidth().height(140.dp))
}

@Composable
private fun PostBody(source: WebSourceDefinition, item: WebSourceItem, showSource: Boolean, actions: ItemActions) {
    Row(verticalAlignment = Alignment.Top) {
        Avatar(source, item.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.author ?: source.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                val meta = listOfNotNull(source.name.takeIf { showSource && item.author != null }, item.date?.let { agoText(it) })
                if (meta.isNotEmpty()) {
                    Text(" · " + meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            item.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item.text?.let { Text(it, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            if (item.pictures.isNotEmpty()) {
                MediaTiles(item.pictures.map { MediaItem(imageUrl = it, alt = item.title) }, { index -> actions.openPictures(item, index) }, Modifier.padding(top = 8.dp))
            }
            MetaLine(
                listOf(
                    item.score?.let { pluginPlural(Strings.points, it.toInt(), it) },
                    item.comments?.let { pluginPlural(Strings.comments, it.toInt(), it) },
                ),
            )
        }
    }
}

@Composable
private fun Avatar(source: WebSourceDefinition, url: String?) {
    if (url == null) return WebSourceMark(source, 40.dp)
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(40.dp).clip(CircleShape),
        loading = { WebSourceMark(source, 40.dp) },
        error = { WebSourceMark(source, 40.dp) },
    )
}

@Composable
private fun Cover(url: String, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        error = { PictureBox { Icon(WebSourcesIcons.Article, null) } },
    )
}

/** A gallery's picture: tapping it opens every picture of the list; a long press opens the shared post menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WebSourcePictureTile(item: WebSourceItem, onMore: () -> Unit, onOpen: () -> Unit) {
    Box(Modifier.aspectRatio(1f).padding(1.dp).combinedClickable(onClick = onOpen, onLongClick = onMore)) {
        val picture = item.pictures.firstOrNull()
        if (picture == null) {
            PictureBox {}
        } else {
            SubcomposeAsyncImage(
                model = picture,
                contentDescription = item.title ?: item.text,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { PictureBox {} },
                error = { PictureBox { Icon(WebSourcesIcons.BrokenImage, null) } },
            )
        }
    }
}

/** Where a picture is not shown: loading, or failed. */
@Composable
private fun PictureBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { content() }
}

/** The pictures a gallery opens from [item] ([WebSourceReading.pictured]), as the viewer shows them. */
internal fun galleryPictures(items: List<WebSourceItem>, item: WebSourceItem): Pair<List<MediaItem>, Int>? =
    WebSourceReading.pictured(items, item)?.let { (pictured, start) -> pictured.map { MediaItem(imageUrl = it.pictures.first(), alt = it.title) } to start }
