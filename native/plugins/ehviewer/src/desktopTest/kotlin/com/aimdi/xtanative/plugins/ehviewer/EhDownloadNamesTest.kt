package com.aimdi.xtanative.plugins.ehviewer

import kotlin.test.Test
import kotlin.test.assertEquals

class EhDownloadNamesTest {
    private val url = "https://abc.hath.network:4433/h/f00d-1234-800-1200-jpg/keystamp=1;fileindex=2;xres=org/image.jpg"

    @Test
    fun aSavedPageIsNamedAfterItsGalleryAndItsPagePaddedToTheGallerysLength() {
        assertEquals("ehviewer-2345-007-image.jpg", EhApp.pageFileName(2345, 7, 120, url))
        assertEquals("ehviewer-2345-120-image.jpg", EhApp.pageFileName(2345, 120, 120, url))
        assertEquals("ehviewer-2345-3-image.jpg", EhApp.pageFileName(2345, 3, 9, url))
        assertEquals("ehviewer-2345-12-image.jpg", EhApp.pageFileName(2345, 12, 0, url), "a page past a length not known yet")
    }
}
