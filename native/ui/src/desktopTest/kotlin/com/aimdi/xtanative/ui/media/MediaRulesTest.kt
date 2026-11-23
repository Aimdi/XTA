package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.settings.MediaQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaRulesTest {
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/poster.jpg",
        1280,
        720,
        variants = listOf(
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/pl/list.m3u8?tag=12", null, "application/x-mpegURL"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/480x270/low.mp4?tag=12", 256_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/high.mp4?tag=12", 2_176_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/640x360/mid.mp4?tag=12", 832_000, "video/mp4"),
        ),
    )

    @Test
    fun qualitiesAreTheMp4sHighestFirstNamedByTheirHeight() {
        assertEquals(listOf("720p", "360p", "270p"), VideoRules.qualities(video).map { it.label })
        assertEquals("2.2 Mbps", VideoRules.label("https://video.twimg.com/amplify/a.mp4", 2_176_000))
        assertEquals("—", VideoRules.label("https://video.twimg.com/a.mp4", null))
        assertTrue(VideoRules.streamUrl(video)!!.contains("high.mp4"))
        val live = video.copy(variants = video.variants.take(1))
        assertTrue(VideoRules.streamUrl(live)!!.endsWith("list.m3u8?tag=12"))
    }

    @Test
    fun theQualitySettingPicksAVariant() {
        val qualities = VideoRules.qualities(video)
        assertEquals(qualities[0].url, VideoRules.defaultUrl(qualities, null, MediaQuality.LARGE))
        assertEquals(qualities[1].url, VideoRules.defaultUrl(qualities, null, MediaQuality.MEDIUM))
        assertEquals(qualities[2].url, VideoRules.defaultUrl(qualities, null, MediaQuality.SMALL))
        assertEquals(qualities[2].url, VideoRules.defaultUrl(qualities, null, MediaQuality.THUMB))
        assertEquals("fallback", VideoRules.defaultUrl(emptyList(), "fallback", MediaQuality.LARGE))
    }

    @Test
    fun timesAndSeeksStayWithinTheVideo() {
        assertEquals("0:05", VideoRules.timeText(5_400))
        assertEquals("12:00", VideoRules.timeText(720_000))
        assertEquals("1:02:05", VideoRules.timeText(3_725_000))
        assertEquals("0:00", VideoRules.timeText(-1))
        assertEquals(0, VideoRules.seekTarget(3_000, -10_000, 60_000))
        assertEquals(60_000, VideoRules.seekTarget(55_000, 10_000, 60_000))
        assertEquals(25_000, VideoRules.seekTarget(15_000, 10_000, 60_000))
        assertEquals(0.5f, VideoRules.fraction(30_000, 60_000))
        assertEquals(0f, VideoRules.fraction(30_000, 0))
        assertEquals(listOf("2.0", "1.75", "1.5", "1.25", "1.0", "0.75", "0.5", "0.25"), VideoRules.SPEEDS.reversed().map(VideoRules::speedText))
    }

    @Test
    fun aDownloadSavesTheVariantWatchedOrTheBest() {
        val qualities = VideoRules.qualities(video)
        assertEquals(qualities[1].url, MediaFiles.downloadUrl(video, playingUrl = qualities[1].url))
        assertEquals(qualities[0].url, MediaFiles.downloadUrl(video, playingUrl = video.variants[0].url))
        assertEquals(qualities[0].url, MediaFiles.downloadUrl(video))
        val gif = video.copy(kind = XMedia.Kind.GIF, variants = listOf(XVideoVariant("https://video.twimg.com/tweet_video/abc.mp4", 0, "video/mp4")))
        assertEquals("https://video.twimg.com/tweet_video/abc.mp4", MediaFiles.downloadUrl(gif))
        assertNull(MediaFiles.downloadUrl(video.copy(variants = video.variants.take(1))))
        val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/Fabc.jpg", 100, 100)
        assertEquals("https://pbs.twimg.com/media/Fabc?format=jpg&name=orig", MediaFiles.downloadUrl(photo))
    }

    @Test
    fun aSavedFileIsNamedForItsAuthor() {
        assertEquals("maya-Fabc.jpg", MediaFiles.fileName("maya", "https://pbs.twimg.com/media/Fabc?format=jpg&name=orig"))
        assertEquals("maya-high.mp4", MediaFiles.fileName("maya", "https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/high.mp4?tag=12"))
        assertEquals("abc.mp4", MediaFiles.fileName("", "https://video.twimg.com/tweet_video/abc.mp4"))
        val save = MediaFiles.save(video, "42", "maya")!!
        assertEquals("maya-high.mp4", save.fileName)
        assertEquals("42", save.postId)
        assertEquals(XMedia.Kind.VIDEO, save.kind)
    }

    @Test
    fun theViewerShowsPhotosAtTheChosenSize() {
        assertEquals(PhotoSize.MEDIUM, MediaFiles.viewerSize(MediaQuality.MEDIUM))
        assertEquals(PhotoSize.LARGE, MediaFiles.viewerSize(MediaQuality.LARGE))
        assertEquals(PhotoSize.THUMB, MediaFiles.viewerSize(MediaQuality.THUMB))
    }

    @Test
    fun aPictureThatIsNoPostsIsShownAndSavedAsItIs() {
        val face = "https://pbs.twimg.com/profile_images/1/face_400x400.jpg"
        val alone = ViewerMedia.image(face, "maya")
        val shown = alone.pages.single()
        assertEquals(face, alone.shownUrl(shown.media, PhotoSize.MEDIUM))
        assertEquals(MediaSave(face, "maya-face_400x400.jpg", XMedia.Kind.PHOTO, ""), alone.save(shown))
        val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/Fabc.jpg", 100, 100)
        val posted = ViewerMedia.of(listOf(photo), "maya", "42")
        assertEquals("https://pbs.twimg.com/media/Fabc?format=jpg&name=medium", posted.shownUrl(photo, PhotoSize.MEDIUM))
        assertEquals(MediaFiles.save(photo, "42", "maya"), posted.save(posted.pages.single()))
        assertTrue(ViewerMedia.of(null, "42").items.isEmpty())
    }

    @Test
    fun aPollsPicturesAreShownAsTheyAreAndSavedUnderItsAuthor() {
        val choices = ViewerMedia.images(listOf("https://pbs.twimg.com/card_img/1/a?format=jpg&name=orig", "https://pbs.twimg.com/card_img/1/b?format=png&name=orig"), "maya", "42")
        assertEquals(listOf(0, 1), choices.pages.map { it.index })
        assertEquals("https://pbs.twimg.com/card_img/1/b?format=png&name=orig", choices.shownUrl(choices.items[1], PhotoSize.SMALL))
        assertEquals(MediaSave("https://pbs.twimg.com/card_img/1/a?format=jpg&name=orig", "maya-a.jpg", XMedia.Kind.PHOTO, "42"), choices.save(choices.pages[0]))
    }

    @Test
    fun aGridsViewerSavesEachPageUnderItsOwnPostsAuthor() {
        val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/Fa.jpg", 100, 100)
        val pages = listOf(ViewerPage(photo, "1", "maya", 0), ViewerPage(video, "2", "jo", 3))
        val grid = ViewerMedia(pages)
        assertEquals(listOf(photo, video), grid.items)
        assertEquals("maya-Fa.jpg", grid.save(pages[0])?.fileName)
        assertEquals(MediaSave(VideoRules.qualities(video).first().url, "jo-high.mp4", XMedia.Kind.VIDEO, "2"), grid.save(pages[1]))
    }

    @Test
    fun aDoubleTapZoomsAroundThePointTapped() {
        assertEquals(ViewerZoom.MAX, ViewerZoom.doubleTapScale(1f))
        assertEquals(ViewerZoom.MIN, ViewerZoom.doubleTapScale(2.5f))
        // Tapping 100 px right of the middle of a 1000 × 800 page: that point stays under the finger.
        val shift = ViewerZoom.shiftFor(Shift(100f, 0f), from = 1f, fromShift = Shift.ZERO, to = 4f, width = 1000f, height = 800f)
        assertEquals(Shift(-300f, 0f), shift)
        assertEquals(Shift.ZERO, ViewerZoom.shiftFor(Shift(100f, 0f), from = 4f, fromShift = shift, to = 1f, width = 1000f, height = 800f))
        // A shift stops where the photo still covers the page.
        assertEquals(Shift(-1500f, 900f), ViewerZoom.bounds(Shift(-2000f, 900f), 4f, 1000f, 800f))
        assertEquals(Shift.ZERO, ViewerZoom.bounds(Shift(-2000f, 900f), 1f, 1000f, 800f))
        assertEquals(Shift(-1500f, -1200f), ViewerZoom.shiftFor(Shift(500f, 400f), 1f, Shift.ZERO, 4f, 1000f, 800f))
        assertEquals(0.9f, ViewerZoom.pinched(1f, 0.5f))
        assertEquals(4f, ViewerZoom.pinched(3f, 2f))
        assertEquals(1f, ViewerZoom.settled(0.9f))
        assertFalse(ViewerZoom.zoomed(1f))
        assertTrue(ViewerZoom.zoomed(1.5f))
    }

    @Test
    fun aDragUpOrDownClosesTheViewerWhenItGoesFarOrFast() {
        assertTrue(SlideToClose.startsSlide(dx = 2f, dy = 20f, slop = 8f))
        assertFalse(SlideToClose.startsSlide(dx = 18f, dy = 20f, slop = 8f))
        assertTrue(SlideToClose.startsPage(dx = 20f, dy = 4f, slop = 8f))
        assertTrue(SlideToClose.closes(drag = 200f, height = 1000f, velocity = 0f))
        assertFalse(SlideToClose.closes(drag = 100f, height = 1000f, velocity = 0f))
        assertTrue(SlideToClose.closes(drag = 10f, height = 1000f, velocity = -2_500f))
        assertEquals(1f, SlideToClose.backdrop(0f, 1000f))
        assertEquals(0.5f, SlideToClose.backdrop(300f, 1000f))
        assertEquals(0.2f, SlideToClose.backdrop(5000f, 1000f))
    }

    @Test
    fun theMouseWheelZoomsInAndOutWithinTheEnds() {
        assertEquals(1.2f, ViewerZoom.wheeled(1f, -1f), 0.001f, "a notch away from the reader zooms in")
        assertEquals(1f, ViewerZoom.wheeled(1.2f, 1f), 0.001f, "a notch towards the reader zooms out")
        assertEquals(ViewerZoom.MIN, ViewerZoom.wheeled(1f, 3f), "never below the fitted size")
        assertEquals(ViewerZoom.MAX, ViewerZoom.wheeled(3.9f, -5f), "never past the most")
        assertEquals(2f, ViewerZoom.wheeled(2f, 0f))
    }

    @Test
    fun aVideoFullScreenTakesThePlayersKeys() {
        fun key(escape: Boolean = false, space: Boolean = false, left: Boolean = false, right: Boolean = false, modified: Boolean = false) =
            VideoKeys.of(escape, space, left, right, modified)
        assertEquals(VideoKey.LEAVE, key(escape = true))
        assertEquals(VideoKey.PLAY_PAUSE, key(space = true))
        assertEquals(VideoKey.BACK, key(left = true))
        assertEquals(VideoKey.FORWARD, key(right = true))
        assertEquals(VideoKey.NONE, key())
        assertEquals(VideoKey.NONE, key(space = true, modified = true), "Ctrl+Space is the window's")
        assertEquals(VideoKey.NONE, key(left = true, modified = true), "Alt+← goes back, not ten seconds")
        assertEquals(VideoKey.LEAVE, key(escape = true, modified = true))
    }

    @Test
    fun theViewerTurnsWithTheArrowsAndGoesBackWithAltLeft() {
        fun key(escape: Boolean = false, left: Boolean = false, right: Boolean = false, back: Boolean = false, modified: Boolean = false) =
            ViewerKeys.of(escape, left, right, back, modified)
        assertEquals(ViewerKey.PREVIOUS, key(left = true))
        assertEquals(ViewerKey.NEXT, key(right = true))
        assertEquals(ViewerKey.CLOSE, key(escape = true))
        assertEquals(ViewerKey.CLOSE, key(left = true, back = true, modified = true), "Alt+← goes back, not to the photo before")
        assertEquals(ViewerKey.CLOSE, key(back = true), "the Back key")
        assertEquals(ViewerKey.NONE, key(right = true, modified = true), "Ctrl+→ is not the viewer's")
        assertEquals(ViewerKey.NONE, key())
    }

    @Test
    fun goingBackLeavesAVideoFullScreen() {
        assertEquals(VideoKey.LEAVE, VideoKeys.of(escape = false, space = false, left = true, right = false, modified = true, back = true))
        assertEquals(VideoKey.LEAVE, VideoKeys.of(escape = false, space = false, left = false, right = false, modified = false, back = true))
    }

    @Test
    fun plusMinusAndZeroZoomTheViewersPhoto() {
        fun key(zoom: ViewerKey, modified: Boolean = false) = ViewerKeys.of(escape = false, left = false, right = false, back = false, modified = modified, zoom = zoom)
        assertEquals(ViewerKey.ZOOM_IN, key(ViewerKey.ZOOM_IN))
        // With Ctrl too, as a browser zooms.
        assertEquals(ViewerKey.ZOOM_OUT, key(ViewerKey.ZOOM_OUT, modified = true))
        assertEquals(ViewerKey.ZOOM_RESET, key(ViewerKey.ZOOM_RESET))
        assertEquals(ViewerKey.NONE, key(ViewerKey.NONE))
        // A zoom key is no other key.
        assertEquals(ViewerKey.NONE, key(ViewerKey.CLOSE))
        assertEquals(ViewerZoom.wheeled(1f, -1f), ViewerZoom.keyed(1f, ViewerKey.ZOOM_IN))
        assertEquals(ViewerZoom.wheeled(2f, 1f), ViewerZoom.keyed(2f, ViewerKey.ZOOM_OUT))
        assertEquals(ViewerZoom.MIN, ViewerZoom.keyed(3f, ViewerKey.ZOOM_RESET))
        assertEquals(ViewerZoom.MIN, ViewerZoom.keyed(ViewerZoom.MIN, ViewerKey.ZOOM_OUT))
        assertEquals(2f, ViewerZoom.keyed(2f, ViewerKey.NEXT))
    }

    @Test
    fun theArrowsMoveAboutAZoomedPhotoAndHomeAndEndGoToTheEnds() {
        fun key(left: Boolean = false, right: Boolean = false, up: Boolean = false, down: Boolean = false, home: Boolean = false, end: Boolean = false, zoomed: Boolean = false) =
            ViewerKeys.of(escape = false, left = left, right = right, back = false, modified = false, up = up, down = down, home = home, end = end, zoomed = zoomed)
        assertEquals(ViewerKey.PREVIOUS, key(left = true))
        assertEquals(ViewerKey.NEXT, key(right = true))
        assertEquals(ViewerKey.NONE, key(up = true))
        assertEquals(ViewerKey.PAN_LEFT, key(left = true, zoomed = true))
        assertEquals(ViewerKey.PAN_RIGHT, key(right = true, zoomed = true))
        assertEquals(ViewerKey.PAN_UP, key(up = true, zoomed = true))
        assertEquals(ViewerKey.PAN_DOWN, key(down = true, zoomed = true))
        assertEquals(ViewerKey.FIRST, key(home = true))
        assertEquals(ViewerKey.LAST, key(end = true, zoomed = true))
        // Zoomed twice over on a 1000 x 800 page: the view moves 150 px and stops at the photo's edge (500 px).
        assertEquals(Shift(150f, 0f), ViewerZoom.panned(Shift.ZERO, 2f, ViewerKey.PAN_LEFT, 1000f, 800f))
        assertEquals(Shift(0f, -150f), ViewerZoom.panned(Shift.ZERO, 2f, ViewerKey.PAN_DOWN, 1000f, 1000f))
        assertEquals(Shift(-500f, 0f), ViewerZoom.panned(Shift(-450f, 0f), 2f, ViewerKey.PAN_RIGHT, 1000f, 800f))
        assertEquals(Shift.ZERO, ViewerZoom.panned(Shift.ZERO, 1f, ViewerKey.PAN_LEFT, 1000f, 800f))
    }

    @Test
    fun theWebPlayersLettersWorkFullScreen() {
        fun key(letter: Char? = null, home: Boolean = false, modified: Boolean = false) =
            VideoKeys.of(escape = false, space = false, left = false, right = false, modified = modified, letter = letter, home = home)
        assertEquals(VideoKey.PLAY_PAUSE, key('k'))
        assertEquals(VideoKey.BACK, key('j'))
        assertEquals(VideoKey.FORWARD, key('l'))
        assertEquals(VideoKey.MUTE, key('m'))
        assertEquals(VideoKey.LEAVE, key('f'))
        assertEquals(VideoKey.RESTART, key(home = true))
        // With Ctrl or Alt they are the window's.
        assertEquals(VideoKey.NONE, key('m', modified = true))
        assertEquals(VideoKey.NONE, key('x'))
    }

    @Test
    fun aVideoOnTheViewersPageTakesThePlayersLetters() {
        assertEquals(VideoKey.PLAY_PAUSE, VideoKeys.onViewerPage(space = true, letter = null, modified = false))
        assertEquals(VideoKey.PLAY_PAUSE, VideoKeys.onViewerPage(space = false, letter = 'k', modified = false))
        assertEquals(VideoKey.FORWARD, VideoKeys.onViewerPage(space = false, letter = 'l', modified = false))
        assertEquals(VideoKey.MUTE, VideoKeys.onViewerPage(space = false, letter = 'm', modified = false))
        // f goes full screen here, rather than leaving it.
        assertEquals(VideoKey.FULLSCREEN, VideoKeys.onViewerPage(space = false, letter = 'f', modified = false))
        assertEquals(VideoKey.NONE, VideoKeys.onViewerPage(space = false, letter = 'm', modified = true))
        assertEquals(VideoKey.NONE, VideoKeys.onViewerPage(space = false, letter = null, modified = false))
    }
}
