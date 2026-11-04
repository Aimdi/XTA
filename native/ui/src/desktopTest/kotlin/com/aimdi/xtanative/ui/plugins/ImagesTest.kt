package com.aimdi.xtanative.ui.plugins

import coil3.PlatformContext
import coil3.network.httpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals

class ImagesTest {
    @Test
    fun aPictureIsAskedForWithTheHeadersItsServerWants() {
        val url = "https://i.pximg.net/img-master/img/2026/01/01/00/00/00/1_p0_master1200.jpg"
        val request = imageRequest(PlatformContext.INSTANCE, url, mapOf("Referer" to "https://www.pixiv.net/", "User-Agent" to "Mozilla/5.0"))

        assertEquals(url, request.data)
        assertEquals("https://www.pixiv.net/", request.httpHeaders["Referer"])
        assertEquals("Mozilla/5.0", request.httpHeaders["User-Agent"])
    }

    @Test
    fun withoutHeadersNoneIsSent() {
        val request = imageRequest(PlatformContext.INSTANCE, "https://example.com/a.png", emptyMap())
        assertEquals(emptyMap(), request.httpHeaders.asMap())
    }
}
