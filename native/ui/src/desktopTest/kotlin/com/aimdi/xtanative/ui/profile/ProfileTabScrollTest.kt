package com.aimdi.xtanative.ui.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each profile tab keeps its own place in the list they share. */
class ProfileTabScrollTest {
    @Test
    fun withTheHeaderInViewNothingMoves() {
        val scroll = ProfileTabScroll()
        assertNull(scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(0, 120), 1))
    }

    @Test
    fun aTabFirstOpenedBelowTheStuckRowStartsUnderIt() {
        val scroll = ProfileTabScroll()
        assertEquals(ScrollSpot(1, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(14, 30), 1))
    }

    @Test
    fun aTabComesBackWhereItWasLeft() {
        val scroll = ProfileTabScroll()
        scroll.turned(ProfileTab.POSTS, ProfileTab.REPLIES, ScrollSpot(14, 30), 1)
        assertEquals(ScrollSpot(14, 30), scroll.turned(ProfileTab.REPLIES, ProfileTab.POSTS, ScrollSpot(5, 0), 1))
        assertEquals(ScrollSpot(5, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.REPLIES, ScrollSpot(20, 0), 1))
    }

    @Test
    fun aTabLeftAtTheHeaderOpensUnderTheRowWhileItSticks() {
        val scroll = ProfileTabScroll()
        assertNull(scroll.turned(ProfileTab.MEDIA, ProfileTab.POSTS, ScrollSpot(0, 0), 1))
        assertEquals(ScrollSpot(1, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(9, 0), 1))
    }

    @Test
    fun theSameTabAgainStays() {
        assertNull(ProfileTabScroll().turned(ProfileTab.POSTS, ProfileTab.POSTS, ScrollSpot(9, 0), 1))
    }

    @Test
    fun aProblemsRowAboveTheTabsPutsTheirRowOneLower() {
        val scroll = ProfileTabScroll()
        assertNull(scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(1, 40), 2), "the problem's row in view")
        assertEquals(ScrollSpot(2, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(14, 30), 2))
        // The problem's row went: the posts come back where they were, under the row.
        assertEquals(ScrollSpot(13, 30), scroll.turned(ProfileTab.MEDIA, ProfileTab.POSTS, ScrollSpot(6, 0), 1))
    }

    @Test
    fun theTabRowComesAfterTheHeaderAndAnyRowShownUnderIt() {
        assertEquals(listOf(ProfileTop.HEADER), ProfileTabScroll.itemsAbove(failed = false, firstLoad = false, problem = false))
        assertEquals(listOf(ProfileTop.HEADER, ProfileTop.PROBLEM), ProfileTabScroll.itemsAbove(failed = false, firstLoad = false, problem = true))
        assertEquals(listOf(ProfileTop.HEADER, ProfileTop.FAILURE), ProfileTabScroll.itemsAbove(failed = true, firstLoad = true, problem = true))
        assertEquals(2, ProfileTabScroll.itemsAbove(failed = false, firstLoad = true, problem = false).size)
    }
}
