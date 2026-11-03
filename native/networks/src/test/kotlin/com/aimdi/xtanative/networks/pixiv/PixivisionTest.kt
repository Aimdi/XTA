package com.aimdi.xtanative.networks.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixivisionTest {
    private val english = Pixivision.parse(Fixtures.text("pixivision/article_en.html"))
    private val chinese = Pixivision.parse(Fixtures.text("pixivision/article_zh.html"))

    @Test
    fun theEnglishLayoutGivesTheTitleTheDescriptionAsIntroAndTheCover() {
        assertEquals("Cats in Autumn", english.title)
        assertEquals("Autumn is here, and the cats know it.\n\nEnjoy these cosy pieces!", english.intro)
        assertTrue(english.coverUrl.orEmpty().contains("1000_p0_master1200.jpg"))
    }

    @Test
    fun eachFeaturedWorkComesOnceWithItsArtistAndNothingOutsideTheArticle() {
        assertEquals(
            listOf(
                PixivisionWork(
                    1001, 501, "Maple Cat", "Mika",
                    "https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/01/00/00/00/1001_p0_master1200.jpg",
                    "https://i.pximg.net/user-profile/img/2020/01/01/00/00/00/501_170.jpg",
                ),
                PixivisionWork(
                    1002, 502, "Sleepy Leaves", "Sora",
                    "https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/02/00/00/00/1002_p0_master1200.jpg",
                    "https://i.pximg.net/user-profile/img/2021/01/01/00/00/00/502_170.jpg",
                ),
            ),
            english.works,
        )
    }

    @Test
    fun theChineseLayoutTakesTheOpeningParagraphsAsIntro() {
        assertEquals("秋天的猫咪", chinese.title)
        assertEquals("秋天来了。\n\n一起欣赏猫咪插画吧！", chinese.intro)
    }

    @Test
    fun lazyPicturesTrackingQueriesAndOldMemberLinksAreRead() {
        assertEquals(
            listOf(
                PixivisionWork(
                    2001, 601, "枫叶猫", "小雪",
                    "https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/03/00/00/00/2001_p0_master1200.jpg",
                    "https://i.pximg.net/user-profile/img/2022/01/01/00/00/00/601_170.jpg",
                ),
                PixivisionWork(
                    2002, 602, "落叶午睡", "阿空",
                    "https://i.pximg.net/c/768x1200_80/img-master/img/2024/09/04/00/00/00/2002_p0_master1200.jpg",
                    "https://i.pximg.net/user-profile/img/2022/01/02/00/00/00/602_170.jpg",
                ),
            ),
            chinese.works,
        )
    }

    @Test
    fun aPageWithNoArticleReadsAsEmptyRatherThanThrowing() {
        val maintenance = Pixivision.parse("<html><body><p>Maintenance</p></body></html>")
        assertTrue(maintenance.works.isEmpty())
        assertFalse(maintenance.isEmpty, "the stray paragraph is all the page says")
        assertTrue(Pixivision.parse("").isEmpty)
    }

    @Test
    fun aWorkWithoutAnArtistLinkIsLeftOutInsteadOfBorrowingANeighbours() {
        val article = Pixivision.parse(
            """
            <article><div class="am__body">
              <div><a href="https://www.pixiv.net/artworks/1"><img src="https://i.pximg.net/1.jpg"></a></div>
              <div>
                <a href="https://www.pixiv.net/artworks/2">Two</a>
                <a href="https://www.pixiv.net/users/20">Artist</a>
              </div>
            </div></article>
            """.trimIndent(),
        )
        assertEquals(listOf(Triple(2L, 20L, "Artist")), article.works.map { Triple(it.artworkId, it.userId, it.userName) })
    }

    @Test
    fun aBlockMissingItsTitleAndPicturesStillGivesTheIds() {
        val work = Pixivision.parse(
            """<article><div class="am__body"><div><a href="/en/artworks/7"></a><a href="/en/users/70"></a></div></div></article>""",
        ).works.single()
        assertEquals(PixivisionWork(7, 70, "", ""), work)
        assertNull(work.imageUrl)
    }

    @Test
    fun thePageLanguageFollowsTheLocale() {
        assertEquals(
            mapOf("ja" to "ja", "ko" to "ko", "zh" to "zh", "zh_Hans" to "zh", "zh_Hant" to "zh-tw", "zh-TW" to "zh-tw", "en" to "en", "de" to "en", "pt_BR" to "en"),
            listOf("ja", "ko", "zh", "zh_Hans", "zh_Hant", "zh-TW", "en", "de", "pt_BR").associateWith(Pixivision::language),
        )
        assertEquals("https://www.pixivision.net/zh-tw/a/42", Pixivision.articleUrl(42, "zh-tw"))
    }
}
