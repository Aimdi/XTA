package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.aimdi.xtanative.networks.ehviewer.EhImagePage
import com.aimdi.xtanative.networks.ehviewer.EhPageSlot
import com.aimdi.xtanative.ui.plugins.ZoomableImage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberImageRequest

/** Portrait paper: the height a page in the column holds until its picture arrives. */
private const val PAPER = 1f / 1.4142f

private val Muted = Color.White.copy(alpha = 0.7f)

/** One page of the reader (eh_reader_page.dart): its number while it is found, its failure with ways out, then its picture. */
@Composable
internal fun ReaderPage(app: EhApp, open: EhOpenReader, page: Int, vertical: Boolean, onTap: (Offset) -> Unit, onHold: (Int) -> Unit) {
    LaunchedEffect(page) { open.resolver.resolve(page) }
    val slots by open.resolver.slots.collectAsState()
    val total = open.model.total
    when (val slot = slots[page]) {
        is EhPageSlot.Ready -> PagePicture(app, open, slot.image, vertical, onTap, onHold)
        is EhPageSlot.Failed -> PageStatus(
            page,
            total,
            vertical,
            onTap,
            { onHold(page) },
            failure = PageFailure(pluginString(EhText.error(slot.error)), onRetry = { open.resolver.retry(page) }),
        )
        else -> PageStatus(page, total, vertical, onTap, { onHold(page) })
    }
}

/**
 * The page's picture: the original for a signed-in reader, the resample
 * when that fails, then the failure, with Retry and another image server.
 * Paged, it zooms; in the column, it is as wide as the screen.
 */
@Composable
private fun PagePicture(app: EhApp, open: EhOpenReader, image: EhImagePage, vertical: Boolean, onTap: (Offset) -> Unit, onHold: (Int) -> Unit) {
    val page = image.page
    val urls = remember(image) { image.imageUrls(app.client.signedIn) }
    var tried by remember(image) { mutableIntStateOf(0) }
    var attempt by remember(image) { mutableIntStateOf(0) }
    val url = urls.getOrNull(tried)
    val hold = { onHold(page) }
    if (url == null) {
        val failure = PageFailure(
            pluginString(Strings.reader_image_failed),
            onRetry = {
                tried = 0
                attempt++
            },
            onOtherServer = { open.resolver.reload(page) },
        )
        PageStatus(page, open.model.total, vertical, onTap, hold, failure)
        return
    }
    key(url, attempt) {
        var shown by remember { mutableStateOf(false) }
        val request = rememberImageRequest(url, app.client.imageHeaders(url))
        val onState: (AsyncImagePainter.State) -> Unit = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> shown = true
                is AsyncImagePainter.State.Error -> tried++
                else -> Unit
            }
        }
        Box(if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
            if (vertical) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    onState = onState,
                    modifier = Modifier.fillMaxWidth().pointerInput(onTap) { detectTapGestures(onTap = onTap, onLongPress = { hold() }) },
                )
            } else {
                ZoomableImage(request, contentDescription = null, modifier = Modifier.fillMaxSize(), onTap = onTap, onLongPress = { hold() }, onState = onState)
            }
            if (!shown) PageStatus(page, open.model.total, vertical, onTap, hold)
        }
    }
}

/** Why a page did not show, and what can be tried. */
internal class PageFailure(val message: String, val onRetry: () -> Unit, val onOtherServer: (() -> Unit)? = null)

/** A page not on screen yet: a spinner and its number, or [failure] with ways to try again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageStatus(page: Int, total: Int, vertical: Boolean, onTap: (Offset) -> Unit, onHold: () -> Unit, failure: PageFailure? = null) {
    val frame = if (vertical) Modifier.fillMaxWidth().aspectRatio(PAPER) else Modifier.fillMaxSize()
    Box(frame.pointerInput(onTap) { detectTapGestures(onTap = onTap, onLongPress = { onHold() }) }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failure == null) {
                CircularProgressIndicator(color = Muted, strokeWidth = 2.dp)
            } else {
                Icon(EhIcons.BrokenImage, contentDescription = null, tint = Muted, modifier = Modifier.size(32.dp))
            }
            Text(pluginString(Strings.page_of, page, total), color = Muted)
            if (failure != null) {
                Text(failure.message, color = Color.White, textAlign = TextAlign.Center)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    OutlinedButton(
                        onClick = failure.onRetry,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(pluginString(Strings.retry), Modifier.padding(start = 8.dp))
                    }
                    failure.onOtherServer?.let { other ->
                        TextButton(onClick = other, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Text(pluginString(Strings.reader_try_another_server))
                        }
                    }
                }
            }
        }
    }
}
