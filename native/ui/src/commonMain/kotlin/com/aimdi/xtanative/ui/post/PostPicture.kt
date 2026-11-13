package com.aimdi.xtanative.ui.post

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A post as it is drawn, for its share sheet's "Share … as image", as XTA
 * captures its tile (tweet.dart's captureWidget): asked for one, the post
 * draws itself into a layer as well ([recordedIn]) for a frame, which
 * becomes a PNG on the screen's background.
 */
@Stable
class PostPicture internal constructor(internal val layer: GraphicsLayer, private val background: Color) {
    internal var recording by mutableStateOf(false)
        private set

    /** The post as a PNG; null when it is not drawn, or too large to take. */
    internal suspend fun png(): ByteArray? {
        recording = true
        return try {
            // One frame draws the post into the layer; by the next it is there.
            withFrameNanos { }
            withFrameNanos { }
            if (layer.size.width <= 0 || layer.size.height <= 0) return null
            val image = layer.toImageBitmap()
            withContext(Dispatchers.Default) { pngOf(image, background) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } finally {
            recording = false
        }
    }
}

/** A post's picture, for the post drawn where [recordedIn] records it. */
@Composable
internal fun rememberPostPicture(): PostPicture {
    val layer = rememberGraphicsLayer()
    val background = MaterialTheme.colorScheme.background
    return remember(layer, background) { PostPicture(layer, background) }
}

/** What it modifies, drawn into [picture]'s layer as well while a picture is asked for. */
internal fun Modifier.recordedIn(picture: PostPicture): Modifier =
    if (!picture.recording) {
        this
    } else {
        drawWithContent {
            picture.layer.record { this@drawWithContent.drawContent() }
            drawLayer(picture.layer)
        }
    }

/** [image] over [background], as a PNG file's bytes; null when the platform could not encode it. */
internal expect fun pngOf(image: ImageBitmap, background: Color): ByteArray?
