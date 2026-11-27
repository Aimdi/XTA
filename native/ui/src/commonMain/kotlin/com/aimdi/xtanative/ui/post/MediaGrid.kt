package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import com.aimdi.xtanative.ui.settings.isDesktop
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.common.AltBadge
import com.aimdi.xtanative.ui.common.AltTextDialog
import com.aimdi.xtanative.ui.common.ShowMediaTile
import com.aimdi.xtanative.ui.common.mediaShown
import com.aimdi.xtanative.ui.common.rememberPictureIndication
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.media.GifCorner
import com.aimdi.xtanative.ui.media.LocalMediaSaver
import com.aimdi.xtanative.ui.media.MediaFiles
import com.aimdi.xtanative.ui.media.MediaIcons
import com.aimdi.xtanative.ui.media.MediaSaver
import com.aimdi.xtanative.ui.media.TileGif
import com.aimdi.xtanative.ui.media.TileVideo
import com.aimdi.xtanative.ui.media.rememberPhotoSize
import org.jetbrains.compose.resources.stringResource

private val MediaShape = RoundedCornerShape(16.dp)

/**
 * A post's photos, videos and GIFs as XTA lays them out (_media.dart): one
 * at its own shape on black; several along a row at one height, each at
 * its own shape, the next one's edge in view, the row scrolling sideways
 * ([MediaStrip]). Photos come at the image-quality setting's size; a
 * described one carries "ALT"; a long press on a photo shows its
 * description, else saves it under [username]'s name from post [postId].
 * While media loads manually each waits behind "Tap to show Media". A video
 * plays in its tile with its controls, and a GIF loops there (`ui/media`'s
 * TileVideo and TileGif), the one seen most of a feed playing by itself
 * as Settings › Media says; [post] names a saved video's file. The row starts at
 * [start] and runs [bleed] on past the end of its column, to the screen's
 * edge, as XTA's does.
 */
@Composable
fun MediaGrid(
    media: List<XMedia>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    post: XPost? = null,
    username: String = post?.author?.screenName.orEmpty(),
    postId: String = post?.id.orEmpty(),
    start: Int = 0,
    bleed: Dp = 0.dp,
) = MediaGrid(media, onOpen, modifier, post, username, postId, start, bleed, tallCapped = false)

/**
 * [MediaGrid], with a picture alone no taller than 4:5 where [tallCapped]
 * ([MediaStrip.feedSingleAspect]): a post in a feed, not the one opened.
 */
@Composable
internal fun MediaGrid(
    media: List<XMedia>,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
    post: XPost?,
    username: String,
    postId: String,
    start: Int,
    bleed: Dp,
    tallCapped: Boolean,
) {
    if (media.isEmpty()) return
    val owner = MediaOwner(username, postId, post)
    if (media.size == 1) {
        val own = MediaStrip.singleAspect(MediaStrip.aspectOf(media[0]))
        val letterboxed = tallCapped && isDesktop && media[0].kind == XMedia.Kind.VIDEO
        val shown = if (tallCapped) MediaStrip.feedSingleAspect(own) else own
        val windowHeight = LocalWindowInfo.current.containerSize.height
        val sized = if (letterboxed) modifier.fillMaxWidth().videoHeight(own, windowHeight) else modifier.fillMaxWidth().aspectRatio(shown)
        val frame = sized.clip(MediaShape)
        val scale = if (letterboxed || shown == own) ContentScale.Fit else ContentScale.Crop
        Box(frame.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, MediaShape).background(Color.Black)) {
            ShownMedia(media[0], 0, 1, scale, Color.Black, owner) { onOpen(0) }
        }
    } else {
        MediaRow(media, owner, start, bleed, modifier, onOpen)
    }
}

/** As tall as [MediaStrip.desktopVideoHeight] makes a video of [aspect] across the width given, in a window [windowHeight] pixels tall. */
private fun Modifier.videoHeight(aspect: Float, windowHeight: Int): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val tall = MediaStrip.desktopVideoHeight(width.toFloat(), aspect, windowHeight.toFloat()).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minWidth = width, minHeight = tall, maxHeight = tall))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** Whose a post's media is, for a photo saved with a long press, and the [post] a saved video is named by. */
private class MediaOwner(val username: String, val postId: String, val post: XPost?)

/** Several along a row at one height, opened at [start], running [bleed] past the column's end. */
@Composable
private fun MediaRow(media: List<XMedia>, owner: MediaOwner, start: Int, bleed: Dp, modifier: Modifier, onOpen: (Int) -> Unit) {
    val row = rememberLazyListState(initialFirstVisibleItemIndex = MediaStrip.startOf(start, media.size))
    val backdrop = MaterialTheme.colorScheme.surfaceVariant
    // The row's height and its cards' widths follow from its width where it is measured: nothing is composed for them.
    val strip = remember(media) { StripMeasure(media.map(MediaStrip::aspectOf)) }
    Box(modifier.fillMaxWidth().bleedEnd(bleed)) {
        LazyRow(
            state = row,
            modifier = Modifier.fillMaxWidth().then(remember(strip) { Modifier.stripHeight(strip) }),
            contentPadding = PaddingValues(end = bleed),
            horizontalArrangement = Arrangement.spacedBy(MediaStrip.GAP.dp),
        ) {
            itemsIndexed(media, key = { index, item -> "$index:" + item.url }) { index, item ->
                val frame = remember(strip, index) { Modifier.stripCard(strip, index) }.clip(MediaShape)
                Box(frame.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, MediaShape)) {
                    ShownMedia(item, index, media.size, ContentScale.Crop, backdrop, owner) { onOpen(index) }
                }
            }
        }
    }
}

/**
 * A row's [MediaStrip.Layout] at the width it was last measured at
 * ([stripHeight]), which its cards read as the row measures them
 * ([stripCard]). Cards are measured again by themselves when the row's
 * height changes; [stale] has them measured again where the width changed
 * and the height, in whole pixels, did not.
 */
@Stable
internal class StripMeasure(private val aspects: List<Float>) {
    private var widthPx = -1
    private var density = 0f
    private var heightPx = -1

    private var widthChanged = false
    private var staleCount = 0

    var layout = MediaStrip.layout(0f, aspects)
        private set

    private var stale by mutableIntStateOf(0)

    /** The layout in a row [widthPx] wide ([Constraints.Infinity] for no bound) at [density]. */
    fun at(widthPx: Int, density: Float): MediaStrip.Layout {
        if (widthPx == this.widthPx && density == this.density) return layout
        this.widthPx = widthPx
        this.density = density
        widthChanged = true
        layout = MediaStrip.layout(if (widthPx == Constraints.Infinity) Float.POSITIVE_INFINITY else widthPx / density, aspects)
        return layout
    }

    /** The row is [px] tall at the width last given. */
    fun heightIs(px: Int) {
        if (widthChanged && px == heightPx) stale = ++staleCount
        widthChanged = false
        heightPx = px
    }

    /** Has the card being measured measured again once [stale] moves on. */
    fun followStale() = stale
}

/** [px] as `Modifier.width` and `height` make a size of it within [min] and [max]: both bounds at it, as near as they allow. */
internal fun fixedSize(px: Int, min: Int, max: Int): IntRange {
    val most = px.coerceAtLeast(0)
    val least = if (most == Constraints.Infinity) 0 else most
    return least.coerceIn(min, max)..most.coerceIn(min, max)
}

/** The row as tall as [strip] makes it at the width it is given, as `height(layout.height.dp)` sized it. */
private fun Modifier.stripHeight(strip: StripMeasure): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
    val tall = fixedSize(strip.at(width, density).height.dp.roundToPx(), constraints.minHeight, constraints.maxHeight)
    strip.heightIs(tall.last)
    val placeable = measurable.measure(constraints.copy(minHeight = tall.first, maxHeight = tall.last))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** Card [index] of [strip]'s row at its width there and the row's height, as `width(layout.widths[index].dp).fillMaxHeight()` sized it. */
private fun Modifier.stripCard(strip: StripMeasure, index: Int): Modifier = layout { measurable, constraints ->
    strip.followStale()
    val wide = fixedSize(strip.layout.widths[index].dp.roundToPx(), constraints.minWidth, constraints.maxWidth)
    val least = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
    val placeable = measurable.measure(constraints.copy(minWidth = wide.first, maxWidth = wide.last, minHeight = least))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * One of a post's media where it is shown (_media.dart's _TweetMediaItem):
 * "Tap to show Media" while it waits, else the tile with its ALT badge,
 * read as "Media 2/4" when there are several.
 */
@Composable
private fun ShownMedia(media: XMedia, index: Int, count: Int, scale: ContentScale, backdrop: Color, owner: MediaOwner, onOpen: () -> Unit) {
    var revealed by rememberSaveable(media.url) { mutableStateOf(false) }
    var describing by remember { mutableStateOf(false) }
    val url = photoUrl(media.url, rememberPhotoSize())
    val word = stringResource(Res.string.reader_media)
    // Shown at once unless media waits for a tap: only then is there anything to look up.
    val shown = !loadsManually() || mediaShown(url, revealed) { revealed = true }
    val saver = LocalMediaSaver.current
    val label = if (count > 1) "$word ${index + 1}/$count" else word
    // One stop for a screen reader: the tile, read as its place and its description; ALT stays a button of its own.
    Box(Modifier.fillMaxSize()) {
        if (!shown) {
            ShowMediaTile(label, MediaIcons.ImageOutlined, onShow = { revealed = true }, modifier = Modifier.fillMaxSize())
            return@Box
        }
        // A long press on a photo shows what it is of, or saves it when it says nothing.
        val onLongPress: (() -> Unit)? = if (media.kind != XMedia.Kind.PHOTO) null else ({ describeOrSave(media, owner, saver) { describing = true } })
        MediaTile(media, url, scale, owner.post, Modifier.fillMaxSize().background(backdrop).semantics { contentDescription = listOfNotNull(label, media.alt).joinToString(". ") }, onLongPress, onOpen)
        // A video's and a GIF's own label holds the bottom-start corner, and its mute switch the other.
        media.alt?.let { AltBadge(it, Modifier.align(if (media.kind == XMedia.Kind.PHOTO) Alignment.BottomStart else Alignment.TopStart)) }
    }
    if (describing) media.alt?.let { AltTextDialog(it) { describing = false } }
}

/** A described photo's description ([describe]), else the photo saved through [saver] under its owner's name (_media.dart's long press). */
private fun describeOrSave(media: XMedia, owner: MediaOwner, saver: MediaSaver?, describe: () -> Unit) {
    if (media.alt != null) {
        describe()
    } else {
        saver?.let { MediaFiles.save(media, owner.postId, owner.username)?.let(it::save) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(media: XMedia, url: String, scale: ContentScale, post: XPost?, modifier: Modifier, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    // No grey wash over the picture under the pointer; a press and the keyboard's focus still show.
    Box(modifier.combinedClickable(interactionSource = null, indication = rememberPictureIndication(), onClick = onClick, onLongClick = onLongClick)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = scale,
            modifier = Modifier.fillMaxSize(),
        )
        when (media.kind) {
            XMedia.Kind.PHOTO -> Unit
            // Its corners (the time, the mute switch) are the tile's own; a tap opens it full screen.
            XMedia.Kind.VIDEO -> TileVideo(media, url, post, Modifier.matchParentSize(), fill = scale != ContentScale.Fit)
            XMedia.Kind.GIF -> {
                TileGif(media, url, Modifier.matchParentSize())
                GifCorner(Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** Lets what this holds run [bleed] on past the end of the room it is given, as a row running off the screen's edge does. */
private fun Modifier.bleedEnd(bleed: Dp): Modifier = if (bleed <= 0.dp) this else layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val wider = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.placeRelative(0, 0) }
}

/** The round play sign over a video's poster. */
@Composable
internal fun PlayBadge(modifier: Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

