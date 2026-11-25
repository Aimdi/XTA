package com.aimdi.xtanative.ui.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each profile tab keeps its own place in the list they share. */
class ProfileTabScrollTest {
    @Test
    fun withTheHeaderInViewNothingMoves() {
        val scroll = ProfileTabScroll()
        assertNull(scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(0, 120)))
    }

    @Test
    fun aTabFirstOpenedBelowTheStuckRowStartsUnderIt() {
        val scroll = ProfileTabScroll()
        assertEquals(ScrollSpot(1, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(14, 30)))
    }

    @Test
    fun aTabComesBackWhereItWasLeft() {
        val scroll = ProfileTabScroll()
        scroll.turned(ProfileTab.POSTS, ProfileTab.REPLIES, ScrollSpot(14, 30))
        assertEquals(ScrollSpot(14, 30), scroll.turned(ProfileTab.REPLIES, ProfileTab.POSTS, ScrollSpot(5, 0)))
        assertEquals(ScrollSpot(5, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.REPLIES, ScrollSpot(20, 0)))
    }

    @Test
    fun aTabLeftAtTheHeaderOpensUnderTheRowWhileItSticks() {
        val scroll = ProfileTabScroll()
        assertNull(scroll.turned(ProfileTab.MEDIA, ProfileTab.POSTS, ScrollSpot(0, 0)))
        assertEquals(ScrollSpot(1, 0), scroll.turned(ProfileTab.POSTS, ProfileTab.MEDIA, ScrollSpot(9, 0)))
    }

    @Test
    fun theSameTabAgainStays() {
        assertNull(ProfileTabScroll().turned(ProfileTab.POSTS, ProfileTab.POSTS, ScrollSpot(9, 0)))
    }
}
