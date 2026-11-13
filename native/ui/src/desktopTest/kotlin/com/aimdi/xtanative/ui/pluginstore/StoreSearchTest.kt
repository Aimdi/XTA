package com.aimdi.xtanative.ui.pluginstore

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreSearchTest {
    private fun hackerNews(query: String) =
        StoreSearch.matches(query, "hackernews", "Hacker News", "Read Hacker News without an account.", "Communities")

    @Test
    fun theStoreFindsAPluginByNameIdOrInitials() {
        assertTrue(hackerNews("hacker"))
        assertTrue(hackerNews("hn"), "its initials")
        assertTrue(hackerNews("Hacker News"), "its name, the id without the space")
        assertTrue(hackerNews("  "), "nothing typed keeps every plugin")
        assertFalse(hackerNews("pixiv"))
    }

    @Test
    fun itFindsOneByWhatItDoesOrItsCategory() {
        assertTrue(hackerNews("without an ACCOUNT"))
        assertTrue(hackerNews("communit"))
        assertFalse(hackerNews("markets"))
    }

    @Test
    fun noResultsOnlyForAQueryThatLeftNothing() {
        assertTrue(StoreSearch.noResults("zzz", installed = 0, offered = 0))
        assertFalse(StoreSearch.noResults("", installed = 0, offered = 0), "an empty store says it is empty instead")
        assertFalse(StoreSearch.noResults("red", installed = 1, offered = 0))
        assertFalse(StoreSearch.noResults("blue", installed = 0, offered = 0, grouping = true), "the grouping row is still there")
    }

    @Test
    fun theGroupingRowStaysWhileTheSearchNamesIt() {
        val words = "Mastodon & Bluesky Group microblogging services Mastodon Bluesky Threads"
        assertTrue(StoreSearch.showsGrouping("", words))
        assertTrue(StoreSearch.showsGrouping("  THREADS ", words))
        assertTrue(StoreSearch.showsGrouping("microblog", words))
        assertFalse(StoreSearch.showsGrouping("reddit", words))
    }
}
