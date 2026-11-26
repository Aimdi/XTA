package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.ui.Routes
import com.aimdi.xtanative.ui.media.Galleries
import com.aimdi.xtanative.ui.media.MediaGallery
import com.aimdi.xtanative.ui.media.ViewerPage
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A double-tap opens a screen once. */
class NavRepeatsTest {
    @Test
    fun theSameScreenAgainAtOnceIsLetGo() {
        val repeats = NavRepeats()
        assertTrue(repeats.allows("post/1", "home", 1_000))
        repeats.pushed("post/1", "e1", 1_000)
        assertFalse(repeats.allows("post/1", "e1", 1_200), "the second tap of a double-tap")
    }

    @Test
    fun anotherScreenOpens() {
        val repeats = NavRepeats()
        repeats.pushed("post/1", "e1", 1_000)
        assertTrue(repeats.allows("profile/maya", "e1", 1_100))
    }

    @Test
    fun afterGoingBackOrAMomentItOpensAgain() {
        val repeats = NavRepeats()
        repeats.pushed("post/1", "e1", 1_000)
        assertTrue(repeats.allows("post/1", "home", 1_200), "back on the screen below")
        assertTrue(repeats.allows("post/1", "e1", 1_000 + NavRepeats.WINDOW_MS), "a moment later")
    }

    @Test
    fun aGridTileDoubleTappedOpensOneViewerThoughEachTapKeepsItsGalleryAnew() {
        val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/A.jpg", 10, 10)
        val page = ViewerPage(photo, "1", "maya", 0)
        val gallery = MediaGallery.of(listOf(page))
        val first = Routes.gallery(Galleries.put(gallery), 0, page)
        val second = Routes.gallery(Galleries.put(gallery), 0, page)
        assertNotEquals(first, second, "each tap's route names a new key")
        val repeats = NavRepeats()
        repeats.pushed(Routes.galleryOpening(0, page), "viewer", 1_000)
        assertFalse(repeats.allows(Routes.galleryOpening(0, page), "viewer", 1_200), "the second tap of a double-tap")
        assertTrue(repeats.allows(Routes.galleryOpening(0, ViewerPage(photo, "2", "maya", 0)), "viewer", 1_200), "another tile")
    }
}
