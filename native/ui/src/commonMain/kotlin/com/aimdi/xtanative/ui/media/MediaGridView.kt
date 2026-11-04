package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.store.settings.MediaGridLayout
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.MediaBadge
import com.aimdi.xtanative.ui.common.ShowMediaTile
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.common.datePattern
import com.aimdi.xtanative.ui.common.mediaShown
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_filter_broadcasts
import com.aimdi.xtanative.ui.generated.resources.posts_filter_photos
import com.aimdi.xtanative.ui.generated.resources.posts_filter_videos
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.posts_spaces
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the viewer over a gallery from its page [Int]: the app's navigation
 * gives it, as the viewer is a screen of its own. Without one a grid's tile
 * opens its post's own media.
 */
val LocalGalleryOpener = staticCompositionLocalOf<((MediaGallery, Int) -> Unit)?> { null }

/** The size a post's photos are shown at, by Settings › Media's image quality (_media.dart). */
@Composable
fun rememberPhotoSize(): PhotoSize {
    val settings by LocalGraph.current.settings.state.collectAsState()
    return MediaFiles.viewerSize(settings?.get(MediaPrefs.imageQuality) ?: MediaQuality.MEDIUM)
}

/** The grid Settings › Media asks for: its layout and columns. */
@Composable
fun rememberMediaGridLook(): MediaGridLook {
    val settings by LocalGraph.current.settings.state.collectAsState()
    val layout = settings?.get(MediaPrefs.gridLayout) ?: MediaGridLayout.MASONRY
    val columns = settings?.get(MediaPrefs.gridColumns) ?: 3
    return remember(layout, columns) { MediaGridLook.of(layout, columns) }
}

/** [items] laid out in [look], made again only when either changes. */
@Composable
fun rememberMediaGridPlan(items: List<MediaGridItem>, look: MediaGridLook): MediaGridPlan = remember(items, look) { MediaGridPlan(items, look) }

/** What a grid keeps while it is on screen: the tiles a tap revealed while media loads manually, by key. */
@Stable
class MediaGridState {
    val revealed = mutableStateMapOf<String, Boolean>()
}

@Composable
fun rememberMediaGridState(): MediaGridState = remember { MediaGridState() }

/** What a list draws a media grid with ([mediaGrid]): its plan, what it keeps, where a tap and a long press lead, and how a tile's post opens. */
class MediaGridRows(
    val plan: MediaGridPlan,
    val state: MediaGridState,
    val onOpen: (MediaGridItem) -> Unit,
    val onLongPress: ((MediaGridItem) -> Unit)?,
    val onOpenPost: (MediaGridItem) -> Unit,
)

/**
 * A grid of [items] in [look], whose tiles open the viewer over every one
 * loaded, at the one tapped (media_grid_lightbox.dart), paging on into what
 * [updates] brings as the viewer asks for more ([loadMore]); a broadcast
 * with nothing to play here opens its post. [onLongPress] is what a long
 * press on a tile does, if anything.
 */
@Composable
fun rememberMediaGridRows(
    items: List<MediaGridItem>,
    actions: PostActions,
    updates: Flow<List<MediaGridItem>>? = null,
    loadMore: () -> Unit = {},
    look: MediaGridLook = rememberMediaGridLook(),
    onLongPress: ((MediaGridItem) -> Unit)? = null,
): MediaGridRows {
    val plan = rememberMediaGridPlan(items, look)
    val state = rememberMediaGridState()
    val opener = LocalGalleryOpener.current
    val more by rememberUpdatedState(loadMore)
    val open = remember(items, updates, actions, opener) {
        { item: MediaGridItem ->
            val gallery = MediaGallery.ofGrid(items, updates ?: flowOf(items)) { more() }
            val start = gallery.startOf(item)
            when {
                start == null -> actions.openPost(item.post.id)
                opener != null -> opener(gallery, start)
                else -> actions.openMedia(item.post.id, item.mediaIndex)
            }
        }
    }
    return remember(plan, state, open, onLongPress, actions) { MediaGridRows(plan, state, open, onLongPress) { item -> actions.openPost(item.post.id) } }
}

/**
 * A media grid as rows of the list it is in (media_grid.dart): [grid]'s
 * tiles at their own shapes, a masonry in its columns, one a row, or a
 * profile's Livestreams as cards. While media loads manually, each waits
 * behind "Tap to show …".
 */
fun LazyListScope.mediaGrid(grid: MediaGridRows) {
    val plan = grid.plan
    val look = plan.look
    item(key = "media-grid-top", contentType = "media-grid-space") { Spacer(Modifier.height(look.padding.top.dp)) }
    itemsIndexed(plan.masonry.slices, key = { index, _ -> "media-slice-$index" }, contentType = { _, _ -> "media-slice" }) { _, slice ->
        when {
            look.broadcastCards -> BroadcastSlice(grid, slice)
            plan.masonry.columns == 1 -> SingleSlice(grid, slice)
            else -> MasonrySliceView(grid, slice)
        }
    }
    item(key = "media-grid-bottom", contentType = "media-grid-space") { Spacer(Modifier.height(look.padding.bottom.dp)) }
}

/** A tile a row of its own: XTA's Timeline layout, a picture across the width. */
@Composable
private fun SingleSlice(grid: MediaGridRows, slice: MasonrySlice) {
    val look = grid.plan.look
    val index = slice.cells.single()
    val item = grid.plan.items[index]
    Box(Modifier.fillMaxWidth().padding(start = look.padding.start.dp, end = look.padding.end.dp, bottom = look.spacing.dp)) {
        GridTile(item, index, grid, Modifier.fillMaxWidth().aspectRatio(look.aspectOf(item)), read = true)
    }
}

/**
 * A broadcast or Space as a profile's Livestreams show it
 * (broadcast_media_card.dart): its preview, then, outside it so that a
 * Space without a picture still says what it is, what its post says, who
 * and when, its kind, and "Open post".
 */
@Composable
private fun BroadcastSlice(grid: MediaGridRows, slice: MasonrySlice) {
    val look = grid.plan.look
    val index = slice.cells.single()
    val item = grid.plan.items[index]
    val shape = RoundedCornerShape(look.radius.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val type = typeLabel(item)
    Box(Modifier.fillMaxWidth().padding(start = look.padding.start.dp, end = look.padding.end.dp, bottom = look.spacing.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            GridTile(item, index, grid, Modifier.fillMaxWidth().aspectRatio(look.aspectOf(item)), read = true)
            Text(
                item.excerpt ?: type,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 4.dp),
            )
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("@" + item.post.author.screenName, style = MaterialTheme.typography.bodySmall, color = muted)
                item.post.createdAt?.let { Text(dateText(it), style = MaterialTheme.typography.bodySmall, color = muted) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.isSpace) MediaIcons.GraphicEq else MediaIcons.LiveTvOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(type, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = { grid.onOpenPost(item) }) {
                    Icon(PostIcons.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.posts_open_post), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** "Nov 4, 2026", as the reader's language writes a day. */
@Composable
private fun dateText(then: Instant): String {
    val locale = currentLocale()
    val pattern = remember(locale) { datePattern(locale, "yMMMd") }
    return DateTimeFormatter.ofPattern(pattern, locale).format(then.atZone(ZoneId.systemDefault()))
}

/**
 * A stripe of a masonry: each tile it crosses, placed where the masonry
 * puts it and cut at the stripe's edges. Positions are rounded from the
 * whole grid's top, so a tile's two halves meet exactly.
 */
@Composable
private fun MasonrySliceView(grid: MediaGridRows, slice: MasonrySlice) {
    val look = grid.plan.look
    val masonry = grid.plan.masonry
    Layout(
        content = {
            slice.cells.forEach { index ->
                key(index) { GridTile(grid.plan.items[index], index, grid, Modifier.fillMaxSize(), read = masonry.cells[index].top >= slice.top) }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = look.padding.start.dp, end = look.padding.end.dp).clipToBounds(),
    ) { measurables, constraints ->
        val gap = look.spacing.dp.roundToPx()
        val pitch = (constraints.maxWidth + gap).toFloat() / masonry.columns
        fun at(units: Float): Int = (units * pitch).roundToInt()
        val top = at(slice.top)
        val placed = measurables.mapIndexed { i, measurable ->
            val cell = masonry.cells[slice.cells[i]]
            val x = at(cell.column.toFloat())
            val y = at(cell.top) - top
            val width = (at(cell.column + 1f) - gap - x).coerceAtLeast(0)
            val height = (at(cell.bottom) - gap - at(cell.top)).coerceAtLeast(0)
            Triple(measurable.measure(Constraints.fixed(width, height)), x, y)
        }
        layout(constraints.maxWidth, at(slice.bottom) - top) {
            placed.forEach { (placeable, x, y) -> placeable.placeRelative(x, y) }
        }
    }
}

/**
 * One tile (media_grid.dart's _MediaGridTile): the picture or the video's
 * poster with its badge, or "Tap to show …" while media loads manually.
 * [read] says whether a screen reader reads it here: a tile cut by a stripe
 * is read once, in the stripe it starts in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(item: MediaGridItem, index: Int, grid: MediaGridRows, modifier: Modifier, read: Boolean) {
    val shape = RoundedCornerShape(grid.plan.look.radius.dp)
    val type = typeLabel(item)
    val url = item.thumbnailUrl?.let { photoUrl(it, PhotoSize.MEDIUM) }
    val reveal = { grid.state.revealed[item.key] = true }
    val shown = mediaShown(url, grid.state.revealed[item.key] == true, reveal)
    val label = stringResource(Res.string.reader_media) + ": " + type + ", " + (index + 1) + "/" + grid.plan.items.size
    Box(
        modifier
            .clip(shape)
            .background(Color.Black)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .let { tile -> if (read) tile else tile.clearAndSetSemantics {} },
    ) {
        if (!shown) {
            ShowMediaTile(type, manualIcon(item), onShow = reveal, modifier = Modifier.fillMaxSize(), compact = true)
            return@Box
        }
        val long = grid.onLongPress
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(role = Role.Button, onClick = { grid.onOpen(item) }, onLongClick = long?.let { { it(item) } })
                .semantics { contentDescription = label },
        ) {
            url?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            TileMarks(item, type)
        }
    }
}

/** What a tile says of its kind: a GIF's or a video's badge, a video's and a broadcast's play sign, a broadcast's or a Space's name. */
@Composable
private fun TileMarks(item: MediaGridItem, type: String) {
    Box(Modifier.fillMaxSize()) {
        when (item.kind) {
            MediaGridItem.Kind.PHOTO -> Unit
            MediaGridItem.Kind.GIF -> MediaBadge(Modifier.align(Alignment.BottomStart).padding(8.dp), icon = MediaIcons.GifBoxOutlined, description = type)
            MediaGridItem.Kind.VIDEO -> {
                PlaySign(Modifier.align(Alignment.Center))
                MediaBadge(Modifier.align(Alignment.BottomStart).padding(8.dp), icon = MediaIcons.VideocamOutlined, description = type)
            }
            MediaGridItem.Kind.BROADCAST -> {
                PlaySign(Modifier.align(Alignment.Center))
                MediaBadge(Modifier.align(Alignment.BottomStart).padding(8.dp), label = type, icon = if (item.isSpace) MediaIcons.GraphicEq else MediaIcons.LiveTvOutlined)
            }
        }
    }
}

/** The round play sign over a grid's video (XTA's FritterCenterPlayButton at 40). */
@Composable
private fun PlaySign(modifier: Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.54f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/** "Photos", "Videos" (a GIF's too), "Broadcasts" or "Spaces". */
@Composable
private fun typeLabel(item: MediaGridItem): String = stringResource(
    when (item.kind) {
        MediaGridItem.Kind.PHOTO -> Res.string.posts_filter_photos
        MediaGridItem.Kind.GIF, MediaGridItem.Kind.VIDEO -> Res.string.posts_filter_videos
        MediaGridItem.Kind.BROADCAST -> if (item.isSpace) Res.string.posts_spaces else Res.string.posts_filter_broadcasts
    },
)

private fun manualIcon(item: MediaGridItem) = when (item.kind) {
    MediaGridItem.Kind.PHOTO -> MediaIcons.ImageOutlined
    MediaGridItem.Kind.GIF -> MediaIcons.GifBoxOutlined
    MediaGridItem.Kind.VIDEO -> MediaIcons.PlayCircleOutline
    MediaGridItem.Kind.BROADCAST -> MediaIcons.LiveTvOutlined
}
