package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivDiscoveryTest {
    private val discovery = Fixtures.json("discovery.json")

    private fun api(filters: PixivFilters = PixivFilters(), locale: String = "en", body: (Asked) -> String): Pair<PixivDiscoveryApi, FakePixivHttp> {
        val http = FakePixivHttp { answer(body(it)) }
        return PixivDiscoveryApi(pixiv(http, filters = filters, locale = locale)) to http
    }

    @Test
    fun rankingsSendTheModeTheArchiveDayWhenPickedAndTheAndroidFilter() = runBlocking {
        val (api, http) = api { illustsJson() }
        api.ranking("week_original", date = LocalDate.of(2026, 8, 1))
        api.ranking("day")
        assertEquals("/v1/illust/ranking", http.asked.first().url.encodedPath)
        assertEquals(mapOf("mode" to "week_original", "date" to "2026-08-01", "filter" to "for_android"), http.asked.first().query)
        assertFalse("date" in http.asked.last().query)
    }

    @Test
    fun aiBoardsKeepTheirAiWorksUnderHideAiAndTheOthersDropThem() = runBlocking {
        val (api, _) = api(PixivFilters(hideAi = true)) { illustsJson(workJson(1, ai = true), workJson(2)) }
        assertEquals(listOf(1L, 2L), api.ranking("day_ai").items.map { it.id })
        assertEquals(listOf(2L), api.ranking("day").items.map { it.id })
    }

    @Test
    fun r18WorksStayOutOfEveryBoardWhileShowR18IsOff() = runBlocking {
        val (api, _) = api { illustsJson(workJson(1, r18 = true), workJson(2)) }
        assertEquals(listOf(2L), api.ranking("day_r18").items.map { it.id })
    }

    @Test
    fun recommendedMangaAsksTheMangaEndpointWithRankingLabels() = runBlocking {
        val (api, http) = api { illustsJson(workJson(5), next = "https://app-api.pixiv.net/next") }
        val page = api.mangaRecommended()
        assertEquals("/v1/manga/recommended" to mapOf("include_ranking_label" to "true", "filter" to "for_android"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(listOf(5L), page.items.map { it.id })
        assertEquals("https://app-api.pixiv.net/next", page.next)
    }

    @Test
    fun theFollowingFeedAsksForTheChosenFollows() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson()) }
        val client = pixiv(http)
        for (restrict in listOf("all", "public", "private")) client.following(restrict)
        assertEquals(listOf("all", "public", "private"), http.asked.map { it.query.getValue("restrict") })
        assertTrue(http.asked.all { it.url.encodedPath == "/v2/illust/follow" })
    }

    @Test
    fun theShowcaseIsReadWithoutAnyTokenEvenSignedOut() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson(workJson(9), next = "https://app-api.pixiv.net/v1/walkthrough/illusts?offset=30")) }
        val api = PixivDiscoveryApi(pixiv(http, MemoryPixivCredentials()))
        val first = api.walkthrough()
        api.walkthrough(first.next)

        assertEquals(listOf("/v1/walkthrough/illusts", "/v1/walkthrough/illusts"), http.asked.map { it.url.encodedPath })
        assertEquals(listOf(null, null), http.asked.map { it.headers["Authorization"] })
        assertEquals("30", http.asked.last().query["offset"])
        assertEquals(listOf(9L), first.items.map { it.id })
    }

    @Test
    fun recommendedCreatorsKeepTheirPreviewsUnderTheReadersChoices() = runBlocking {
        val (api, http) = api { Fixtures.text("user_previews.json") }
        val page = api.recommendedUsers()
        assertEquals(mapOf("filter" to "for_android"), http.asked.single().query)
        assertEquals(listOf("One", "Two"), page.items.map { it.user.name })
        assertEquals(listOf(10L, 12L), page.items.first().illusts.map { it.id })
        assertTrue(page.next.orEmpty().endsWith("offset=30"))
    }

    @Test
    fun recommendedWorksMixInTheRankingWhenThinAndPage() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson(workJson(11), workJson(12), next = "https://app-api.pixiv.net/v1/illust/recommended?offset=30")) }
        val page = pixiv(http).recommended()
        assertEquals("true", http.asked.single().query["include_ranking_illusts"])
        assertEquals(listOf(11L, 12L), page.items.map { it.id })
        assertTrue(page.next.orEmpty().contains("recommended"))
    }

    @Test
    fun pixivisionArticlesComeFromEveryCategoryNewestFirst() = runBlocking {
        val (api, http) = api { discovery["spotlight"].raw.toString() }
        val page = api.spotlightArticles()
        assertEquals("/v1/spotlight/articles" to mapOf("filter" to "for_android", "category" to "all"), http.asked.single().url.encodedPath to http.asked.single().query)
        val article = page.items.single()
        assertEquals(Triple(9876L, "Cats in Autumn", "https://i.pximg.net/c/w1200/9876.jpg"), Triple(article.id, article.displayTitle, article.thumbnailUrl))
        assertEquals("https://www.pixivision.net/en/a/9876", article.articleUrl)
        assertEquals(Instant.parse("2024-10-01T09:00:00Z"), article.publishedAt)
        assertTrue(page.next.orEmpty().contains("offset=10"))
    }

    @Test
    fun aSeriesPageCarriesItsHeaderAndItsWorks() = runBlocking {
        val (api, http) = api { Fixtures.text("illust_series.json") }
        val page = api.illustSeries(266067)
        assertEquals("/v1/illust/series" to mapOf("illust_series_id" to "266067", "filter" to "for_android"), http.asked.single().url.encodedPath to http.asked.single().query)
        val series = page.series!!
        assertEquals(listOf<Any?>("X'max Present", "Two parts", 2L, true, "https://i.pximg.net/cover.jpg"), listOf(series.title, series.caption, series.workCount, series.watchlistAdded, series.coverUrl))
        assertEquals(4004637L, series.user.id)
        assertEquals("https://www.pixiv.net/user/4004637/series/266067", series.url)
        assertEquals(listOf(11L), page.works.items.map { it.id }, "the R-18 part stays out while Show R-18 is off")
        assertNull(page.works.next)
    }

    @Test
    fun aReshapedSeriesHeaderReadsAsNoneAndMissingFieldsAsDefaults() {
        assertNull(PixivIllustSeries.parse(J.parse("""{"title": "no id"}""")))
        assertNull(PixivIllustSeries.parse(J.parse("\"nonsense\"")))
        val bare = PixivIllustSeries.parse(J.parse("""{"id": 3}"""))!!
        assertEquals(listOf<Any?>("", null, 0L, false, 0L), listOf(bare.title, bare.coverUrl, bare.workCount, bare.watchlistAdded, bare.user.id))
    }

    @Test
    fun theSeriesContextGivesThePlaceAndTheNeighboursTheReaderMaySee() = runBlocking {
        val (api, http) = api { discovery["context"].raw.toString() }
        val place = api.seriesContext(12)!!
        assertEquals("/v1/illust-series/illust" to mapOf("illust_id" to "12"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(listOf<Any?>(2, 3L, 11L, null), listOf(place.order, place.total, place.previous?.id, place.next))
    }

    @Test
    fun aContextWithoutAPlaceIsNoContext() {
        assertNull(PixivSeriesContext.parse(J.parse("""{"illust_series_context": null}""")))
        assertNull(PixivSeriesContext.parse(J.parse("""{"illust_series_context": {"content_order": 0}}""")))
        val place = PixivSeriesContext.parse(J.parse("""{"illust_series_context": {"content_order": "4", "prev": "x"}}"""))!!
        assertEquals(listOf<Any?>(4, 0L, null, null), listOf(place.order, place.total, place.previous, place.next))
    }

    @Test
    fun theMangaWatchlistListsEachWatchedSeriesAndSkipsRowsThatDoNotParse() = runBlocking {
        val (api, http) = api { discovery["watchlist"].raw.toString() }
        val page = api.mangaWatchlist()
        assertEquals("/v1/watchlist/manga", http.asked.single().url.encodedPath)
        assertEquals(listOf(266067L, 77L), page.items.map { it.id })
        val watched = page.items.first()
        assertEquals(listOf<Any?>("X'max Present", "幻羽", 125547965L, 2L), listOf(watched.title, watched.userName, watched.latestContentId, watched.publishedCount))
        assertEquals(Instant.parse("2024-12-25T16:27:48Z"), watched.lastPublishedAt)
        assertTrue(watched.coverUrl.orEmpty().contains("125508098"))
        val masked = page.items.last()
        assertEquals(listOf<Any?>("Restricted", null, null, 0L), listOf(masked.maskText, masked.latestContentId, masked.coverUrl, masked.publishedCount))
        assertTrue(PixivWatchlistSeries.list(J.parse("""{"series": "nope"}""")).isEmpty())
    }

    @Test
    fun aPixivisionArticleIsReadAsADesktopBrowserInTheReadersLanguageWithoutTheToken() = runBlocking {
        val (api, http) = api(locale = "zh_Hant") { Fixtures.text("pixivision/article_en.html") }
        val article = api.pixivisionArticle(9876)
        val read = http.asked.single()
        assertEquals("https://www.pixivision.net/zh-tw/a/9876", read.url.toString())
        assertEquals(Pixivision.USER_AGENT, read.headers["User-Agent"])
        assertEquals("https://www.pixivision.net/zh-tw/", read.headers["Referer"])
        assertEquals("zh-TW" to "text/html", read.headers["Accept-Language"] to read.headers["Accept"])
        assertFalse("Authorization" in read.headers)
        assertEquals(2, article.works.size)
    }

    @Test
    fun aMissingArticleIsNotFoundAndAnythingElsePixivisionRefusesABadAnswer() = runBlocking {
        suspend fun failure(code: Int, body: String) = PixivFailure.of(
            assertFailsWith<NetworkRequestException> {
                PixivDiscoveryApi(pixiv(FakePixivHttp { answer(body, code) })).pixivisionArticle(1)
            },
        )
        assertEquals(PixivFailure.NOT_FOUND, failure(404, ""))
        assertEquals(PixivFailure.BAD_RESPONSE, failure(200, "<html><body></body></html>"))
        assertEquals(PixivFailure.BAD_RESPONSE, failure(403, ""), "Pixivision holds no Pixiv token to blame")
    }
}
