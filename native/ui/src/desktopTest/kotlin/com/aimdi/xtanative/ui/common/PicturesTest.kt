package com.aimdi.xtanative.ui.common

import coil3.PlatformContext
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PicturesTest {
    @Test
    fun aPictureOnPixivsServerIsAskedForWithItsReferer() {
        val url = "https://i.pximg.net/user-profile/img/2026/01/01/00/00/00/3_170.jpg"
        val request = assertIs<ImageRequest>(pictureModel(PlatformContext.INSTANCE, url), "an avatar as the tiles ask for their pictures")

        assertEquals(url, request.data)
        assertEquals("https://www.pixiv.net/", request.httpHeaders["Referer"])
    }

    @Test
    fun anyOtherPictureIsAskedForAsItIs() {
        val url = "https://pbs.twimg.com/profile_images/1/a_normal.jpg"
        assertEquals(url, pictureModel(PlatformContext.INSTANCE, url))
        assertEquals("https://i.pixiv.re/user-profile/img/3.jpg", pictureModel(PlatformContext.INSTANCE, "https://i.pixiv.re/user-profile/img/3.jpg"), "a mirror wants no Referer")
    }
}
