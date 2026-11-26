package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A post's card that does not open says so, so what asked for it (going forward) keeps its step. */
class CardBacksTest {
    /** Runs what is sent to it only when told to. */
    private class Held : CoroutineDispatcher() {
        val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued.addLast(block)
        }
        fun runAll() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }

    /** A picture that needs no drawing library. */
    private class Picture : ImageBitmap {
        override val width = 1
        override val height = 1
        override val config = ImageBitmapConfig.Argb8888
        override val hasAlpha = false
        override val colorSpace = ColorSpaces.Srgb
        override fun prepareToDraw() = Unit
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
    }

    @Test
    fun aCardOpenedWhileAnotherIsOpeningIsAbandoned() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val backs = CardBacks()
        val done = mutableListOf<String>()
        backs.open(scope, abandoned = { done += "first abandoned" }) { done += "first"; true }
        backs.open(scope, abandoned = { done += "second abandoned" }) { done += "second"; true }
        assertEquals(listOf("second abandoned"), done)
        held.runAll()
        assertEquals(listOf("second abandoned", "first"), done)
    }

    @Test
    fun aCardWhoseWorkIsCancelledIsAbandoned() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val done = mutableListOf<String>()
        CardBacks().open(scope, abandoned = { done += "abandoned" }) { done += "navigated"; true }
        scope.cancel()
        held.runAll()
        assertEquals(listOf("abandoned"), done)
    }

    @Test
    fun aPostThatDoesNotOpenDropsItsPictureSoALaterCardDoesNotTakeIt() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val backs = CardBacks()
        backs.pictured = { Picture() }
        // The second tap of a double-tap: its picture is taken, its post let go.
        backs.open(scope) { false }
        held.runAll()
        assertNull(backs.take("viewer's post"), "a post opened later without a picture")
    }

    @Test
    fun aPostThatOpensTakesItsPicture() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val backs = CardBacks()
        val picture = Picture()
        backs.pictured = { picture }
        backs.open(scope) { true }
        held.runAll()
        assertSame(picture, backs.take("post"))
        assertSame(picture, backs.take("post"), "kept for its screen")
        assertNull(backs.take("another"))
    }

    @Test
    fun onlyTheNewestTwoPicturesAreKept() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val backs = CardBacks()
        val pictures = (1..3).map { Picture() }
        pictures.forEachIndexed { index, picture ->
            backs.pictured = { picture }
            backs.open(scope) { true }
            held.runAll()
            assertSame(picture, backs.take("post $index"))
        }
        assertNull(backs.take("post 0"), "the deepest card is dragged off the dark stage")
        assertSame(pictures[1], backs.take("post 1"))
        assertSame(pictures[2], backs.take("post 2"))
    }

    @Test
    fun noPictureIsTakenOfScreensThatWereNeverDrawn() {
        val held = Held()
        val scope = CoroutineScope(held + Job())
        val backs = CardBacks()
        backs.open(scope) { true }
        held.runAll()
        assertNull(backs.take("post"))
    }
}
