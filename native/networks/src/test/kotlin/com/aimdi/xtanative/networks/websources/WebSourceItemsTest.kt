package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.websources.WebSourceFixtures.obj
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.source
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.text
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.with
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.without
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

class WebSourceItemsTest {
    private val lobsters = source(WebSourceFixtures.links())
    private val hottest = lobsters.feeds.first()
    private val hottestUrl = WebSourcePages.listUrl(hottest)!!
    private val linksPage = WebSourcePages.read(lobsters, hottest, text("lobsters_page.json"), hottestUrl, page = 1)

    @Test
    fun aJsonListStartsAtTheFirstPage() {
        assertEquals("https://lobste.rs/page/1.json".toHttpUrl(), hottestUrl)
    }

    @Test
    fun aJsonListGivesAPostPerElementThatHasAnIdOrLinkAndATitle() {
        assertEquals(listOf("lobsters:abc", "lobsters:def"), linksPage.items.map { it.key })
    }

    @Test
    fun aJsonListReadsEveryFieldDecodingEntitiesAndNumbers() {
        val first = linksPage.items.first()
        assertEquals("Rust & Dart", first.title)
        assertEquals("https://example.org/a", first.link)
        assertEquals(Triple("ann", 12L, 3L), Triple(first.author, first.score, first.comments))
        assertEquals("https://lobste.rs/s/abc", first.discussion)
        assertEquals(listOf("rust", "dart"), first.tags)
        assertEquals(Instant.parse("2026-10-29T13:00:00Z"), first.date)
        assertEquals(4L, linksPage.items[1].score)
        assertNull(linksPage.items[1].link)
    }

    @Test
    fun aJsonListNamesTheNextPageByNumber() {
        assertEquals("https://lobste.rs/page/2.json".toHttpUrl(), linksPage.next)
    }

    @Test
    fun anEmptyPageHasNoNextPageAndABrokenOneNoPosts() {
        assertNull(WebSourcePages.read(lobsters, hottest, "[]", hottestUrl, page = 3).next)
        assertTrue(WebSourcePages.read(lobsters, hottest, "{oops", hottestUrl).items.isEmpty())
    }

    @Test
    fun aLongPageGivesAtMostAHundredPosts() {
        val long = List(300) { """{"short_id": "x$it", "title": "${"t".repeat(80)}", "url": "https://example.org/a"}""" }.joinToString(",", "[", "]")

        val page = WebSourcePages.read(lobsters, hottest, long, hottestUrl, page = 1)

        assertEquals(WebSourcePages.MAX_ITEMS, page.items.size)
        assertEquals("t".repeat(80), page.items.first().title)
        assertEquals("https://lobste.rs/page/2.json".toHttpUrl(), page.next)
    }

    @Test
    fun searchFillsTheQuery() {
        assertEquals(
            "https://lobste.rs/search.json?q=dart%20%26%20flutter&page=1".toHttpUrl(),
            WebSourcePages.listUrl(lobsters.search!!, query = "dart & flutter"),
        )
        assertNull(WebSourcePages.listUrl(lobsters.search!!), "a search without a query has no address")
    }

    private val lemmy = source(WebSourceFixtures.posts())
    private val hot = lemmy.feeds.single()
    private val hotUrl = WebSourcePages.listUrl(hot)!!
    private val postsPage = WebSourcePages.read(lemmy, hot, text("lemmy_page.json"), hotUrl)

    @Test
    fun aCursorListStartsWithAnEmptyCursor() {
        assertEquals("https://lemmy.world/api/v3/post/list?sort=Hot&page_cursor=", hotUrl.toString())
    }

    @Test
    fun templatesBuildLinksAndRelativePicturesAreResolved() {
        val post = postsPage.items.single()
        assertEquals("https://lemmy.world/post/7", post.link)
        assertEquals("https://lemmy.world/pictrs/7.jpg", post.image)
        assertEquals("First post", post.text)
        assertEquals(Instant.ofEpochSecond(1761724800), post.date)
        assertEquals("https://lemmy.world/pictrs/cy.png", post.avatar)
        assertEquals(listOf("https://lemmy.world/pictrs/7.jpg"), post.pictures)
    }

    @Test
    fun aCursorListNamesTheNextPageByItsCursor() {
        assertEquals("https://lemmy.world/api/v3/post/list?sort=Hot&page_cursor=Pa7", postsPage.next.toString())
    }

    @Test
    fun theArticleRuleFindsTheFullText() {
        val article = lemmy.article!!
        assertEquals("https://lemmy.world/api/v3/post?id=7".toHttpUrl(), WebSourcePages.articleUrl(article, postsPage.items.single()))
        assertEquals("<p>Full</p>", WebSourcePages.articleHtml(article, """{"post_view": {"post": {"body": "<p>Full</p>"}}}"""))
        assertNull(WebSourcePages.articleHtml(article, "not json"))
    }

    private val tildes = source(WebSourceFixtures.html())
    private val htmlPage = WebSourcePages.read(tildes, tildes.feeds.single(), text("tildes_page.html"), "https://tildes.net/".toHttpUrl())

    @Test
    fun anHtmlPageGivesAPostPerMatchingElementWithATitle() {
        val post = htmlPage.items.single()
        assertEquals("topic-1", post.id)
        assertEquals("Hello world", post.title)
        assertEquals("https://tildes.net/~comp/1/hello", post.link)
        assertEquals("An excerpt", post.text)
        assertEquals(1205L, post.comments)
        assertEquals(Instant.parse("2026-10-29T10:00:00Z"), post.date)
    }

    @Test
    fun anHtmlPageFollowsTheNextPageLink() {
        assertEquals("https://tildes.net/?after=topic-1".toHttpUrl(), htmlPage.next)
    }

    @Test
    fun anHtmlArticleRuleReadsMarkup() {
        val article = WebSourceArticle(url = "{link}", type = WebSourcePageType.HTML, html = "div.body")
        assertEquals("<p>Text</p>", WebSourcePages.articleHtml(article, """<div class="body"><p>Text</p></div>"""))
    }

    @Test
    fun aGalleryPageGivesPictures() {
        val nasa = source(WebSourceFixtures.gallery())
        val page = WebSourcePages.read(
            nasa,
            nasa.feeds.single(),
            """{"collection": {"items": [
              {"data": [{"nasa_id": "as11", "title": "Earthrise"}], "links": [{"href": "https://images-assets.nasa.gov/a.jpg"}]}
            ]}}""",
            "https://images-api.nasa.gov/search?q=moon".toHttpUrl(),
        )

        val picture = page.items.single()
        assertEquals(listOf("https://images-assets.nasa.gov/a.jpg"), picture.pictures)
        assertEquals("https://images.nasa.gov/details/as11", picture.link)
    }

    @Test
    fun anRssPageIsReadByTheRssParser() {
        val rss = source(
            WebSourceFixtures.links().with("type", JsonPrimitive("rss"))
                .with("feeds", JsonArray(listOf(obj("id" to "rss", "name" to "RSS", "url" to "https://lobste.rs/rss"))))
                .without("search"),
        )
        val page = WebSourcePages.read(
            rss,
            rss.feeds.single(),
            """<rss><channel><item>
              <title>From RSS</title><link>https://lobste.rs/s/x</link><guid>x</guid>
              <pubDate>Wed, 29 Oct 2026 08:00:00 GMT</pubDate></item></channel></rss>""",
            "https://lobste.rs/rss".toHttpUrl(),
        )

        val item = page.items.single()
        assertEquals("From RSS", item.title)
        assertEquals("https://lobste.rs/s/x", item.link)
        assertEquals(Instant.parse("2026-10-29T08:00:00Z"), item.date)
        assertEquals(mapOf("id" to "https://lobste.rs/rss::x", "title" to "From RSS", "link" to "https://lobste.rs/s/x"), item.values)
        assertNull(page.next)
    }

    @Test
    fun datesInEachFormSitesWriteThem() {
        assertEquals(Instant.parse("2026-10-29T10:00:00Z"), WebSourcePages.date("2026-10-29T10:00:00Z"))
        assertEquals(Instant.parse("2026-10-29T10:00:00Z"), WebSourcePages.date("Wed, 29 Oct 2026 10:00:00 GMT"))
        assertEquals(Instant.parse("2025-10-29T10:00:00Z"), WebSourcePages.date("1761732000"))
        assertEquals(Instant.parse("2025-10-29T10:00:00Z"), WebSourcePages.date("1761732000000"))
        assertNull(WebSourcePages.date("yesterday"))
        assertNull(WebSourcePages.date("9".repeat(30)), "digits too many for a date")
    }

    @Test
    fun countsAsSitesWriteThem() {
        assertEquals(12L, WebSourcePages.count("12"))
        assertEquals(13L, WebSourcePages.count("12.5"))
        assertEquals(1205L, WebSourcePages.count("1,205 points"))
        assertNull(WebSourcePages.count("NaN"), "XTA threw on it")
        assertNull(WebSourcePages.count("none"))
    }

    @Test
    fun markupInATextIsReducedToTextWithSpacesBetweenParagraphsWhereXtaRanThemTogether() {
        val page = WebSourcePages.read(
            lobsters,
            hottest,
            """[{"short_id": "p", "title": "<p>One</p><p>Two</p>", "url": "https://example.org/p"}]""",
            hottestUrl,
        )
        assertEquals("One Two", page.items.single().title)
    }

    @Test
    fun anAddressThatIsNotTheWebIsLeftOutAndANextPageThatIsNoneEndsTheList() {
        val page = WebSourcePages.read(
            lemmy,
            hot,
            """{"posts": [{"post": {"id": 1, "name": "x", "thumbnail_url": "javascript:alert(1)"}}], "next_page": ""}""",
            hotUrl,
        )
        assertNull(page.items.single().image)
        assertNull(page.next)
    }
}
