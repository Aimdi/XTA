package com.aimdi.xtanative.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The page under each opened post as it looked when the post was opened
 * over it: XTA draws the live page behind its card, and a screen here is no
 * longer drawn once another covers it, so the card is dragged off a picture
 * of it. The screens are drawn through [Recorded]; [open] takes the picture,
 * then opens the post, whose screen [claim]s it. The newest two are kept.
 *
 * The screens are drawn straight, as any other: only the one frame a
 * picture is taken of is recorded ([asked]), and the recording is let go
 * again on the frame after, so the pictures the page holds (avatars,
 * media) are not kept alive by it in between.
 */
class CardBacks {
    private var layer: GraphicsLayer? = null
    private var pending: ImageBitmap? = null
    private var opening = false
    private val kept = LinkedHashMap<String, ImageBitmap>()
    private val watched = HashSet<String>()

    /** Bumped to have the next frame recorded: the screens' drawing reads it, so a change draws them again. */
    private val asked = mutableIntStateOf(0)

    /** The frame [picture] waits for, while it waits. */
    private var recorded: CompletableDeferred<Unit>? = null

    /** Whether the layer still holds a recording that a frame should let go of. */
    private var stale = false

    /** How the screens are pictured; a test's stand-in in tests. */
    internal var pictured: suspend () -> ImageBitmap? = { picture() }

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
                asked.intValue
                val waiting = recorded
                when {
                    waiting != null -> {
                        recording.record { this@drawWithContent.drawContent() }
                        drawLayer(recording)
                        recorded = null
                        waiting.complete(Unit)
                    }
                    else -> {
                        if (stale) {
                            recording.record { }
                            stale = false
                        }
                        drawContent()
                    }
                }
            },
        ) { content() }
    }

    /**
     * Pictures the screens, then runs [navigate]: a picture that cannot be
     * taken leaves the card on its dark stage. [abandoned] runs instead when
     * nothing is navigated: another card still opening, or the picture's
     * work cancelled. A [navigate] that opens nothing (false: the second tap
     * of a double-tap) drops the picture, so a card opened later without one
     * does not take it.
     */
    fun open(scope: CoroutineScope, abandoned: () -> Unit = {}, navigate: () -> Boolean) {
        if (opening) return abandoned()
        opening = true
        val job = scope.launch {
            try {
                pending = pictured()
            } finally {
                opening = false
            }
            if (!navigate()) pending = null
        }
        job.invokeOnCompletion { cause -> if (cause != null) abandoned() }
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
        take(id)
    }

    /** The picture kept for screen [id], or the one just taken, which is then [id]'s. */
    internal fun take(id: String): ImageBitmap? = kept[id] ?: pending?.also {
        kept[id] = it
        pending = null
        trim()
    }

    /** The next frame of the screens, recorded and turned into a picture; null when no frame comes in [FRAME_WAIT_MS]. */
    private suspend fun picture(): ImageBitmap? {
        val target = layer ?: return null
        val frame = CompletableDeferred<Unit>()
        recorded = frame
        asked.intValue++
        try {
            withTimeoutOrNull(FRAME_WAIT_MS) { frame.await() } ?: return null
            if (target.size.width <= 0 || target.size.height <= 0) return null
            return target.toImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } finally {
            if (recorded === frame) recorded = null
            stale = true
        }
    }

    private fun trim() {
        while (kept.size > KEPT) kept.remove(kept.keys.first())
    }

    internal companion object {
        /**
         * A picture is a window's worth of pixels (8 MB at 1920×1080, 33 MB at
         * 4K): the post opened and the one opened from it keep theirs, cards
         * deeper down are dragged off the dark stage alone.
         */
        const val KEPT = 2

        /** How long a picture waits for the frame it is taken of before the post opens without one. */
        const val FRAME_WAIT_MS = 300L
    }
}
