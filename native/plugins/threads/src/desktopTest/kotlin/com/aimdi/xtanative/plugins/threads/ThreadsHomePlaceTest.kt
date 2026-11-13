package com.aimdi.xtanative.plugins.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadsHomePlaceTest {
    @Test
    fun homeKeepsThePostTheReaderStoppedAtAndReadsAnythingElseAsTheTop() {
        val place = ThreadsPlace("42", 120)
        assertEquals(place, ThreadsHomePlace.decode(ThreadsHomePlace.encode(place)))
        assertEquals(ThreadsPlace(), ThreadsHomePlace.decode(ThreadsHomePlace.encode(ThreadsPlace())))
        assertEquals(ThreadsPlace(), ThreadsHomePlace.decode(null))
        assertEquals(ThreadsPlace(), ThreadsHomePlace.decode("""{"v":9,"placePost":"42"}"""))
        assertEquals(ThreadsPlace(), ThreadsHomePlace.decode("<html>"))
    }

    @Test
    fun theListOpensOnThatPostPastThePeopleStripAndAtTheTopWhenItIsGone() {
        val ids = listOf("a", "b", "c", "d", "e")
        assertEquals(1, ThreadsFresh.indexOf(ids, ThreadsPlace("b"), people = true, peopleAfter = 3))
        assertEquals(4, ThreadsFresh.indexOf(ids, ThreadsPlace("d"), people = true, peopleAfter = 3))
        assertEquals(3, ThreadsFresh.indexOf(ids, ThreadsPlace("d"), people = false, peopleAfter = 3))
        assertNull(ThreadsFresh.indexOf(ids, ThreadsPlace("gone"), people = true, peopleAfter = 3))
        assertNull(ThreadsFresh.indexOf(ids, ThreadsPlace(), people = true, peopleAfter = 3))
    }

    @Test
    fun onlyPostsAReadBroughtInAboveTheReaderAreCountedAsNew() {
        assertEquals(emptySet(), ThreadsFresh.arrived(null, listOf("a", "b")), "a first read is no news")
        assertEquals(emptySet(), ThreadsFresh.arrived(emptyList(), listOf("a", "b")))
        val fresh = ThreadsFresh.arrived(listOf("c", "d"), listOf("a", "b", "c", "x", "d"))
        assertEquals(setOf("a", "b", "x"), fresh)

        val shown = listOf("a", "b", "c", "x", "d")
        assertEquals(2, ThreadsFresh.above(shown, fresh, firstShown = 2), "the reader still sees c: a and b wait above")
        assertEquals(3, ThreadsFresh.above(shown, fresh, firstShown = 4))
        assertEquals(1, ThreadsFresh.above(shown, fresh, firstShown = 1))
        assertEquals(0, ThreadsFresh.above(shown, fresh, firstShown = 0))
        assertEquals(0, ThreadsFresh.above(shown, emptySet(), firstShown = 4))
    }

    @Test
    fun forYouKeepsItsPlaceAsBefore() {
        val kept = ThreadsForYouKept(place = ThreadsPlace("7", 33))
        assertEquals(ThreadsPlace("7", 33), ThreadsForYouKept.decode(kept.encode()).place)
        assertEquals("7", postIdOf("post:7"))
        assertNull(postIdOf("people"))
    }
}
