package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live Pixiv, read without an account: the showcase Pixiv offers before
 * sign-in, the one read that takes no token, and a picture of it from
 * Pixiv's image server with the Referer it wants. Skipped unless RUN_LIVE=true.
 */
class PixivLiveTest {
    @Test
    fun theSignedOutShowcaseReadsAndItsPicturesComeWithPixivsReferer() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Pixiv")
        val http = OkHttpXHttp()
        val api = PixivDiscoveryApi(PixivClient(http, MemoryPixivCredentials()))

        val page = api.walkthrough()
        assertTrue(page.items.isNotEmpty(), "the showcase has works")
        val work = page.items.first()
        assertTrue(work.thumbnailUrl.contains("pximg.net"), work.thumbnailUrl)

        val picture = http.get(work.thumbnailUrl.toHttpUrl(), PixivImages.HEADERS, followRedirects = true)
        assertEquals(200, picture.code, "Pixiv's image server serves a picture asked for with its Referer")
        val bare = http.get(work.thumbnailUrl.toHttpUrl(), emptyMap(), followRedirects = true)
        println("LIVE showcase: ${page.items.size} works, next ${page.next != null}; picture ${picture.code} with the Referer, ${bare.code} without")
    }
}
