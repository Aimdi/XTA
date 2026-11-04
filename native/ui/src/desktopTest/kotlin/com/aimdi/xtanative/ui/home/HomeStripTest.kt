package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** XTA's home strip (lib/home/_feed.dart, home_screen.dart, network_recents_store.dart) as Home's tabs. */
class HomeStripTest {
    private val tests = FollowGroup("g1", "Tests", listOf("1"), emoji = "🧪")
    private val news = FollowGroup("g2", "News", listOf("2"))

    @Test
    fun followingAndForYouComeFirstThenPinnedNetworksThenGroups() {
        val tabs = HomeStrip.tabs(listOf("reddit", "x", "pixiv", "reddit", "gone"), { it != "gone" }, listOf(tests, news))
        assertEquals(
            listOf(HomeTab.Following, HomeTab.ForYou, HomeTab.Network("reddit"), HomeTab.Network("pixiv"), HomeTab.Group("g1", "🧪 Tests"), HomeTab.Group("g2", "News")),
            tabs,
        )
        assertEquals(listOf(HomeTab.Following, HomeTab.ForYou), HomeStrip.tabs(emptyList(), { true }, emptyList()))
    }

    @Test
    fun aGroupIsNamedByItsEmojiAndName() {
        assertEquals("🧪 Tests", HomeStrip.groupLabel(tests))
        assertEquals("News", HomeStrip.groupLabel(news.copy(emoji = " ")))
    }

    @Test
    fun keysNeverCollide() {
        val keys = listOf(HomeTab.Following, HomeTab.ForYou, HomeTab.Network("following"), HomeTab.Group("following", "x")).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun homeOpensOnTheDefaultFeed() {
        val tabs = HomeStrip.tabs(listOf("reddit"), { true }, listOf(tests))
        assertEquals(0, HomeStrip.initialIndex(tabs, "feed", "following"))
        assertEquals(1, HomeStrip.initialIndex(tabs, "feed", "x"))
        assertEquals(1, HomeStrip.initialIndex(tabs, "feed", "foryou"))
        assertEquals(2, HomeStrip.initialIndex(tabs, "feed", "reddit"))
        // A network no longer on the strip falls back to Following, as XTA's home does.
        assertEquals(0, HomeStrip.initialIndex(tabs, "feed", "pixiv"))
        assertEquals(0, HomeStrip.initialIndex(tabs, "", ""))
    }

    @Test
    fun theInitialPageWinsWhenItIsOnTheStrip() {
        val tabs = HomeStrip.tabs(listOf("reddit"), { true }, listOf(tests))
        assertEquals(3, HomeStrip.initialIndex(tabs, "group-g1", "x"))
        assertEquals(2, HomeStrip.initialIndex(tabs, "reddit", "x"))
        assertEquals(1, HomeStrip.initialIndex(tabs, "group-missing", "x"))
        assertEquals(1, HomeStrip.initialIndex(tabs, "saved", "x"))
    }

    @Test
    fun theBarPagesThatAreScreensOpenOverHome() {
        assertEquals("subscriptions", HomeStrip.startRoute("subscriptions"))
        assertEquals("trends", HomeStrip.startRoute("trending"))
        assertEquals("saved", HomeStrip.startRoute("saved"))
        assertNull(HomeStrip.startRoute("feed"))
        assertNull(HomeStrip.startRoute("group-g1"))
        assertNull(HomeStrip.pageKey("saved"))
        assertNull(HomeStrip.pageKey("feed"))
    }

    @Test
    fun theTabShownStaysWhenTabsComeAndGo() {
        val before = HomeStrip.tabs(emptyList(), { true }, listOf(tests))
        val after = HomeStrip.tabs(listOf("reddit"), { true }, listOf(tests))
        assertEquals(3, HomeStrip.indexOf(after, before[2].key))
        assertEquals(0, HomeStrip.indexOf(before, HomeTab.Network("reddit").key))
    }

    @Test
    fun recentNetworksAreNewestFirstAndBounded() {
        assertEquals(listOf("b", "a", "c"), HomeStrip.touched(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a"), HomeStrip.touched(listOf("a"), ""))
        val many = (1..12).map { "n$it" }
        assertEquals(listOf("new") + many.take(11), HomeStrip.touched(many, "new"))
    }
}
