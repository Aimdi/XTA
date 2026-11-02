package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import org.jetbrains.compose.resources.stringResource

/** A post's photos and videos full screen: swipe between them, pinch or double-tap a photo to zoom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(postId: String, start: Int, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val items = remember(postId) { graph.posts.post(postId)?.media.orEmpty() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            LaunchedEffect(Unit) { onClose() }
            return@Box
        }
        val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
            val item = items[page]
            when (item.kind) {
                XMedia.Kind.PHOTO -> ZoomablePhoto(item)
                XMedia.Kind.VIDEO, XMedia.Kind.GIF -> VideoPage(item, active = pager.currentPage == page)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, stringResource(Res.string.close), tint = Color.White)
        }
        if (items.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${items.size}",
                color = Color.White,
                modifier = Modifier.statusBarsPadding().padding(20.dp).align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun ZoomablePhoto(media: XMedia) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = photoUrl(media.url, PhotoSize.LARGE),
        contentDescription = media.alt,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 1.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Two fingers zoom; one finger pans only once zoomed, so the pager still swipes.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** A video or GIF in the viewer, playing while it is the page shown; each platform plays it its own way. */
@Composable
internal expect fun VideoPage(media: XMedia, active: Boolean)
