package com.aimdi.xtanative.ui.media

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals

/** The media viewer's keys on the page shown, as the app's viewer and the plugins' MediaPager read them. */
@OptIn(InternalComposeUiApi::class) // The only way to make a key event outside a window; the test reads it as the app does.
class PageKeyTest {
    private fun down(key: Key, ctrl: Boolean = false, char: Char? = null) =
        KeyEvent(key, KeyEventType.KeyDown, codePoint = char?.code ?: 0, isCtrlPressed = ctrl)

    private fun typed(char: Char) = KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = char.code)

    @Test
    fun aVideoShownTakesThePlayerKeysFirst() {
        assertEquals(PageKey.Video(VideoKey.PLAY_PAUSE), pageKeyOf(down(Key.Spacebar, char = ' '), onVideo = true, zoomed = false))
        assertEquals(PageKey.Video(VideoKey.PLAY_PAUSE), pageKeyOf(down(Key.K, char = 'k'), onVideo = true, zoomed = false))
        assertEquals(PageKey.Video(VideoKey.MUTE), pageKeyOf(down(Key.M, char = 'm'), onVideo = true, zoomed = false))
        assertEquals(PageKey.Video(VideoKey.FULLSCREEN), pageKeyOf(down(Key.F, char = 'f'), onVideo = true, zoomed = false))
        assertEquals(PageKey.Video(VideoKey.FASTER), pageKeyOf(typed('>'), onVideo = true, zoomed = false))
        assertEquals(PageKey.None, pageKeyOf(typed('>'), onVideo = false, zoomed = false))
        // Ctrl held: the window's, not the video's.
        assertEquals(PageKey.None, pageKeyOf(down(Key.K, ctrl = true, char = 'k'), onVideo = true, zoomed = false))
    }

    @Test
    fun aDigitTypedJumpsInAVideoAndZeroShowsAPhotoWhole() {
        val jump = pageKeyOf(typed('5'), onVideo = true, zoomed = false)
        assertEquals(5, (jump as PageKey.Video).key.tenths)
        assertEquals(PageKey.Viewer(ViewerKey.ZOOM_RESET), pageKeyOf(down(Key.Zero, char = '0'), onVideo = false, zoomed = true))
    }

    @Test
    fun theViewersOwnKeysStayTheViewers() {
        assertEquals(PageKey.Viewer(ViewerKey.NEXT), pageKeyOf(down(Key.DirectionRight), onVideo = true, zoomed = false))
        assertEquals(PageKey.Viewer(ViewerKey.LAST), pageKeyOf(down(Key.MoveEnd), onVideo = true, zoomed = false))
        assertEquals(PageKey.Viewer(ViewerKey.PAN_RIGHT), pageKeyOf(down(Key.DirectionRight), onVideo = false, zoomed = true))
        assertEquals(PageKey.Viewer(ViewerKey.ZOOM_IN), pageKeyOf(down(Key.Equals, char = '='), onVideo = false, zoomed = false))
        assertEquals(PageKey.Viewer(ViewerKey.CLOSE), pageKeyOf(down(Key.Escape), onVideo = true, zoomed = false))
        assertEquals(PageKey.None, pageKeyOf(down(Key.K, char = 'k'), onVideo = false, zoomed = false))
    }

    @Test
    fun aKeyLetGoIsNothing() {
        val up = KeyEvent(Key.Spacebar, KeyEventType.KeyUp, codePoint = ' '.code)
        assertEquals(PageKey.None, pageKeyOf(up, onVideo = true, zoomed = false))
    }

    @Test
    fun keysAndTheWheelZoomAndPanWithinThePicture() {
        val wheeledIn = ViewerZoom.wheeled(1f, -1f)
        assertEquals(ViewerZoom.WHEEL_STEP, wheeledIn)
        assertEquals(ViewerZoom.MIN, ViewerZoom.wheeled(1f, 3f))
        assertEquals(ViewerZoom.MAX, ViewerZoom.wheeled(3.9f, -2f))
        assertEquals(wheeledIn, ViewerZoom.keyed(1f, ViewerKey.ZOOM_IN))
        assertEquals(ViewerZoom.MIN, ViewerZoom.keyed(3f, ViewerKey.ZOOM_RESET))
        // Panned right at 2x on a 1000 by 800 page: the picture moves left, never past its edge.
        assertEquals(Shift(-150f, 0f), ViewerZoom.panned(Shift.ZERO, 2f, ViewerKey.PAN_RIGHT, 1000f, 800f))
        assertEquals(Shift(-500f, 0f), ViewerZoom.panned(Shift(-450f, 0f), 2f, ViewerKey.PAN_RIGHT, 1000f, 800f))
    }
}
