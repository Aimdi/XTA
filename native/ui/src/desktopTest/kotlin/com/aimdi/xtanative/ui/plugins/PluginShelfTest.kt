package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PluginShelfTest {
    @Test
    fun aScreenTakesWhatTheLastOneHandedItByItsKey() {
        val shelf = PluginShelf()
        val key = shelf.put("a gallery")

        assertEquals("a gallery", shelf.take<String>(key))
        assertNull(shelf.take<Int>(key), "something of another kind is not taken")
        assertNull(shelf.get(null))
    }

    @Test
    fun onlyTheNewestAreKept() {
        val shelf = PluginShelf(capacity = 2)
        val first = shelf.put(1)
        val second = shelf.put(2)
        val third = shelf.put(3)

        assertNull(shelf.get(first))
        assertEquals(2, shelf.get(second))
        assertEquals(3, shelf.get(third))
    }

    @Test
    fun anotherRunNeverGivesTheSameKeys() {
        assertNotEquals(PluginShelf().put("a"), PluginShelf().put("a"))
    }
}
