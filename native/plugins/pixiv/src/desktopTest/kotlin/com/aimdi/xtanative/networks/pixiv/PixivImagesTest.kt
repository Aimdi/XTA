package com.aimdi.xtanative.networks.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixivImagesTest {
    private val master = "https://i.pximg.net/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg"

    private val listed = PixivIllust(
        id = 120,
        title = "",
        caption = "",
        type = "manga",
        thumbnailUrl = "https://i.pximg.net/c/540x540_70/120_p0.jpg",
        largeUrl = "https://i.pximg.net/c/600x1200_90/120_p0.jpg",
        pageCount = 2,
        userId = 2,
        userName = "",
        userAccount = "",
        pageUrls = listOf("https://i.pximg.net/l/120_p0.jpg", "https://i.pximg.net/l/120_p1.jpg"),
        originalUrls = listOf("https://i.pximg.net/o/120_p0.png", "https://i.pximg.net/o/120_p1.png"),
        pageThumbUrls = listOf("https://i.pximg.net/m/120_p0.jpg", "https://i.pximg.net/m/120_p1.jpg"),
    )

    @Test
    fun pixivsOwnServerOrNoChoiceLeavesTheAddressAlone() {
        for (host in listOf(PixivImages.HOST, "", "  ")) assertEquals(master, PixivImages.url(master, host), host)
    }

    @Test
    fun theMirrorSwapsOnlyTheHost() {
        assertEquals(
            "https://i.pixiv.re/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg",
            PixivImages.url(master, PixivImages.MIRROR_HOST),
        )
    }

    @Test
    fun aServerOfTheReadersOwnKeepsItsPortAndPathPrefix() {
        assertEquals(
            "https://example.com:8443/pixiv/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg",
            PixivImages.url(master, "https://example.com:8443/pixiv/"),
        )
        assertTrue(PixivImages.url(master, "img.example.com").startsWith("https://img.example.com/img-master/"))
        assertTrue(PixivImages.url("$master?x=1", "img.example.com").endsWith("_master1200.jpg?x=1"))
    }

    @Test
    fun anUnusableServerIsRefusedAndChangesNothing() {
        for (host in listOf("img example.com", "ftp://example.com", "http://example.com", "https://user:pw@example.com", "https://", "?x")) {
            assertNull(PixivImages.parseHost(host), host)
            assertEquals(master, PixivImages.url(master, host), host)
        }
        assertNotNull(PixivImages.parseHost(" img.example.com "))
    }

    @Test
    fun theStaticServerAndOtherSitesAreNeverMirrored() {
        val avatar = "https://s.pximg.net/common/images/no_profile.png"
        assertEquals(avatar, PixivImages.url(avatar, PixivImages.MIRROR_HOST))
        assertEquals("https://example.org/a.jpg", PixivImages.url("https://example.org/a.jpg", PixivImages.MIRROR_HOST))
        assertEquals("not a url", PixivImages.url("not a url", PixivImages.MIRROR_HOST))
    }

    @Test
    fun aStoredHostReadsAsPixivTheMirrorOrACustomServer() {
        assertEquals(
            listOf(PixivImageHostChoice.PIXIV, PixivImageHostChoice.PIXIV, PixivImageHostChoice.MIRROR, PixivImageHostChoice.CUSTOM),
            listOf("", PixivImages.HOST, " i.pixiv.re ", "img.example.com").map(PixivImages::hostChoice),
        )
    }

    @Test
    fun everyPictureRequestCarriesPixivsReferer() {
        assertEquals("https://www.pixiv.net/", PixivImages.HEADERS["Referer"])
    }

    @Test
    fun aTileShowsTheMediumPreviewOrTheLargePictureWhenPixivSentOne() {
        assertEquals(listed.thumbnailUrl, PixivImages.tileUrl(listed, PixivImageQuality.MEDIUM))
        assertEquals(listed.largeUrl, PixivImages.tileUrl(listed, PixivImageQuality.LARGE))
        assertEquals(listed.thumbnailUrl, PixivImages.tileUrl(listed.copy(largeUrl = null), PixivImageQuality.LARGE))
    }

    @Test
    fun aPageComesInEachSizeFallingBackToWhatPixivSent() {
        assertEquals(listed.pageThumbUrls[1], PixivImages.pageUrl(listed, 1, PixivImageQuality.MEDIUM))
        assertEquals(listed.pageUrls[1], PixivImages.pageUrl(listed, 1, PixivImageQuality.LARGE))
        assertEquals(listed.originalUrls[1], PixivImages.pageUrl(listed, 1, PixivImageQuality.ORIGINAL))
        val bare = PixivIllust(1, "", "", "illust", "https://i.pximg.net/t.jpg", 1, 2, "", "")
        for (quality in PixivImageQuality.entries) assertEquals(bare.thumbnailUrl, PixivImages.pageUrl(bare, 0, quality), quality.name)
    }

    @Test
    fun pixivsStandInsForHiddenWorksAreRecognised() {
        for (url in listOf(
            "https://s.pximg.net/common/images/limit_unknown_360.png",
            "https://s.pximg.net/common/images/limit_r18_360.png",
            "https://s.pximg.net/common/images/limit_sanity_level_360.png",
            "https://s.pximg.net/common/images/limit_mypixiv_360.png",
        )) assertTrue(PixivImages.isLimitPlaceholder(url), url)
        assertFalse(PixivImages.isLimitPlaceholder(master))
        assertFalse(PixivImages.isLimitPlaceholder(null))
    }
}
