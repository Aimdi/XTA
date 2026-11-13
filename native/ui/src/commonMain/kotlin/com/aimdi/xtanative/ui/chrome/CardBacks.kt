package com.aimdi.xtanative.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The page under each opened post as it looked when the post was opened
 * over it: XTA draws the live page behind its card, and a screen here is no
 * longer drawn once another covers it, so the card is dragged off a picture
 * of it. The screens are drawn through [Recorded]; [open] takes the picture,
 * then opens the post, whose screen [claim]s it. The newest few are kept.
 */
class CardBacks {
    private var layer: GraphicsLayer? = null
    private var pending: ImageBitmap? = null
    private var opening = false
    private val kept = LinkedHashMap<String, ImageBitmap>()
    private val watched = HashSet<String>()

    /** [content], the app's screens, drawn so that they can be pictured. */
    @Composable
    fun Recorded(content: @Composable () -> Unit) {
        val recording = rememberGraphicsLayer()
        DisposableEffect(recording) {
            layer = recording
            onDispose { if (layer === recording) layer = null }
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                recording.record { this@drawWithContent.drawContent() }
                drawLayer(recording)
            },
        ) { content() }
    }

    /** Pictures the screens, then runs [navigate]: a picture that cannot be taken leaves the card on its dark stage. */
    fun open(scope: CoroutineScope, navigate: () -> Unit) {
        if (opening) return
        opening = true
        scope.launch {
            try {
                pending = picture()
            } finally {
                opening = false
            }
            navigate()
        }
    }

    /** The picture under the screen of [entry], forgotten once that screen is gone for good. */
    @Composable
    fun claim(entry: NavBackStackEntry): ImageBitmap? = remember(entry.id) {
        val id = entry.id
        if (watched.add(id)) {
            entry.lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        kept.remove(id)
                        watched.remove(id)
                    }
                },
            )
        }
        kept[id] ?: pending?.also {
            kept[id] = it
            pending = null
            trim()
        }
    }

    private suspend fun picture(): ImageBitmap? {
        val recorded = layer ?: return null
        if (recorded.size.width <= 0 || recorded.size.height <= 0) return null
        return try {
            recorded.toImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun trim() {
        while (kept.size > KEPT) kept.remove(kept.keys.first())
    }

    private companion object {
        /** A picture is a screen's worth of pixels: the cards deeper down are dragged off the dark stage alone. */
        const val KEPT = 4
    }
}
