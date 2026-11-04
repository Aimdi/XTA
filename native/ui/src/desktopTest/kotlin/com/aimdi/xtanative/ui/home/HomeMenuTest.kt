package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's drawer (lib/home/home_group_drawer.dart) as Home's account menu, and the strip's sheet (feed_strip_add_sheet.dart). */
class HomeMenuTest {
    @Test
    fun theMenuShowsOnlyTheScreensTheAppHas() {
        val routes = setOf("search", "settings", "saved", "deck")
        assertEquals(listOf(HomeDestination.SEARCH, HomeDestination.SETTINGS), HomeMenu.shown(HomeMenu.buttons, routes::contains, deckFits = true))
        assertEquals(listOf(HomeDestination.SAVED, HomeDestination.DECK), HomeMenu.shown(HomeMenu.list, routes::contains, deckFits = true))
        assertEquals(listOf(HomeDestination.SAVED), HomeMenu.shown(HomeMenu.list, routes::contains, deckFits = false))
    }

    @Test
    fun theMenuKeepsXtasOrder() {
        val everything = HomeMenu.shown(HomeMenu.list, { true }, deckFits = true).map { it.route }
        assertEquals(listOf("subscriptions", "trends", "saved", "downloads", "antennas", "plugins", "deck"), everything)
        assertEquals(HomeDestination.entries.toSet(), (HomeMenu.buttons + HomeMenu.list).toSet())
    }

    @Test
    fun aGroupOpensItsScreenOnlyOnceTheAppHasOne() {
        val opened = mutableListOf<String>()
        val without = HomeRoutes({ false }, { opened += it })
        assertFalse(without.openGroup("g 1"))
        assertFalse(without.openIfExists("saved"))
        val with = HomeRoutes({ it == HomeRoutes.GROUP || it == "saved" }, { opened += it })
        assertTrue(with.openGroup("g 1"))
        assertTrue(with.openIfExists("saved"))
        assertEquals(listOf("group/g%201", "saved"), opened)
    }

    private val pinnedOne = FollowGroup("a", "Alpha", listOf("1"), pinned = true)
    private val plain = FollowGroup("b", "Beta", listOf("2"))
    private val empty = FollowGroup("c", "Calm", emptyList())

    @Test
    fun theMenuListsTheGroupsThatCanOpen() {
        assertEquals(listOf("a", "b"), DrawerGroups.listed(listOf(plain, pinnedOne, empty), listOf(plain, pinnedOne), groupScreen = false).map { it.id })
        assertEquals(listOf("a", "b", "c"), DrawerGroups.listed(listOf(plain, pinnedOne, empty), listOf(plain, pinnedOne), groupScreen = true).map { it.id })
    }

    @Test
    fun theGroupSearchKeepsPinnedGroupsFirst() {
        assertEquals(listOf("a", "b"), DrawerGroups.matching(listOf(plain, pinnedOne), "").map { it.id })
        assertEquals(listOf("a", "c"), DrawerGroups.matching(listOf(empty, plain, pinnedOne), " AL").map { it.id })
        assertEquals(listOf("b"), DrawerGroups.matching(listOf(plain, pinnedOne), "bet").map { it.id })
        assertTrue(DrawerGroups.matching(listOf(plain), "zzz").isEmpty())
    }

    private val reddit = StripNetwork("reddit", "Reddit")
    private val pixiv = StripNetwork("pixiv", "Pixiv")
    private val bluesky = StripNetwork("bluesky", "Bluesky")

    @Test
    fun theSheetSplitsPinnedAndAvailableNetworks() {
        val available = listOf(reddit, pixiv, bluesky, StripNetwork("x", "X"))
        assertEquals(listOf(pixiv, reddit), StripPins.pinned(listOf("pixiv", "gone", "reddit", "pixiv"), available))
        assertEquals(listOf(bluesky), StripPins.candidates(listOf("pixiv", "reddit"), available))
        assertTrue(StripPins.pinned(listOf("x"), available).isEmpty())
    }

    @Test
    fun movingAPinKeepsTheNetworksShown() {
        val available = listOf(reddit, pixiv, bluesky)
        assertEquals(listOf("pixiv", "reddit", "bluesky"), StripPins.moved(listOf("reddit", "gone", "pixiv", "bluesky"), available, 0, 1))
        assertEquals(listOf("bluesky", "reddit", "pixiv"), StripPins.moved(listOf("reddit", "pixiv", "bluesky"), available, 2, 0))
        assertEquals(listOf("reddit", "pixiv"), StripPins.moved(listOf("reddit", "pixiv"), available, 5, 0))
    }
}
