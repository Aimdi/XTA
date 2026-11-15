package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkBarRulesTest {
    @Test
    fun redditsSectionsTakeTheBottomBarOtherNetworksKeepTheirs() {
        assertTrue(NetworkBarRules.takesOver("reddit", 3))
        assertFalse(NetworkBarRules.takesOver("reddit", 1), "one section is no switch worth the bar")
        assertFalse(NetworkBarRules.takesOver("bluesky", 3))
        assertFalse(NetworkBarRules.takesOver(null, 3), "Following and X keep the app's bar")
    }

    @Test
    fun moreListsEveryPlaceButHomesFeed() {
        val bar = listOf(BarPage.Feed, BarPage.Subscriptions, BarPage.Trending, BarPage.Saved)
        val slots = bar.indices.map { BarSlot.Page(it) } + BarSlot.Networks
        assertEquals(listOf(BarSlot.Page(1), BarSlot.Page(2), BarSlot.Page(3), BarSlot.Networks), NetworkBarRules.moreSlots(slots, bar))
    }

    @Test
    fun theTakeoverFollowsTheTimelineShown() {
        val takeover = NetworkBarTakeover()
        takeover.show("reddit", null)
        assertFalse(takeover.active, "no sections handed yet: the app's bar stays")
        takeover.show(null, null)
        assertEquals(null, takeover.network)
    }
}
