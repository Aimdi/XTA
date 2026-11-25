package com.aimdi.xtanative.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class PastEmptiedTest {
    /** Pages by the cursor that asks for them, and the cursors asked. */
    private class Pages(vararg pages: Pair<String?, Narrowed<String>>) {
        private val byCursor = pages.toMap()
        val asked = mutableListOf<String?>()

        suspend fun read(cursor: String?): Narrowed<String> {
            asked += cursor
            return byCursor.getValue(cursor)
        }
    }

    @Test
    fun aPageWithPostsLeftIsGivenAsItIs() = runBlocking {
        val pages = Pages(null to Narrowed(listOf("a"), read = 3, next = "c1"))
        assertEquals(Page(listOf("a"), "c1"), PastEmptied.read(null, pages::read))
        assertEquals(listOf<String?>(null), pages.asked)
    }

    @Test
    fun aPageTheFilterEmptiedIsPassedOver() = runBlocking {
        val pages = Pages(
            "c0" to Narrowed(emptyList(), read = 5, next = "c1"),
            "c1" to Narrowed(listOf("b"), read = 5, next = "c2"),
        )
        assertEquals(Page(listOf("b"), "c2"), PastEmptied.read("c0", pages::read))
        assertEquals(listOf<String?>("c0", "c1"), pages.asked)
    }

    @Test
    fun aPageThatCameEmptyOrLastEndsTheList() = runBlocking {
        val empty = Pages(null to Narrowed(emptyList(), read = 0, next = "c1"))
        assertEquals(Page(emptyList<String>(), "c1"), PastEmptied.read(null, empty::read))
        val last = Pages(null to Narrowed(emptyList(), read = 4, next = null))
        assertEquals(Page(emptyList<String>(), null), PastEmptied.read(null, last::read))
    }

    @Test
    fun aFilterThatHidesEverythingStillStops() = runBlocking {
        val asked = mutableListOf<String?>()
        val page = PastEmptied.read<String>(null) { cursor ->
            asked += cursor
            Narrowed(emptyList(), read = 5, next = (cursor ?: "c") + "x")
        }
        assertEquals(PastEmptied.HOPS, asked.size)
        assertEquals(emptyList(), page.items)
    }
}
