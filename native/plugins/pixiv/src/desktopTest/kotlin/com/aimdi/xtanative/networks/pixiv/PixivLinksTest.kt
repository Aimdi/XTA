package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.x.http.XResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class PixivLinksTest {
    private fun parse(link: String) = PixivLinks.parse(link)

    @Test
    fun aBareNumberIsAWork() {
        assertEquals(PixivLinkRef.Artwork(123), parse("123"))
    }

    @Test
    fun worksAreReadFromAddressesAndPaths() {
        for (link in listOf(
            "https://www.pixiv.net/artworks/123", "https://www.pixiv.net/en/artworks/123", "www.pixiv.net/artworks/123", "/artworks/123",
            "artworks/123", "https://pixiv.me/member_illust.php?illust_id=123",
        )) {
            assertEquals(PixivLinkRef.Artwork(123), parse(link), link)
        }
    }

    @Test
    fun creatorsAreReadFromAddressesAndPaths() {
        for (link in listOf("https://www.pixiv.net/users/456", "https://www.pixiv.net/en/users/456", "users/456", "user/456")) {
            assertEquals(PixivLinkRef.User(456), parse(link), link)
        }
    }

    @Test
    fun unsupportedLinksNameNothing() {
        for (link in listOf(
            "https://example.com/users/1", "not pixiv", "users/nope", "https://www.pixiv.net/ranking.php", "pixiv://account/login?code=abc",
            "https://i.pximg.net/user-profile/img/2023/09/15/12/22/50/4004637_abc_170.jpg", "https://www.pixiv.net/jump.php?%E0%A4%A", "",
        )) {
            assertNull(parse(link), link)
        }
    }

    @Test
    fun theShortFormsAndTheOldPhpPagesAreRead() {
        assertEquals(PixivLinkRef.Artwork(123), parse("https://www.pixiv.net/i/123"))
        assertEquals(PixivLinkRef.User(456), parse("https://www.pixiv.net/u/456"))
        assertEquals(PixivLinkRef.User(456), parse("https://www.pixiv.net/member.php?id=456"))
        assertEquals(PixivLinkRef.User(456), parse("https://www.pixiv.net/member_illust.php?id=456&type=all"))
        assertEquals(PixivLinkRef.User(456, "works"), parse("https://www.pixiv.net/users/456/artworks"))
    }

    @Test
    fun aTagPageOpensAsItsTagDecoded() {
        for (link in listOf("https://www.pixiv.net/tags/%E7%8C%AB/artworks?s_mode=s_tag", "https://www.pixiv.net/en/tags/猫", "tags/猫")) {
            assertEquals(PixivLinkRef.Tag("猫"), parse(link), link)
        }
    }

    @Test
    fun aTagsNovelsPageSearchesNovelsAndItsOtherPagesWorks() {
        assertEquals(PixivLinkRef.Tag("猫", novels = true), parse("https://www.pixiv.net/tags/%E7%8C%AB/novels"))
        for (link in listOf("https://www.pixiv.net/tags/猫/artworks", "https://www.pixiv.net/tags/猫")) {
            assertEquals(PixivLinkRef.Tag("猫", novels = false), parse(link), link)
        }
    }

    @Test
    fun aCreatorsPageOpensTheirProfileOnTheTabItNames() {
        for ((page, tab) in listOf(
            "" to null, "/artworks" to "works", "/illustrations" to "works", "/manga" to "works", "/novels" to "novels",
            "/bookmarks/artworks" to "bookmarks", "/bookmarks/novels" to "bookmarks", "/following" to "following", "/followers" to null,
        )) {
            assertEquals(PixivLinkRef.User(456, tab), parse("https://www.pixiv.net/users/456$page"), page)
        }
    }

    @Test
    fun aPictureFileNamesItsWork() {
        for (link in listOf(
            "https://i.pximg.net/img-original/img/2026/07/01/00/00/00/123_p0.png",
            "https://i.pximg.net/c/600x1200_90/img-master/img/2026/07/01/00/00/00/123_p3_master1200.jpg",
            "i.pximg.net/img-zip-ugoira/img/2026/07/01/00/00/00/123_ugoira600x600.zip",
        )) {
            assertEquals(PixivLinkRef.Artwork(123), parse(link), link)
        }
    }

    @Test
    fun pixivsAppLinksAreRead() {
        assertEquals(PixivLinkRef.Artwork(123), parse("pixiv://illusts/123"))
        assertEquals(PixivLinkRef.User(456), parse("pixiv://users/456"))
        assertEquals(PixivLinkRef.Novel(789), parse("pixiv://novels/789"))
    }

    @Test
    fun seriesNovelsAndPixivisionKnowThePageABrowserOpens() {
        val series = parse("https://www.pixiv.net/user/4004637/series/266067")
        assertEquals(PixivLinkRef.Series(266067, 4004637), series)
        assertEquals("https://www.pixiv.net/user/4004637/series/266067", (series as PixivLinkRef.WebPage).webUrl)
        val novel = parse("https://www.pixiv.net/novel/show.php?id=789")
        assertEquals(PixivLinkRef.Novel(789), novel)
        assertEquals("https://www.pixiv.net/novel/show.php?id=789", (novel as PixivLinkRef.WebPage).webUrl)
        assertEquals(PixivLinkRef.Novel(789), parse("https://www.pixiv.net/n/789"))
        assertEquals(PixivLinkRef.NovelSeries(55), parse("https://www.pixiv.net/novel/series/55"))
        val article = parse("https://www.pixivision.net/ja/a/9876")
        assertEquals(PixivLinkRef.PixivisionPage(9876, "ja"), article)
        assertEquals("https://www.pixivision.net/ja/a/9876", (article as PixivLinkRef.WebPage).webUrl)
    }

    @Test
    fun aPixivMeNameIsKeptForResolvingButItsOldPhpLinksAreReadAtOnce() {
        assertEquals(PixivLinkRef.ShortLink("mika"), parse("https://pixiv.me/mika"))
        assertEquals(PixivLinkRef.ShortLink("mika"), parse("pixiv.me/mika"))
        assertNull(parse("https://pixiv.me/a/b"))
    }

    @Test
    fun aShortLinkFollowsOneRedirectByHandToAPixivNetPage() = runBlocking {
        val http = FakePixivHttp { XResponse(302, "", mapOf("Location" to listOf("https://www.pixiv.net/users/456"))) }
        assertEquals(PixivLinkRef.User(456), PixivLinks.resolve(http, PixivLinkRef.ShortLink("mika")))
        val asked = http.asked.single()
        assertEquals("https://pixiv.me/mika" to false, asked.url.toString() to asked.followRedirects)
    }

    @Test
    fun aShortLinkTrustsOnlyAPixivNetLocation() = runBlocking {
        for (location in listOf("https://evil.example/users/456", "https://pixiv.net.evil.example/users/456", "pixiv://users/456", "https://pixiv.me/other")) {
            val http = FakePixivHttp { XResponse(302, "", mapOf("location" to listOf(location))) }
            assertNull(PixivLinks.resolve(http, PixivLinkRef.ShortLink("mika")), location)
        }
    }

    @Test
    fun aShortLinkWithoutARedirectOrWhoseRequestFailsResolvesToNothing() = runBlocking {
        assertNull(PixivLinks.resolve(FakePixivHttp { answer("", 404) }, PixivLinkRef.ShortLink("mika")))
        assertNull(PixivLinks.resolve(FakePixivHttp { throw IOException("offline") }, PixivLinkRef.ShortLink("mika")))
    }
}
