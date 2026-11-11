package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

/**
 * The column reader magnified as one, as XTA's PixivZoomable wraps its list:
 * two fingers zoom and move it, one finger still scrolls the pages and, once
 * magnified, moves it sideways too; a double tap zooms in and back out.
 */
@Stable
internal class ColumnZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val zoomed: Boolean get() = scale > 1.01f

    fun transform(zoom: Float, pan: Offset, size: IntSize) {
        scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        offset = clamped(offset + pan, scale, size)
    }

    fun toggle(size: IntSize) {
        if (zoomed) {
            scale = 1f
            offset = Offset.Zero
        } else {
            scale = DOUBLE_TAP_SCALE
            offset = clamped(offset, scale, size)
        }
    }

    companion object {
        const val MAX_SCALE = 4f
        const val DOUBLE_TAP_SCALE = 2.5f

        /** [offset] kept where the magnified column still covers the screen at [scale]. */
        fun clamped(offset: Offset, scale: Float, size: IntSize): Offset {
            val x = (scale - 1f) * size.width / 2f
            val y = (scale - 1f) * size.height / 2f
            return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
        }
    }
}

/** [zoom]'s gestures over a scrolling column, seen before the column scrolls, and its magnification drawn. */
internal fun Modifier.zoomsColumn(zoom: ColumnZoom): Modifier = clipToBounds()
    .pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) {
                    zoom.transform(event.calculateZoom(), event.calculatePan(), size)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (zoom.zoomed) {
                    zoom.transform(1f, Offset(event.calculatePan().x, 0f), size)
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        scaleX = zoom.scale
        scaleY = zoom.scale
        translationX = zoom.offset.x
        translationY = zoom.offset.y
    }
