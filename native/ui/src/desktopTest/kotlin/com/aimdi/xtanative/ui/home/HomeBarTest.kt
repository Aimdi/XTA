package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.settings.HomePages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's bottom bar (lib/home/home_screen.dart, home_model.dart, network_switcher.dart, home_navigation_visibility.dart). */
class HomeBarTest {
    private val networks = setOf("reddit", "pixiv", "bluesky")

    private fun pages(vararg ids: String, groups: Set<String> = setOf("g1")) = HomeBar.pages(ids.toList(), networks::contains, groups)

    @Test
    fun theBarHoldsTheReadersPagesInTheirOrder() {
        assertEquals(
            listOf(BarPage.Saved, BarPage.Feed, BarPage.Network("reddit"), BarPage.Group("g1")),
            pages("saved", "feed", "reddit", "group-g1"),
        )
        assertEquals("group-g1", BarPage.Group("g1").id)
        assertEquals(listOf("feed", "subscriptions", "trending", "saved"), HomeBar.DEFAULT_PAGES.map { it.id })
    }

    @Test
    fun aPageHomeCannotShowIsLeftOffAndTooFewMeansHomesFour() {
        assertEquals(listOf(BarPage.Feed, BarPage.Saved), pages("feed", "gone-network", "group-gone", "saved", "feed"))
        assertEquals(HomeBar.DEFAULT_PAGES, pages("feed"))
        assertEquals(HomeBar.DEFAULT_PAGES, pages())
        assertEquals(HomeBar.DEFAULT_PAGES, pages("gone", "group-gone"))
    }

    @Test
    fun theBarReadsSettingsHomeAsItLoads() {
        val session = Session(groups = listOf(FollowGroup("g1", "News", listOf("1"))))
        val settings = Settings().with(HomePrefs.pages, listOf("trending", "group-g1", "feed"))
        val loaded = HomePages.load(settings, session.groups.map { it.id })
        assertEquals(listOf(BarPage.Trending, BarPage.Group("g1"), BarPage.Feed), HomeBar.pages(loaded.saved, networks::contains, setOf("g1")))
    }

    @Test
    fun homeOpensOnItsInitialPageWhenItIsOnTheBar() {
        val bar = pages("feed", "saved", "group-g1")
        assertEquals(1, HomeBar.initialIndex(bar, "saved"))
        assertEquals(2, HomeBar.initialIndex(bar, " group-g1 "))
        assertEquals(0, HomeBar.initialIndex(bar, "subscriptions"))
        assertTrue(HomeBar.opensOnBar(bar, "saved"))
        assertFalse(HomeBar.opensOnBar(bar, "trending"))
    }

    @Test
    fun oneNetworkKeepsItsPlaceAfterTheOthersAndMoreGoBehindNetworks() {
        val one = pages("reddit", "feed", "saved")
        assertEquals(listOf(BarSlot.Page(1), BarSlot.Page(2), BarSlot.Page(0)), HomeBar.slots(one, emptyList()))

        val three = pages("feed", "reddit", "pixiv", "saved", "bluesky")
        assertEquals(listOf(BarSlot.Page(0), BarSlot.Page(3), BarSlot.Page(1), BarSlot.Networks), HomeBar.slots(three, emptyList()))
        assertEquals(listOf(BarSlot.Page(0), BarSlot.Page(3), BarSlot.Page(4), BarSlot.Networks), HomeBar.slots(three, listOf("gone", "bluesky", "pixiv")))
    }

    @Test
    fun aNetworkBehindNetworksLightsItUp() {
        val three = pages("feed", "reddit", "pixiv", "saved")
        val slots = HomeBar.slots(three, listOf("reddit"))
        assertEquals(0, HomeBar.slotOf(slots, 0))
        assertEquals(2, HomeBar.slotOf(slots, 1))
        assertEquals(3, HomeBar.slotOf(slots, 2), "pixiv is behind Networks")
        assertEquals(0, HomeBar.slotOf(HomeBar.slots(pages("feed", "saved"), emptyList()), 7))
    }

    @Test
    fun theNetworksSheetListsTheRecentOnesFirst() {
        val bar = pages("feed", "reddit", "pixiv", "bluesky")
        assertEquals(listOf("reddit", "pixiv", "bluesky"), HomeBar.networks(bar))
        assertEquals(listOf("bluesky", "reddit") to listOf("pixiv"), HomeBar.networkSections(HomeBar.networks(bar), listOf("bluesky", "gone", "reddit", "bluesky")))
        assertEquals(emptyList<String>() to listOf("reddit"), HomeBar.networkSections(listOf("reddit"), emptyList()))
    }

    @Test
    fun aSwipeAcrossTheBarTurnsOnePageWhenItGoesFarOrFastEnough() {
        assertEquals(2, HomeBar.swiped(1, 4, distance = -80f, velocity = 0f))
        assertEquals(0, HomeBar.swiped(1, 4, distance = 80f, velocity = 0f))
        assertEquals(1, HomeBar.swiped(1, 4, distance = -40f, velocity = -100f), "too short and too slow")
        assertEquals(2, HomeBar.swiped(1, 4, distance = -40f, velocity = -900f), "a flick")
        assertEquals(1, HomeBar.swiped(1, 4, distance = -10f, velocity = -900f), "a tap that moved")
        assertEquals(1, HomeBar.swiped(1, 4, distance = -80f, velocity = 300f), "released the other way")
        assertEquals(3, HomeBar.swiped(3, 4, distance = -80f, velocity = 0f), "no wrapping")
        assertEquals(0, HomeBar.swiped(1, 4, distance = -80f, velocity = 0f, rtl = true))
        assertEquals(0, HomeBar.swiped(0, 1, distance = -80f, velocity = 0f))
    }

    @Test
    fun theBarsFollowTheFingerAndSettleWhenTheScrollStops() {
        val hiding = BarHiding(hideAfter = 24f, showAfter = 12f)
        hiding.scrolled(50f, travel = 100f)
        assertEquals(0.5f, hiding.hidden)
        hiding.scrolled(500f, travel = 100f)
        assertEquals(1f, hiding.hidden)
        hiding.scrolled(-10f, travel = 100f)
        assertEquals(0.9f, hiding.hidden, 0.0001f)
        assertEquals(1f, hiding.resting(100f), "back up by less than 12: away again")
        hiding.scrolled(-20f, travel = 100f)
        assertEquals(0f, hiding.resting(100f), "back up by 30: back")
        hiding.shown()
        hiding.scrolled(20f, travel = 100f)
        assertEquals(0f, hiding.resting(100f), "down by less than 24: springs back")
        hiding.scrolled(10f, travel = 100f)
        assertEquals(1f, hiding.resting(100f))
        assertEquals(0.3f, hiding.hidden, 0.0001f, "resting only says where")
        assertEquals(0.4f, BarHiding.followed(0.4f, 10f, travel = 0f), "nothing measured yet")
    }

    @Test
    fun theMenuLeavesOutThePagesOnTheBar() {
        val all = HomeMenu.shown(HomeMenu.list, { true }, deckFits = true, onBar = setOf("feed", "subscriptions", "saved")).map { it.route }
        assertEquals(listOf("trends", "downloads", "antennas", "plugins", "deck"), all)
        assertEquals(HomeBar.TRENDING, HomeDestination.TRENDS.barPage)
    }
}
