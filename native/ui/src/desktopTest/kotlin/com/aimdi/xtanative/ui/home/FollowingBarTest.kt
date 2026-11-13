package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Following's one bar and the timeline picker (lib/home/home_timeline_controls.dart, home_timeline_picker.dart, home_source_picker.dart). */
class FollowingBarTest {
    @Test
    fun theTabsMoveCloserThenToASecondRowWhenTheyDoNotFit() {
        // Posts | Media at 100 dp together, three buttons beside the order: 4 + 4 × 48 + 4 = 200 dp of controls.
        assertEquals(BarFit.ONE_ROW, FollowingBar.fit(width = 356f, labels = 100f, actions = 3))
        assertEquals(BarFit.COMPACT, FollowingBar.fit(width = 355f, labels = 100f, actions = 3))
        assertEquals(BarFit.COMPACT, FollowingBar.fit(width = 332f, labels = 100f, actions = 3))
        assertEquals(BarFit.TWO_ROWS, FollowingBar.fit(width = 331f, labels = 100f, actions = 3))
        assertEquals(BarFit.TWO_ROWS, FollowingBar.fit(width = 360f, labels = 100f, actions = FollowingBar.actions(desktop = true)))
    }

    @Test
    fun twoRowsAreTwiceAsTall() {
        assertEquals(48f, FollowingBar.height(BarFit.ONE_ROW))
        assertEquals(48f, FollowingBar.height(BarFit.COMPACT))
        assertEquals(96f, FollowingBar.height(BarFit.TWO_ROWS))
        assertEquals(3, FollowingBar.actions(desktop = false))
    }

    @Test
    fun theOrderMenuKeepsXtasOrderAndCustomOpensItsRules() {
        assertEquals(listOf(FeedSort.RECENT, FeedSort.POPULAR, FeedSort.CUSTOM), FollowingBar.ORDERS)
        assertTrue(FollowingBar.opensCustom(FeedSort.CUSTOM))
        assertFalse(FollowingBar.opensCustom(FeedSort.POPULAR))
        assertEquals("following", FollowingBar.MEDIA_KEY)
    }

    @Test
    fun thePickerSearchesOnlyWhenThereIsMuchToPickFrom() {
        assertFalse(SourcePicker.hasSearch(8, ""))
        assertTrue(SourcePicker.hasSearch(9, ""))
        assertTrue(SourcePicker.hasSearch(2, "n"), "a search typed stays")
    }

    @Test
    fun aSearchMatchesEveryWordAnywhereWhateverTheCase() {
        assertTrue(SourcePicker.matches("Tech News Daily", "news tech"))
        assertTrue(SourcePicker.matches("Tech News", "  "))
        assertFalse(SourcePicker.matches("Tech News", "news sport"))
    }

    @Test
    fun thePickerOffersTheTimelinesAndListsGroupsApart() {
        val tabs = HomeStrip.tabs(listOf("reddit"), { true }, listOf(FollowGroup("g", "G", listOf("1"))))
        assertEquals(listOf(HomeTab.Following, HomeTab.ForYou, HomeTab.Network("reddit")), SourcePicker.sources(tabs))
    }

    @Test
    fun aTimelineAskedForIsTakenOnce() {
        HomeRequests.show(HomeTab.ForYou.key)
        assertEquals(HomeTab.ForYou.key, HomeRequests.tab.value)
        HomeRequests.taken(HomeTab.Following.key)
        assertEquals(HomeTab.ForYou.key, HomeRequests.tab.value, "another request is left alone")
        HomeRequests.taken(HomeTab.ForYou.key)
        assertNull(HomeRequests.tab.value)
    }
}
