package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Home in a wide window: the navigation beside the pages, and the timelines read side by side. */
class HomeWideTest {
    @Test
    fun theNavigationStandsBesideThePagesOnlyInAWideWindow() {
        assertFalse(HomeWide.sidebar(412f), "a phone")
        assertFalse(HomeWide.sidebar(999f))
        assertTrue(HomeWide.sidebar(1000f))
        assertTrue(HomeWide.sidebar(1892f), "a 1080p desktop window")
    }

    @Test
    fun asManyTimelinesAsFitAreReadSideBySideUpToThree() {
        assertEquals(1, HomeWide.columns(412f, timelines = 4), "a phone reads one")
        assertEquals(1, HomeWide.columns(839f, timelines = 4))
        assertEquals(2, HomeWide.columns(840f, timelines = 4))
        assertEquals(3, HomeWide.columns(1572f, timelines = 4), "1080p beside the navigation")
        assertEquals(3, HomeWide.columns(3000f, timelines = 9), "never more than three")
        assertEquals(2, HomeWide.columns(1572f, timelines = 2), "no more columns than timelines")
        assertEquals(1, HomeWide.columns(1572f, timelines = 0))
    }

    @Test
    fun columnsShareTheWidthButGrowNoWiderThanTheReadingColumn() {
        assertEquals(479.5f, HomeWide.columnWidth(960f, 2))
        assertEquals(HomeWide.COLUMN_MAX, HomeWide.columnWidth(3000f, 2))
        assertEquals(500f, HomeWide.columnWidth(500f, 1))
    }

    @Test
    fun theTimelinesBesideTheOneShownFollowItAndGoRound() {
        val tabs = listOf("following", "x", "reddit", "rss")
        assertEquals(listOf("x", "reddit"), HomeWide.beside(tabs, 0, 3))
        assertEquals(listOf("rss", "following"), HomeWide.beside(tabs, 2, 3), "past the last, the first")
        assertEquals(listOf("following"), HomeWide.beside(tabs, 3, 2))
        assertEquals(emptyList(), HomeWide.beside(tabs, 0, 1), "one column: none beside it")
        assertEquals(emptyList(), HomeWide.beside(tabs, 7, 3), "no timeline shown")
        assertEquals(listOf("x"), HomeWide.beside(listOf("following", "x"), 0, 3), "never one twice")
    }
}
