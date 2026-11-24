package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageFindTest {
    @Test
    fun findsWhateverTheCase() {
        assertEquals(listOf(0..4, 12..16), PageFind.ranges("hello", "Hello world HELLO"))
    }

    @Test
    fun accentsCountForNothingEitherWay() {
        assertEquals(listOf(4..7), PageFind.ranges("cafe", "the Café"))
        assertEquals(listOf(0..3), PageFind.ranges("CAFÉ", "cafe"))
        assertEquals(listOf(0..3), PageFind.ranges("lodz", "Łódź"))
        assertEquals(listOf(6..9), PageFind.ranges("uber", "naïve über"), "the range is the text's as drawn")
    }

    @Test
    fun foldingKeepsEveryCharacterInItsPlace() {
        val text = "Straße Œuvre ﬁne が Ǆ"
        assertEquals(text.length, PageFind.fold(text).length)
        assertEquals('が', PageFind.fold('が'), "a script's own signs stay")
    }

    @Test
    fun matchesDoNotOverlapAndABlankQueryFindsNothing() {
        assertEquals(listOf(0..1, 2..3), PageFind.ranges("aa", "aaaaa"))
        assertTrue(PageFind.ranges("", "text").isEmpty())
        assertTrue(PageFind.ranges("  ", "a  b").isEmpty())
        assertTrue(PageFind.ranges("longer", "short").isEmpty())
    }

    @Test
    fun matchesRunInThePagesOrderWithTheirOrdinals() {
        val blocks = listOf(FindBlock("a", "one cat, two cats", 0), FindBlock("b", "no", 1), FindBlock("c", "Cat", 3))
        val found = PageFind.matches("cat", blocks)
        assertEquals(listOf("a" to 0, "a" to 1, "c" to 0), found.map { it.key to it.ordinal })
        assertEquals(listOf(0, 0, 3), found.map { it.item })
        assertEquals(13..15, found[1].range)
    }

    @Test
    fun stepsGoRoundPastEitherEnd() {
        assertEquals(1, PageFind.step(0, 3, 1))
        assertEquals(0, PageFind.step(2, 3, 1))
        assertEquals(2, PageFind.step(0, 3, -1))
        assertEquals(-1, PageFind.step(0, 0, 1), "none to go to")
        assertEquals(0, PageFind.step(-1, 3, 1), "from none, the first")
        assertEquals(2, PageFind.step(-1, 3, -1), "from none backwards, the last")
        assertEquals(0, PageFind.step(7, 3, 1), "past the matches left after the page changed")
    }

    @Test
    fun startsAtTheFirstMatchFromWhereTheListIs() {
        val found = PageFind.matches("x", listOf(FindBlock("a", "x", 0), FindBlock("b", "x", 4), FindBlock("c", "x", 9)))
        assertEquals(1, PageFind.startAt(found, 3))
        assertEquals(0, PageFind.startAt(found, 0))
        assertEquals(0, PageFind.startAt(found, 12), "none below: the first, as a browser wraps")
        assertEquals(0, PageFind.startAt(found, null), "a page that scrolls as a whole")
        assertEquals(-1, PageFind.startAt(emptyList(), 0))
    }

    @Test
    fun countsFromOne() {
        assertEquals("3 / 12", PageFind.count(2, 12))
        assertNull(PageFind.count(-1, 0))
        assertNull(PageFind.count(5, 3))
    }
}
