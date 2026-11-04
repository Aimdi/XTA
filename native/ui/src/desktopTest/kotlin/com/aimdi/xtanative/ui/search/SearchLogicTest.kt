package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.search.XAdvancedSearch
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Filter
import com.aimdi.xtanative.core.x.search.XSearchView
import com.aimdi.xtanative.store.settings.MediaGridLayout
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchLogicTest {
    private val maya = XUser("1", "maya", "Maya Rivers")
    private val jo = XUser("2", "jo", "Jo")

    private fun photo(width: Int = 100, height: Int = 100) = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/a.jpg", width, height)

    @Test
    fun aRouteNamesItsTab() {
        assertEquals(SearchTab.PEOPLE, SearchTab.of("people"))
        assertEquals(SearchTab.COMMUNITIES, SearchTab.of(SearchTab.COMMUNITIES.routeName))
        assertEquals(SearchTab.TOP, SearchTab.of(null))
        assertEquals(SearchTab.TOP, SearchTab.of("trending"))
        assertEquals(listOf("Top", "Latest", "Media", null, null), SearchTab.entries.map { it.product })
    }

    @Test
    fun mediaTilesAreEachPostsOwnAndARepostsAreThePostReposted() {
        val own = XPost("10", maya, null, "two photos", media = listOf(photo(), photo()))
        val original = XPost("20", jo, null, "a photo", media = listOf(photo()))
        val repost = XPost("21", maya, null, "", reposted = original)
        val tiles = MediaTile.of(listOf(XThread("10", listOf(own)), XThread("21", listOf(repost))))
        assertEquals(listOf("10/0", "10/1", "20/0"), tiles.map { it.key })
        assertEquals(listOf(0, 1, 0), tiles.map { it.index })
    }

    @Test
    fun theGridFollowsTheMediaSettings() {
        assertEquals(MediaGridShape(3, false), MediaGridShape.of(MediaGridLayout.MASONRY, 3))
        assertEquals(MediaGridShape(5, false), MediaGridShape.of(MediaGridLayout.MASONRY, 9))
        assertEquals(MediaGridShape(2, false), MediaGridShape.of(MediaGridLayout.TWO_COLUMNS, 4))
        val feed = MediaGridShape.of(MediaGridLayout.FEED, 3)
        assertEquals(1, feed.columns)
        assertEquals(1.5f, feed.aspectOf(photo(300, 200)))
        assertEquals(MediaGridShape.MIN_ASPECT, feed.aspectOf(photo(100, 400)))
        assertEquals(MediaGridShape.MAX_ASPECT, feed.aspectOf(photo(1000, 100)))
        assertEquals(1f, MediaGridShape.of(MediaGridLayout.MASONRY, 3).aspectOf(photo(300, 200)))
    }

    @Test
    fun chipsNameEachFilterSetWithItsValue() {
        val search = XAdvancedSearch(
            allWords = "cats",
            language = "de",
            fromAccounts = "maya",
            links = XAdvancedSearch.Inclusion.EXCLUDE,
            replies = XAdvancedSearch.Inclusion.ONLY,
            excludeRetweets = true,
            since = LocalDate.of(2026, 1, 31),
        )
        val chips = SearchFilters.chips(search)
        assertEquals(
            listOf(
                FilterChip(Filter.ALL_WORDS, FilterLabel.ALL_WORDS, "cats"),
                FilterChip(Filter.LANGUAGE, FilterLabel.LANGUAGE, "Deutsch"),
                FilterChip(Filter.FROM_ACCOUNTS, FilterLabel.FROM_ACCOUNTS, "maya"),
                FilterChip(Filter.LINKS, FilterLabel.HIDE_LINKS, ""),
                FilterChip(Filter.REPLIES, FilterLabel.ONLY_REPLIES, ""),
                FilterChip(Filter.EXCLUDE_RETWEETS, FilterLabel.HIDE_RETWEETS, ""),
                FilterChip(Filter.SINCE, FilterLabel.SINCE, "2026-01-31"),
            ),
            chips,
        )
    }

    @Test
    fun theFormOpensOnTheFiltersShownElseOnWhatIsTyped() {
        val typed = XSearchView.typed("cats lang:fr")
        assertEquals(typed.advanced, SearchFilters.formFor(typed, "dogs"))
        val plain = XSearchView.typed("cats")
        assertEquals("fr", SearchFilters.formFor(plain, "dogs lang:fr").language)
    }

    @Test
    fun languagesAreNamedInTheirOwnAndSortedByThatName() {
        assertEquals("Deutsch", SearchLanguages.name("de"))
        assertEquals("norsk", SearchLanguages.name("no"))
        assertEquals("xx", SearchLanguages.name("xx"))
        val choices = SearchLanguages.choices("")
        assertEquals(XAdvancedSearch.LANGUAGES.size, choices.size)
        assertEquals(choices.sortedBy { SearchLanguages.name(it).lowercase() }, choices)
        assertTrue(choices.indexOf("de") < choices.indexOf("en"))
        assertTrue("pt-br" in SearchLanguages.choices("pt-br"))
        assertEquals(choices.size, SearchLanguages.choices("de").size)
    }

    @Test
    fun aLoadedPostIsFoundByItsTextAuthorLinksAndWhatItQuotes() {
        val link = XEntity.Link(0, 23, "https://t.co/x", "https://example.com/story", "example.com/story")
        val quoted = XPost("30", jo, null, "Quoted words")
        val post = XPost("31", maya, null, "Hello World", entities = listOf(link), quoted = quoted)
        val thread = XThread("31", listOf(post))
        assertTrue(LoadedFeed.matches(LoadedFeed.textOf(thread), "hello"))
        assertTrue(LoadedFeed.matches(LoadedFeed.textOf(thread), "WORLD   maya"))
        assertTrue(LoadedFeed.matches(LoadedFeed.textOf(thread), "example.com quoted"))
        assertFalse(LoadedFeed.matches(LoadedFeed.textOf(thread), "hello cats"))
        assertTrue(LoadedFeed.matches(LoadedFeed.textOf(thread), "  "))

        val other = XThread("40", listOf(XPost("40", jo, null, "Cats")))
        assertEquals(listOf(other), LoadedFeed.Index(listOf(thread, other)).matching("cats"))
        assertEquals(listOf(thread, other), LoadedFeed.Index(listOf(thread, other)).matching(""))
    }
}

class SearchDatesTest {
    @Test
    fun daysGoToThePickerAndBackAsUtcMidnights() {
        val day = LocalDate.of(2026, 1, 31)
        assertEquals(1_769_817_600_000L, SearchDates.millisOf(day))
        assertEquals(day, SearchDates.dayOf(SearchDates.millisOf(day)))
        assertEquals(day, SearchDates.dayOf(SearchDates.millisOf(day) + 23 * 3_600_000L))
    }

    @Test
    fun thePickerOffersXsYearsUpToToday() {
        val today = LocalDate.of(2026, 11, 4)
        assertTrue(SearchDates.selectable(SearchDates.millisOf(today), today))
        assertTrue(SearchDates.selectable(SearchDates.millisOf(SearchDates.FIRST), today))
        assertFalse(SearchDates.selectable(SearchDates.millisOf(today.plusDays(1)), today))
        assertFalse(SearchDates.selectable(SearchDates.millisOf(LocalDate.of(2005, 12, 31)), today))
        assertEquals(2006..2026, SearchDates.years(today))
        assertEquals(today, SearchDates.opening(null, today))
        assertEquals(SearchDates.FIRST, SearchDates.opening(SearchDates.FIRST, today))
    }
}
