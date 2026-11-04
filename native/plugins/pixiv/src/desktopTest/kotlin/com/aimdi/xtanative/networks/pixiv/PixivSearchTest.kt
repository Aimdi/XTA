package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PixivSearchTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun dates(filter: PixivSearchFilter, on: LocalDate): Pair<String?, String?> =
        filter.query("miku", on).let { it["start_date"] to it["end_date"] }

    private fun preset(preset: PixivDatePreset) = PixivSearchFilter().withDates(preset)

    @Test
    fun aPastDayAndWeekStepBackAcrossAMonthEdge() {
        val march = LocalDate.of(2026, 3, 1)
        assertEquals("2026-02-28" to "2026-03-01", dates(preset(PixivDatePreset.DAY), march))
        assertEquals("2026-02-22" to "2026-03-01", dates(preset(PixivDatePreset.WEEK), march))
    }

    @Test
    fun aMonthBackFromThe31stLandsOnTheShorterMonthsLastDay() {
        assertEquals("2026-02-28" to "2026-03-31", dates(preset(PixivDatePreset.MONTH), LocalDate.of(2026, 3, 31)))
        assertEquals("2024-02-29" to "2024-03-31", dates(preset(PixivDatePreset.MONTH), LocalDate.of(2024, 3, 31)))
    }

    @Test
    fun monthsHalfYearsAndYearsCrossTheYearEdge() {
        val january = LocalDate.of(2026, 1, 5)
        assertEquals("2025-12-05" to "2026-01-05", dates(preset(PixivDatePreset.MONTH), january))
        assertEquals("2025-07-05" to "2026-01-05", dates(preset(PixivDatePreset.HALF_YEAR), january))
        assertEquals("2023-02-28" to "2024-02-29", dates(preset(PixivDatePreset.YEAR), LocalDate.of(2024, 2, 29)))
        assertEquals("2026-02-28" to "2026-08-31", dates(preset(PixivDatePreset.HALF_YEAR), LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun anyTimeSendsNoDatesAndACustomRangeSendsItsOwn() {
        assertEquals(null to null, dates(PixivSearchFilter(), today))
        val custom = PixivSearchFilter().withDates(PixivDatePreset.CUSTOM, PixivDateRange(LocalDate.of(2008, 1, 2), LocalDate.of(2009, 11, 30)))
        assertEquals("2008-01-02" to "2009-11-30", dates(custom, today))
        assertEquals(PixivDatePreset.ANY, PixivSearchFilter().withDates(PixivDatePreset.CUSTOM).datePreset, "custom without dates means any time")
    }

    @Test
    fun theQueryCarriesThePlaceTheOrderAndPixivsFixedParameters() {
        val query = PixivSearchFilter(target = PixivSearchTarget.EXACT_TAGS, sort = PixivSearchSort.OLDEST).query(" 初音ミク ", today)
        assertEquals(
            mapOf(
                "word" to "初音ミク", "search_target" to "exact_match_for_tags", "sort" to "date_asc", "search_ai_type" to "0",
                "merge_plain_keyword_results" to "true", "filter" to "for_android",
            ),
            query,
        )
    }

    @Test
    fun thePopularityTagGoesOnTheSentWordAlone() {
        val filter = PixivSearchFilter(usersIri = 1000)
        assertEquals("miku rin 1000users入り", filter.query("miku rin", today)["word"])
        assertEquals("miku", PixivSearchFilter.searchWord("miku", null))
        assertTrue(PixivSearchFilter.USERS_IRI_THRESHOLDS.contains(1000))
    }

    @Test
    fun premiumBookmarkBracketsBecomeAMinimumAndAMaximum() {
        val bounded = PixivSearchFilter(bookmarks = PixivBookmarkRange(1000, 4999)).query("miku", today)
        assertEquals("1000" to "4999", bounded["bookmark_num_min"] to bounded["bookmark_num_max"])
        val open = PixivSearchFilter(bookmarks = PixivSearchFilter.BOOKMARK_RANGES.first()).query("miku", today)
        assertEquals("10000" to null, open["bookmark_num_min"] to open["bookmark_num_max"])
    }

    @Test
    fun theAiSwitchSendsOneToHideAndZeroToInclude() {
        assertEquals("1", PixivSearchFilter(hideAi = true).query("x", today)["search_ai_type"])
        assertEquals("0", PixivSearchFilter().query("x", today)["search_ai_type"])
    }

    @Test
    fun thePopularPreviewAsksForTranslatedTagsToo() {
        assertEquals(
            mapOf(
                "word" to "miku", "search_target" to "title_and_caption", "merge_plain_keyword_results" to "true",
                "include_translated_tag_results" to "true", "filter" to "for_android",
            ),
            PixivSearchFilter.popularPreviewQuery(PixivSearchTarget.TITLE_CAPTION, " miku "),
        )
    }

    @Test
    fun onlyTheAudienceOrdersNeedPremiumForWorks() {
        assertEquals(listOf(PixivSearchSort.NEWEST, PixivSearchSort.OLDEST, PixivSearchSort.POPULAR), PixivSearchKind.WORKS.offeredSorts(isPremium = false))
        assertEquals(PixivSearchSort.entries, PixivSearchKind.WORKS.offeredSorts(isPremium = true))
    }

    @Test
    fun anAccountWithoutPremiumDropsAPremiumOrderAndTheBookmarkBracket() {
        val filter = PixivSearchFilter(sort = PixivSearchSort.POPULAR_FEMALE, hideAi = true, bookmarks = PixivBookmarkRange(50, 99), usersIri = 500)
        val free = filter.forAccount(isPremium = false)
        assertEquals(listOf<Any?>(PixivSearchSort.NEWEST, null, 500, true), listOf(free.sort, free.bookmarks, free.usersIri, free.hideAi))
        assertEquals(filter, filter.forAccount(isPremium = true))
        assertEquals(PixivSearchSort.POPULAR, PixivSearchFilter(sort = PixivSearchSort.POPULAR).forAccount(isPremium = false).sort)
    }

    @Test
    fun novelsOfferTheirOwnPlacesAndPopularOnlyToPremium() {
        assertEquals(
            listOf(PixivSearchTarget.PARTIAL_TAGS, PixivSearchTarget.EXACT_TAGS, PixivSearchTarget.TEXT, PixivSearchTarget.KEYWORD),
            PixivSearchKind.NOVELS.targets,
        )
        assertEquals(listOf(PixivSearchSort.NEWEST, PixivSearchSort.OLDEST), PixivSearchKind.NOVELS.offeredSorts(isPremium = false))
        assertEquals(listOf(PixivSearchSort.NEWEST, PixivSearchSort.OLDEST, PixivSearchSort.POPULAR), PixivSearchKind.NOVELS.offeredSorts(isPremium = true))
    }

    @Test
    fun theNovelQueryTakesNoAiOrBookmarkParameters() {
        val filter = PixivSearchFilter(target = PixivSearchTarget.KEYWORD, sort = PixivSearchSort.POPULAR, hideAi = true, bookmarks = PixivBookmarkRange(50, 99))
            .withDates(PixivDatePreset.DAY)
        assertEquals(
            mapOf(
                "word" to "rain", "search_target" to "keyword", "sort" to "popular_desc", "start_date" to "2026-10-09", "end_date" to "2026-10-10",
                "merge_plain_keyword_results" to "true", "filter" to "for_android",
            ),
            filter.query(" rain ", today, PixivSearchKind.NOVELS),
        )
    }

    @Test
    fun aFilterCarriedOverToNovelsKeepsOnlyWhatNovelsTake() {
        val carried = PixivSearchFilter(
            target = PixivSearchTarget.TITLE_CAPTION,
            sort = PixivSearchSort.POPULAR_MALE,
            ugoira = PixivUgoiraFilter.ONLY,
            hideAi = true,
            bookmarks = PixivBookmarkRange(50, 99),
            usersIri = 500,
        )
        val premium = carried.forAccount(isPremium = true, kind = PixivSearchKind.NOVELS)
        assertEquals(
            listOf<Any?>(PixivSearchTarget.PARTIAL_TAGS, PixivSearchSort.NEWEST, PixivUgoiraFilter.ALL, null, 500, true),
            listOf(premium.target, premium.sort, premium.ugoira, premium.bookmarks, premium.usersIri, premium.hideAi),
        )
        val popular = PixivSearchFilter(sort = PixivSearchSort.POPULAR, target = PixivSearchTarget.TEXT)
        assertEquals(PixivSearchSort.NEWEST, popular.forAccount(isPremium = false, kind = PixivSearchKind.NOVELS).sort)
        assertEquals(popular, popular.forAccount(isPremium = true, kind = PixivSearchKind.NOVELS))
        assertEquals(PixivSearchSort.OLDEST, PixivSearchFilter(sort = PixivSearchSort.OLDEST).forAccount(isPremium = false, kind = PixivSearchKind.NOVELS).sort)
    }

    @Test
    fun theUgoiraChoiceKeepsAllOnlyUgoiraOrNone() {
        val works = PixivIllust.list(J.parse(illustsJson(workJson(1), workJson(2, type = "ugoira"), workJson(3))))
        assertEquals(listOf(1L, 2L, 3L), PixivUgoiraFilter.ALL.applyTo(works).map { it.id })
        assertEquals(listOf(2L), PixivUgoiraFilter.ONLY.applyTo(works).map { it.id })
        assertEquals(listOf(1L, 3L), PixivUgoiraFilter.NONE.applyTo(works).map { it.id })
    }

    @Test
    fun aWorkSearchSendsTheFilterQueryAndFollowsNextUrl() = runBlocking {
        val http = FakePixivHttp {
            answer(illustsJson(workJson(1), workJson(2, ai = true), next = "https://app-api.pixiv.net/v1/search/illust?word=miku&offset=30"))
        }
        val api = PixivSearchApi(pixiv(http, filters = PixivFilters(hideAi = true)))
        val query = PixivSearchFilter(target = PixivSearchTarget.EXACT_TAGS, usersIri = 500).query("miku", today)
        val page = api.illusts(query, includeAi = true)
        api.illusts(query, page.next)

        assertEquals("/v1/search/illust", http.asked.first().url.encodedPath)
        assertEquals(
            mapOf(
                "word" to "miku 500users入り", "search_target" to "exact_match_for_tags", "sort" to "date_desc", "search_ai_type" to "0",
                "merge_plain_keyword_results" to "true", "filter" to "for_android",
            ),
            http.asked.first().query,
        )
        assertEquals(listOf(1L, 2L), page.items.map { it.id }, "the search said to include AI works")
        assertEquals("30", http.asked.last().query["offset"])
    }

    @Test
    fun withoutAnAiChoiceTheFeedsHideAiApplies() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson(workJson(1), workJson(2, ai = true))) }
        val page = PixivSearchApi(pixiv(http, filters = PixivFilters(hideAi = true))).illusts(mapOf("word" to "miku"))
        assertEquals(listOf(1L), page.items.map { it.id })
    }

    @Test
    fun aCreatorSearchSendsTheWordAndReadsEachCreatorWithTheirPreviews() = runBlocking {
        val http = FakePixivHttp { answer(Fixtures.text("user_previews.json")) }
        val page = PixivSearchApi(pixiv(http)).users(" kagamine ")
        assertEquals("/v1/search/user" to mapOf("word" to "kagamine", "filter" to "for_android"), http.asked.single().url.encodedPath to http.asked.single().query)
        assertEquals(listOf(1L, 2L), page.items.map { it.user.id })
        assertEquals(listOf(10L, 12L), page.items.first().illusts.map { it.id })
    }

    @Test
    fun thePopularPreviewPagesThroughItsOwnEndpoint() = runBlocking {
        val http = FakePixivHttp { answer(illustsJson(workJson(3), next = "https://app-api.pixiv.net/v1/search/popular-preview/illust?word=miku&offset=30")) }
        val api = PixivSearchApi(pixiv(http))
        val page = api.popularPreview("miku", PixivSearchTarget.PARTIAL_TAGS, includeAi = true)
        api.popularPreview("miku", PixivSearchTarget.PARTIAL_TAGS, page.next)

        assertEquals("/v1/search/popular-preview/illust", http.asked.first().url.encodedPath)
        assertEquals("true" to "partial_match_for_tags", http.asked.first().query["include_translated_tag_results"] to http.asked.first().query["search_target"])
        assertEquals(listOf(3L), page.items.map { it.id })
        assertEquals("30", http.asked.last().query["offset"])
    }

    @Test
    fun aReshapedAnswerReadsAsAnEmptyPage() = runBlocking {
        val api = PixivSearchApi(pixiv(FakePixivHttp { answer("""{"illusts": "nope", "user_previews": 42}""") }))
        assertTrue(api.illusts(mapOf("word" to "x")).items.isEmpty())
        val users = api.users("x")
        assertTrue(users.items.isEmpty())
        assertNull(users.next)
    }

    @Test
    fun theLandingSuggestsCreatorsFromTheRecommendedList() = runBlocking {
        val http = FakePixivHttp { answer(Fixtures.text("user_previews.json")) }
        val users = PixivSearchApi(pixiv(http)).recommendedUsers()
        assertEquals("/v1/user/recommended", http.asked.single().url.encodedPath)
        assertEquals(listOf("One", "Two"), users.map { it.name })
    }
}
