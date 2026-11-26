package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import kotlin.test.Test
import kotlin.test.assertEquals

/** A list's sticky tabs keep what the keys focus out from under them ([StickyHeaderCover]). */
@OptIn(ExperimentalFoundationApi::class)
class StickyHeaderCoverTest {
    private val platform = object : BringIntoViewSpec {}

    @Test
    fun aStuckHeaderCoversTheListsTopToItsEnd() {
        assertEquals(48, StickyHeaderCover.covered(top = 0, size = 48))
        assertEquals(30, StickyHeaderCover.covered(top = -18, size = 48), "pushed partly off the top")
    }

    @Test
    fun aHeaderScrollingWithTheRowsOrNotShownCoversNothing() {
        assertEquals(0, StickyHeaderCover.covered(top = 300, size = 48), "still in its place under the community's header")
        assertEquals(0, StickyHeaderCover.covered(top = null, size = 0), "a tab without the sticky tabs")
    }

    @Test
    fun aButtonUnderTheTabsIsScrolledOutFromUnderThem() {
        // A post's Translate at the list's top, 48 px tall, under 48 px of stuck tabs, in an 800 px list.
        assertEquals(0f, StickyHeaderCover.distance(platform, offset = 0f, size = 48f, container = 800f, cover = 0f), "nothing covers it: shown as it is")
        assertEquals(-48f, StickyHeaderCover.distance(platform, offset = 0f, size = 48f, container = 800f, cover = 48f))
        assertEquals(-20f, StickyHeaderCover.distance(platform, offset = 28f, size = 48f, container = 800f, cover = 48f), "half under them")
    }

    @Test
    fun whatIsClearOfTheTabsOrBelowStaysAsThePlatformScrollsIt() {
        assertEquals(0f, StickyHeaderCover.distance(platform, offset = 60f, size = 48f, container = 800f, cover = 48f))
        assertEquals(48f, StickyHeaderCover.distance(platform, offset = 800f, size = 48f, container = 800f, cover = 48f), "below the list's end")
    }

    @Test
    fun aCoverAsTallAsTheListIsIgnored() {
        assertEquals(0f, StickyHeaderCover.distance(platform, offset = 0f, size = 48f, container = 48f, cover = 48f))
    }
}
