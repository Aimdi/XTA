package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivNovelTest {
    private val list = Fixtures.json("novels.json")

    private fun api(filters: PixivFilters = PixivFilters(), body: (Asked) -> String = { novelsJson(novelJson(1), novelJson(2, xRestrict = 1), novelJson(3, aiType = 2)) }): Pair<PixivNovelApi, FakePixivHttp> {
        val http = FakePixivHttp { answer(body(it)) }
        return PixivNovelApi(pixiv(http, filters = filters)) to http
    }

    private fun ids(page: Page<PixivNovel>) = page.items.map { it.id }

    @Test
    fun aNovelReadsEveryFieldAListSends() {
        val novel = PixivNovel.list(list).first()
        assertEquals(5L to "Novel 5", novel.id to novel.title)
        assertEquals("First lineSee part two", novel.caption)
        assertTrue("<a href" in novel.captionHtml)
        assertTrue(novel.coverUrl.orEmpty().endsWith("176x352/novel-cover-master/5.jpg"))
        assertEquals(listOf<Any?>(42L, "Mika", "mika", true), listOf(novel.user.id, novel.user.name, novel.user.account, novel.user.isFollowed))
        assertEquals(listOf("original", "秋"), novel.tags.map { it.displayName })
        assertEquals(PixivSeriesRef(77, "Seasons"), novel.series)
        assertEquals(15234L to 3, novel.textLength to novel.pageCount)
        assertEquals(Instant.parse("2026-09-01T03:30:00Z"), novel.createdAt)
        assertEquals(listOf(321L, 4567L, 8L), listOf(novel.totalBookmarks, novel.totalViews, novel.totalComments))
        assertEquals(listOf(true, false, false, true), listOf(novel.isBookmarked, novel.isR18, novel.isAi, novel.isOriginal))
        assertEquals("https://www.pixiv.net/novel/show.php?id=5", novel.url)
    }

    @Test
    fun ratingsAndAiComeFromXRestrictAndNovelAiType() {
        val all = PixivNovel.list(list, includeR18 = true).associateBy { it.id }
        assertEquals(Triple(true, false, false), all.getValue(6).let { Triple(it.isR18, it.isR18G, it.isAi) })
        assertEquals(Triple(true, true, true), all.getValue(7).let { Triple(it.isR18, it.isR18G, it.isAi) })
    }

    @Test
    fun aListLeavesOutR18AndAiUnlessAskedAndSkipsWhatDoesNotParse() {
        fun ids(r18: Boolean, ai: Boolean) = PixivNovel.list(list, r18, ai).map { it.id }
        assertEquals(listOf(5L, 8L), ids(r18 = false, ai = true))
        assertEquals(listOf(5L, 6L, 7L, 8L), ids(r18 = true, ai = true))
        assertEquals(listOf(5L, 6L), ids(r18 = true, ai = false))
        assertEquals(listOf(5L), ids(r18 = false, ai = false))
        assertTrue(PixivNovel.list(J.parse("""{"novels": "none"}""")).isEmpty())
    }

    @Test
    fun aMissingOrReshapedNovelReadsAsEmptyFieldsNeverAThrow() {
        val bare = PixivNovel.parse(J.parse("""{"id": "12"}"""))!!
        assertEquals(listOf<Any?>(12L, "", "", null, 0, null), listOf(bare.id, bare.title, bare.caption, bare.coverUrl, bare.tags.size, bare.series))
        assertEquals(listOf<Any?>(0L, 0L, 1, null), listOf(bare.user.id, bare.textLength, bare.pageCount, bare.createdAt))

        val reshaped = PixivNovel.parse(
            J.parse("""{"id": 13, "title": 7, "tags": "not a list", "user": ["not", "a", "map"], "series": {"id": 0}, "image_urls": {"medium": "  "}, "x_restrict": "r18", "novel_ai_type": null, "create_date": "yesterday"}"""),
        )!!
        assertEquals(listOf<Any?>("", 0, null, null, false, false, null), listOf(reshaped.title, reshaped.tags.size, reshaped.series, reshaped.coverUrl, reshaped.isR18, reshaped.isAi, reshaped.createdAt))
    }

    @Test
    fun aNovelPixivWithholdsOrOneWithoutAnIdIsNoNovel() {
        for (json in listOf("""{"id": 3, "visible": false}""", """{"title": "no id"}""", """{"id": -1}""", "\"a string\"", "null")) {
            assertNull(PixivNovel.parse(J.parse(json)), json)
        }
    }

    @Test
    fun aSeriesPageReadsTheSeriesItsEndsAndNumbersItsChapters() {
        val page = PixivNovelSeriesPage.parse(Fixtures.json("novel_series.json"))
        val series = page.series!!
        assertEquals(listOf<Any>(77L, "Seasons", "Four seasons", "Four <b>seasons</b>"), listOf(series.id, series.title, series.caption, series.captionHtml))
        assertEquals(Triple(true, 4L, 54321L), Triple(series.isConcluded, series.contentCount, series.totalCharacterCount))
        assertEquals(Triple(true, true, "Mika"), Triple(series.watchlistAdded, series.isOriginal, series.user.name))
        assertEquals("https://www.pixiv.net/novel/series/77", series.url)
        assertEquals(10L to 13L, page.first?.id to page.latest?.id)
        assertEquals(listOf(1 to 10L, 2 to 11L, 3 to 12L), page.chapters.items.map { it.order to it.novel.id })
        assertEquals(3 to "https://app-api.pixiv.net/v2/novel/series?series_id=77&last_order=3", page.listed to page.chapters.next)
    }

    @Test
    fun aChapterTheFiltersHideKeepsTheNextOnesNumberAndAHiddenNewestIsNotOffered() {
        val page = PixivNovelSeriesPage.parse(Fixtures.json("novel_series.json")) { !it.isR18 }
        assertEquals(listOf(1 to 10L, 3 to 12L), page.chapters.items.map { it.order to it.novel.id })
        assertEquals(3, page.listed)
        assertEquals(10L to null, page.first?.id to page.latest?.id)
    }

    @Test
    fun aReshapedSeriesPageHasNoSeriesAndNoChapters() {
        val page = PixivNovelSeriesPage.parse(J.parse("""{"novel_series_detail": "gone", "novels": {}, "next_url": ""}"""))
        assertEquals(listOf<Any?>(null, null, null, 0, 0, null), listOf(page.series, page.first, page.latest, page.chapters.items.size, page.listed, page.chapters.next))
        assertNull(PixivNovelSeries.parse(J.parse("""{"id": "x"}""")))
    }

    @Test
    fun recommendedAsksForRankingNovelsThenFollowsNextUrl() = runBlocking {
        val (api, http) = api { novelsJson(novelJson(1), novelJson(2, xRestrict = 1), novelJson(3, aiType = 2), next = "https://app-api.pixiv.net/v1/novel/recommended?p=2") }
        val page = api.recommended()
        api.recommended(page.next)

        assertEquals("/v1/novel/recommended", http.asked.first().url.encodedPath)
        assertEquals(mapOf("include_privacy_policy" to "true", "filter" to "for_android", "include_ranking_novels" to "true"), http.asked.first().query)
        assertEquals(listOf(1L, 3L), ids(page), "R-18 novels stay out while Show R-18 is off")
        assertEquals("https://app-api.pixiv.net/v1/novel/recommended?p=2", http.asked.last().url.toString())
    }

    @Test
    fun followingSendsPublicOrPrivateAndTheReadersChoicesApply() = runBlocking {
        val (api, http) = api()
        api.following()
        api.following(restrict = "private")
        assertEquals(listOf("public", "private"), http.asked.map { it.query["restrict"] })
        assertTrue(http.asked.all { it.url.encodedPath == "/v1/novel/follow" })

        assertEquals(listOf(1L, 2L, 3L), ids(api(PixivFilters(showR18 = true)).first.following()))
        assertEquals(listOf(1L), ids(api(PixivFilters(hideAi = true)).first.following()))
    }

    @Test
    fun rankingsSendTheBoardTheArchiveDateAndTheAndroidFilter() = runBlocking {
        val (api, http) = api()
        api.ranking("day_male", date = LocalDate.of(2026, 8, 1))
        api.ranking("week")
        assertEquals("/v1/novel/ranking", http.asked.first().url.encodedPath)
        assertEquals(mapOf("mode" to "day_male", "date" to "2026-08-01", "filter" to "for_android"), http.asked.first().query)
        assertEquals(mapOf("mode" to "week", "filter" to "for_android"), http.asked.last().query)
    }

    @Test
    fun anAiBoardKeepsItsAiNovelsUnderHideAi() = runBlocking {
        val (api, _) = api(PixivFilters(hideAi = true))
        assertEquals(listOf(1L, 3L), ids(api.ranking("week_ai")))
        assertEquals(listOf(1L), ids(api.ranking("day")))
    }

    @Test
    fun theReadersOwnBookmarksKeepEverythingAndSomeoneElsesFollowTheChoices() = runBlocking {
        val (api, http) = api(PixivFilters(hideAi = true))
        val own = api.ownBookmarks(restrict = "private")
        val theirs = api.bookmarks(userId = 5)

        assertEquals("/v1/user/bookmarks/novel", http.asked.first().url.encodedPath)
        assertEquals(mapOf("user_id" to "77", "restrict" to "private"), http.asked.first().query)
        assertEquals(mapOf("user_id" to "5", "restrict" to "public"), http.asked.last().query)
        assertEquals(listOf(1L, 2L, 3L), ids(own))
        assertEquals(listOf(1L), ids(theirs))
    }

    @Test
    fun theWatchlistListsWatchedSeriesSkippingRowsThatDoNotParse() = runBlocking {
        val (api, http) = api { Fixtures.json("discovery.json")["watchlist"].raw.toString() }
        val page = api.watchlist()
        assertEquals("/v1/watchlist/novel", http.asked.single().url.encodedPath)
        assertEquals(listOf(266067L, 77L), page.items.map { it.id })
    }

    @Test
    fun aSeriesIsAskedByIdThenByNextUrlHidingWhatTheChoicesHide() = runBlocking {
        val (api, http) = api {
            """{"novel_series_detail": {"id": 8, "title": "Seasons", "user": {}}, "novel_series_first_novel": ${novelJson(1)},
               "novel_series_latest_novel": ${novelJson(2, xRestrict = 1)}, "novels": [${novelJson(1)}, ${novelJson(2, xRestrict = 1)}],
               "next_url": "https://app-api.pixiv.net/v2/novel/series?series_id=8&last_order=2"}"""
        }
        val page = api.series(8)
        api.series(8, page.chapters.next)

        assertEquals("/v2/novel/series" to mapOf("series_id" to "8"), http.asked.first().url.encodedPath to http.asked.first().query)
        assertEquals("2", http.asked.last().query["last_order"])
        assertEquals(listOf(1L), page.chapters.items.map { it.novel.id })
        assertEquals(Triple(2, 1L, null), Triple(page.listed, page.first?.id, page.latest?.id))
    }

    @Test
    fun searchSendsTheWordPlaceOrderAndDatesThenFollowsNextUrl() = runBlocking {
        val (api, http) = api { novelsJson(novelJson(1), novelJson(2, xRestrict = 1), novelJson(3, aiType = 2), next = "https://app-api.pixiv.net/v1/search/novel?offset=30") }
        val filter = PixivSearchFilter(target = PixivSearchTarget.TEXT, sort = PixivSearchSort.OLDEST).withDates(PixivDatePreset.WEEK).copy(usersIri = 1000)
        val query = filter.query("letters", LocalDate.of(2026, 10, 10), PixivSearchKind.NOVELS)
        val page = api.search(query)
        api.search(query, page.next)

        assertEquals("/v1/search/novel", http.asked.first().url.encodedPath)
        assertEquals(
            mapOf(
                "word" to "letters 1000users入り", "search_target" to "text", "sort" to "date_asc", "start_date" to "2026-10-03",
                "end_date" to "2026-10-10", "merge_plain_keyword_results" to "true", "filter" to "for_android",
            ),
            http.asked.first().query,
        )
        assertEquals("https://app-api.pixiv.net/v1/search/novel?offset=30", http.asked.last().url.toString())
        assertEquals(listOf(1L, 3L), ids(page))
    }

    @Test
    fun theSearchsOwnAiChoiceDecidesNotTheFeeds() = runBlocking {
        val (api, _) = api(PixivFilters(hideAi = true))
        assertEquals(listOf(1L, 3L), ids(api.search(mapOf("word" to "a"), includeAi = true)))
        assertEquals(listOf(1L), ids(api.search(mapOf("word" to "a"), includeAi = false)))
    }

    @Test
    fun trendingNovelTagsKeepTheirPicturesUnlessTheChoicesHideThem() = runBlocking {
        val (api, http) = api { Fixtures.json("discovery.json")["trend_tags"].raw.toString() }
        val tags = api.trendingTags()
        assertEquals("/v1/trending-tags/novel" to mapOf("filter" to "for_android"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(listOf("原神" to 9L, "異世界" to null, "noface" to null), tags.map { it.name to it.illust?.id })
    }

    @Test
    fun aCreatorsNovelsAskByUserIdAndTheReadersOwnKeepEverything() = runBlocking {
        val (api, http) = api(PixivFilters(hideAi = true))
        val theirs = api.userNovels(5)
        val own = api.userNovels(77)
        assertEquals("/v1/user/novels" to mapOf("user_id" to "5", "filter" to "for_android"), http.asked.first().url.encodedPath to http.asked.first().query)
        assertEquals(listOf(1L), ids(theirs))
        assertEquals(listOf(1L, 2L, 3L), ids(own))
    }

    @Test
    fun aNovelsDetailIsReadByIdAndAWithheldOneIsNotFound() = runBlocking {
        val (api, http) = api { """{"novel": ${novelJson(31)}}""" }
        val novel = api.detail(31)
        assertEquals("/v2/novel/detail" to mapOf("novel_id" to "31"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(Triple(31L, "Novel 31", "A"), Triple(novel.id, novel.title, novel.user.name))

        for (body in listOf("""{"novel": {"id": 31, "visible": false}}""", """{"novel": "gone"}""", "{}")) {
            val error = assertFailsWith<NetworkRequestException> { api { body }.first.detail(31) }
            assertEquals(PixivFailure.NOT_FOUND, PixivFailure.of(error), body)
        }
    }

    @Test
    fun aNovelsTextIsReadFromItsWebviewPage() = runBlocking {
        val (api, http) = api { Fixtures.text("novel_webview.html") }
        val content = api.content(41)
        val read = http.asked.single()
        assertEquals("/webview/v2/novel" to mapOf("id" to "41"), read.url.encodedPath to read.query)
        assertEquals("text/html" to "Bearer access-1", read.headers["Accept"] to read.headers["Authorization"])
        assertEquals(40L, content.previous?.id)
        assertEquals("https://i.pximg.net/novel/9_1200.jpg", content.uploads["9"]?.url)
    }

    @Test
    fun aPageWithoutTheNovelObjectIsABadResponse() = runBlocking {
        val error = assertFailsWith<NetworkRequestException> { api { "<html>maintenance</html>" }.first.content(41) }
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(error))
    }
}
