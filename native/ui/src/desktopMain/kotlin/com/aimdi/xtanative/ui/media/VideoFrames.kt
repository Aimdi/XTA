package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/** A frame drawn for source [load] of its player, to be turned [rotation] degrees on screen. */
internal class VideoFrame(val image: Image, val load: Long, val rotation: Int)

/**
 * The thread that draws a video player's frames with mpv's software
 * renderer: it waits until mpv says one is due, draws it into a bitmap at
 * the size the surface shows it ([VideoFrameSizes]) and hands a copy over as
 * a Skia image, which the surface's canvas draws as it is. A frame drawn
 * before its source is ready ([MpvState.ready]) is drawn, since mpv waits
 * for it, but not handed over. Closing ends the thread, which frees the
 * render context and then the player. [onFirst] hears each source's first
 * frame handed over.
 */
internal class MpvFrames(private val player: MpvPlayer, private val onFirst: (load: Long) -> Unit = {}) {
    private val mpv = checkNotNull(player.mpv)
    private val render = mpv.render
    private val handed = AtomicReference<VideoFrame?>(null)
    private val redraw = AtomicBoolean(false)
    private var bitmap: Bitmap? = null
    private var pixels = 0L
    private var firstOf = 0L

    /** Grows with each frame handed over: the surface draws again on it. */
    val version = mutableLongStateOf(0L)

    /** Frames handed over, and taken onto the screen: what the video check counts. */
    @Volatile
    var handedCount = 0L
        private set

    @Volatile
    var shownCount = 0L
        private set

    @Volatile
    private var target = IntSize.Zero

    @Volatile
    private var crop = false

    @Volatile
    private var closing = false

    /** Guards the render context from a wake after it is freed. */
    private val alive = Any()
    private var freed = false

    init {
        player.onReady = ::redrawSoon
        thread(name = "mpv-render", isDaemon = true) { run() }
    }

    /** The surface is [size] pixels, cropping the video to cover it or fitting it in. */
    fun show(size: IntSize, crop: Boolean) {
        if (size == target && crop == this.crop) return
        target = size
        this.crop = crop
        redrawSoon()
    }

    /** Draws the frame due again, at once: a source that just became ready shows its first frame even while paused. */
    fun redrawSoon() {
        redraw.set(true)
        wake()
    }

    /** The newest frame not taken yet, for the screen; whoever takes it closes it. */
    fun take(): VideoFrame? = handed.getAndSet(null)?.also { shownCount += 1 }

    fun close() {
        closing = true
        wake()
    }

    private fun wake() = synchronized(alive) {
        if (!freed) MpvJni.nativeRenderWake(render)
    }

    private fun run() {
        try {
            while (!closing) {
                MpvJni.nativeRenderWait(render, WAIT_MS)
                if (closing) break
                val due = MpvJni.nativeRenderUpdate(render) and UPDATE_FRAME != 0L
                if (redraw.getAndSet(false) || due) draw()
            }
        } finally {
            handed.getAndSet(null)?.image?.close()
            bitmap?.close()
            synchronized(alive) {
                freed = true
                mpv.freeRender()
            }
            mpv.close()
        }
    }

    private fun draw() {
        val state = player.state.value
        val size = VideoFrameSizes.of(state.width, state.height, state.rotation, target.width, target.height, crop) ?: IDLE
        val drawn = bitmapOf(size) ?: return
        if (MpvJni.nativeRender(render, size.width, size.height, size.stride, pixels) < 0) return
        if (!state.drawable || state.load == 0L) return
        drawn.notifyPixelsChanged()
        handed.getAndSet(VideoFrame(Image.makeFromBitmap(drawn), state.load, state.rotation))?.image?.close()
        handedCount += 1
        version.longValue += 1
        if (firstOf != state.load) {
            firstOf = state.load
            onFirst(state.load)
        }
    }

    /** The bitmap frames of [size] are drawn into, made again when the size changes. */
    private fun bitmapOf(size: FrameSize): Bitmap? {
        val current = bitmap
        if (current != null && current.width == size.width && current.height == size.height) return current
        current?.close()
        val made = Bitmap()
        if (!made.allocPixels(ImageInfo(size.width, size.height, ColorType.RGB_888X, ColorAlphaType.OPAQUE), size.stride)) {
            made.close()
            bitmap = null
            return null
        }
        pixels = made.peekPixels()?.use { it.addr } ?: 0L
        bitmap = made
        return made.takeIf { pixels != 0L }
    }

    private companion object {
        const val WAIT_MS = 250
        const val UPDATE_FRAME = 1L
        val IDLE = FrameSize(VideoFrameSizes.IDLE, VideoFrameSizes.IDLE)
    }
}

/**
 * A player's frames on screen, fitted in the surface or, [crop]ped, covering
 * it as a tile's poster does. Only a frame of a source [shows] accepts is
 * drawn (an inline tile: its own); until one comes, nothing is, so the
 * poster under the surface shows.
 */
@Composable
internal fun VideoSurface(frames: MpvFrames, crop: Boolean, modifier: Modifier, shows: (load: Long) -> Boolean = { true }) {
    val shown = remember(frames) { ShownFrame() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    SideEffect { frames.show(size, crop) }
    DisposableEffect(frames) { onDispose { shown.close() } }
    Canvas(modifier.onSizeChanged { size = it }) {
        frames.version.longValue
        shown.next(frames.take())?.takeIf { shows(it.load) }?.let { drawFrame(it, crop) }
    }
}

/** The frame on screen, kept until the next replaces it; touched by the drawing thread only. */
private class ShownFrame {
    private var frame: VideoFrame? = null

    fun next(taken: VideoFrame?): VideoFrame? {
        if (taken != null) {
            frame?.image?.close()
            frame = taken
        }
        return frame
    }

    fun close() {
        frame?.image?.close()
        frame = null
    }
}

/** [frame] centred on the surface, turned upright about its centre, at the scale that fits or covers the surface. */
private fun DrawScope.drawFrame(frame: VideoFrame, crop: Boolean) {
    val image = frame.image
    val scale = VideoFrameSizes.shownScale(image.width, image.height, frame.rotation, size.width, size.height, crop)
    val width = image.width * scale
    val height = image.height * scale
    clipRect {
        rotate(frame.rotation.toFloat()) {
            drawIntoCanvas { canvas ->
                val placed = Rect.makeXYWH(center.x - width / 2f, center.y - height / 2f, width, height)
                canvas.nativeCanvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), placed, SamplingMode.LINEAR, null, true)
            }
        }
    }
}
