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
    fun theBarsSwipeRulesAreXtasPageAfterNavigationSwipe() {
        assertEquals(2, HomeBar.swiped(1, 4, distance = -32f, velocity = -800f), "a leftward flick advances")
        assertEquals(0, HomeBar.swiped(1, 4, distance = 32f, velocity = 800f), "a rightward one goes back")
        assertEquals(0, HomeBar.swiped(0, 4, distance = 32f, velocity = 800f), "the ends clamp")
        assertEquals(3, HomeBar.swiped(3, 4, distance = -32f, velocity = -800f))
        assertEquals(1, HomeBar.swiped(1, 4, distance = 0f, velocity = -50f), "a mis-tap")
        assertEquals(1, HomeBar.swiped(1, 4, distance = -4f, velocity = -50f))
        assertEquals(2, HomeBar.swiped(1, 4, distance = -120f, velocity = 0f), "a slow but long drag counts")
        assertEquals(0, HomeBar.swiped(1, 4, distance = 120f, velocity = 0f))
        assertEquals(0, HomeBar.swiped(0, 4, distance = 120f, velocity = 0f), "a long drag still clamps")
        assertEquals(3, HomeBar.swiped(3, 4, distance = -120f, velocity = 0f))
        assertEquals(1, HomeBar.swiped(1, 4, distance = 60f, velocity = -800f), "an opposing release cancels")
        assertEquals(1, HomeBar.swiped(1, 4, distance = -8f, velocity = -1600f), "a fast but tiny movement")
        assertEquals(1, HomeBar.swiped(1, 4, distance = -100f, velocity = 500f), "a moderate opposing release")
        assertEquals(0, HomeBar.swiped(0, 1, distance = 0f, velocity = -800f), "a single tab")
        assertEquals(0, HomeBar.swiped(0, 1, distance = -400f, velocity = 0f))
    }

    @Test
    fun settlingTakesUpTo250MsNeverUnder120() {
        assertEquals(250, BarHiding.settleMillis(1f))
        assertEquals(150, BarHiding.settleMillis(0.6f))
        assertEquals(120, BarHiding.settleMillis(0.1f))
    }

    @Test
    fun aStoppedBarRestsByTheLastDirectionAndHowFarItWent() {
        val hiding = BarHiding(hideAfter = 24f, showAfter = 12f)
        assertFalse(hiding.restsHidden(10f, 100f, movingDown = true), "under 24 down springs back")
        assertTrue(hiding.restsHidden(24f, 100f, movingDown = true))
        assertTrue(hiding.restsHidden(95f, 100f, movingDown = false), "under 12 up drops away again")
        assertFalse(hiding.restsHidden(88f, 100f, movingDown = false))
    }

    @Test
    fun theMenuLeavesOutThePagesOnTheBar() {
        val all = HomeMenu.shown(HomeMenu.list, { true }, deckFits = true, onBar = setOf("feed", "subscriptions", "saved")).map { it.route }
        assertEquals(listOf("trends", "downloads", "antennas", "plugins", "deck"), all)
        assertEquals(HomeBar.TRENDING, HomeDestination.TRENDS.barPage)
    }
}
