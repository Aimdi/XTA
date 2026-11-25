package com.aimdi.xtanative.ui.media

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_media
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** How a gesture on a photo is being read once it has moved: undecided, a pinch or pan, a drag to close, or a page turn. */
private enum class PhotoGesture { UNDECIDED, TRANSFORM, SLIDE, PAGE }

/**
 * A photo in the viewer (_photo.dart, double_tap_zoom.dart): at full size,
 * pinched between one and four times, panned once zoomed in, and a double
 * tap toggles between the two around the point tapped; the mouse wheel zooms
 * about the pointer. At its fitted size a
 * drag up or down slides it away and, far or fast enough, closes the viewer
 * ([onBackdrop] hears how dark the backdrop stays meanwhile); a sideways drag
 * turns the page. [motion] off makes every change at once. A finger's single
 * tap goes to [onTap] (the viewer hides or shows its bar); a mouse's click
 * does not, so the desktop's bar stays where its pointer looks for it.
 */
@Composable
internal fun ZoomablePhoto(
    url: String,
    alt: String?,
    motion: Boolean,
    onBackdrop: (Float) -> Unit,
    onClose: () -> Unit,
    zoomKeys: Flow<ViewerKey>? = null,
    onZoomed: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
    sharpUrl: String? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var shift by remember { mutableStateOf(Shift.ZERO) }
    var slide by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val animation = remember { arrayOfNulls<Job>(1) }
    val close by rememberUpdatedState(onClose)
    val backdrop by rememberUpdatedState(onBackdrop)
    val page = remember { floatArrayOf(0f) }
    val tap by rememberUpdatedState(onTap)
    // Whether the press last put down was a finger's, not a mouse's.
    val pressedByTouch = remember { booleanArrayOf(false) }
    fun report(drag: Float) = backdrop(SlideToClose.backdrop(drag, page[0]))

    fun animateTo(toScale: Float, toShift: Shift, toSlide: Float = 0f) {
        animation[0]?.cancel()
        val fromScale = scale
        val fromShift = shift
        val fromSlide = slide
        animation[0] = scope.launch {
            animate(0f, 1f, animationSpec = tween(if (motion) MOTION_FAST_MS else 0)) { t, _ ->
                scale = fromScale + (toScale - fromScale) * t
                shift = fromShift + (toShift - fromShift) * t
                slide = fromSlide + (toSlide - fromSlide) * t
                report(slide)
            }
        }
    }

    // The keys' zoom goes about the middle, eased as a double tap's; the arrows move a zoomed photo about.
    LaunchedEffect(zoomKeys) {
        zoomKeys?.collect { key ->
            val width = area.width.toFloat()
            val height = area.height.toFloat()
            val to = ViewerZoom.keyed(scale, key)
            when {
                to != scale -> animateTo(to, if (ViewerZoom.zoomed(to)) ViewerZoom.shiftFor(Shift.ZERO, scale, shift, to, width, height) else Shift.ZERO)
                ViewerZoom.zoomed(scale) -> animateTo(scale, ViewerZoom.panned(shift, scale, key, width, height))
            }
        }
    }
    // The viewer reads the arrows as moving about the photo while it is zoomed in.
    val zoomedNow by rememberUpdatedState(onZoomed)
    // Once zoomed into, the photo is read again larger ([sharpUrl]) and laid over the fitted one when it has come.
    var sharpWanted by remember(sharpUrl) { mutableStateOf(false) }
    var sharpLoaded by remember(sharpUrl) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { ViewerZoom.zoomed(scale) }.collect {
            zoomedNow(it)
            if (it) sharpWanted = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { area = it }
            // The mouse wheel zooms about the pointer, as a desktop's photo viewers do.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val to = ViewerZoom.wheeled(scale, change.scrollDelta.y)
                        if (to != scale) {
                            animation[0]?.cancel()
                            val focus = Shift(change.position.x - size.width / 2f, change.position.y - size.height / 2f)
                            shift = ViewerZoom.shiftFor(focus, scale, shift, to, size.width.toFloat(), size.height.toFloat())
                            scale = to
                        }
                        change.consume()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (pressedByTouch[0]) tap() },
                    onDoubleTap = { at ->
                        val focus = Shift(at.x - size.width / 2f, at.y - size.height / 2f)
                        val to = ViewerZoom.doubleTapScale(if (ViewerZoom.zoomed(scale)) scale else ViewerZoom.MIN)
                        animateTo(to, ViewerZoom.shiftFor(focus, scale, shift, to, size.width.toFloat(), size.height.toFloat()))
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    pressedByTouch[0] = awaitFirstDown(requireUnconsumed = false).type != PointerType.Mouse
                    var gesture = PhotoGesture.UNDECIDED
                    var moved = Offset.Zero
                    val velocity = VelocityTracker()
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    page[0] = height
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (gesture == PhotoGesture.UNDECIDED && (fingers > 1 || ViewerZoom.zoomed(scale))) gesture = PhotoGesture.TRANSFORM
                        when (gesture) {
                            PhotoGesture.TRANSFORM -> {
                                animation[0]?.cancel()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val next = ViewerZoom.pinched(scale, zoom)
                                if (centroid != Offset.Unspecified) {
                                    val focus = Shift(centroid.x - width / 2f, centroid.y - height / 2f)
                                    val target = focus - (focus - shift) * (next / scale) + Shift(pan.x, pan.y)
                                    shift = ViewerZoom.bounds(target, next, width, height)
                                }
                                scale = next
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            PhotoGesture.UNDECIDED, PhotoGesture.SLIDE -> {
                                val change = event.changes.first()
                                moved += change.positionChange()
                                if (gesture == PhotoGesture.UNDECIDED) {
                                    gesture = when {
                                        SlideToClose.startsSlide(moved.x, moved.y, viewConfiguration.touchSlop) -> PhotoGesture.SLIDE
                                        SlideToClose.startsPage(moved.x, moved.y, viewConfiguration.touchSlop) -> PhotoGesture.PAGE
                                        else -> gesture
                                    }
                                }
                                if (gesture == PhotoGesture.SLIDE) {
                                    velocity.addPosition(change.uptimeMillis, change.position)
                                    slide += change.positionChange().y
                                    report(slide)
                                    change.consume()
                                }
                            }
                            PhotoGesture.PAGE -> Unit
                        }
                    } while (event.changes.any { it.pressed })
                    when (gesture) {
                        PhotoGesture.TRANSFORM -> if (scale < ViewerZoom.MIN) animateTo(ViewerZoom.MIN, Shift.ZERO)
                        PhotoGesture.SLIDE -> if (SlideToClose.closes(slide, height, velocity.calculateVelocity().y)) close() else animateTo(scale, shift, 0f)
                        else -> Unit
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
                failed = state is AsyncImagePainter.State.Error
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = shift.x
                    translationY = shift.y + slide
                },
        )
        if (sharpWanted && sharpUrl != null && !failed) {
            val context = LocalPlatformContext.current
            AsyncImage(
                model = remember(sharpUrl, context) { ImageRequest.Builder(context).data(sharpUrl).size(SharpPhoto.PIXELS).build() },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { state -> sharpLoaded = state is AsyncImagePainter.State.Success },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (sharpLoaded) 1f else 0f
                        scaleX = scale
                        scaleY = scale
                        translationX = shift.x
                        translationY = shift.y + slide
                    },
            )
        }
        when {
            failed -> Icon(ReaderIcons.BrokenImage, stringResource(Res.string.reader_media), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
            loading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}

/**
 * The larger reading of a photo the viewer lays over the fitted one once it
 * is zoomed into: a fitted photo is decoded at the screen's size, which
 * four times magnified shows its pixels.
 */
internal object SharpPhoto {
    /** The longest side the larger reading is decoded at: sharp at any zoom the viewer allows, and a bitmap a phone holds easily. */
    const val PIXELS = 2560

    /** The address read once zoomed in, [large] being the photo at its large size; none while data is saved on a metered network. */
    fun url(large: String, savesData: Boolean): String? = large.takeUnless { savesData }
}

/** XTA's quick motion, in milliseconds (motion.dart's kXtaMotionFast). */
internal const val MOTION_FAST_MS = 120

/** XTA's standard motion (kXtaMotionStandard). */
internal const val MOTION_STANDARD_MS = 180
