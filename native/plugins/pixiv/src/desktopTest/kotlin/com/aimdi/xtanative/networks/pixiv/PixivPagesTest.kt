package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.networks.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class PixivPagesTest {
    /** Pages served in order, each asked-for cursor kept. */
    private class Pages(vararg pages: Page<Int>) {
        private val queue = pages.toMutableList()
        val asked = mutableListOf<String?>()

        suspend fun load(next: String?): Page<Int> {
            asked += next
            return queue.removeAt(0)
        }
    }

    @Test
    fun pagesTheFiltersEmptyAreSkippedUntilSomethingShows() = runBlocking {
        val pages = Pages(Page(listOf(1), "next-1"), Page(listOf(2), "next-2"), Page(listOf(3), null))
        val page = PixivPages.visiblePage(null, pages::load) { items -> items.filter { it == 3 } }
        assertEquals(listOf(3), page.items)
        assertEquals(listOf(null, "next-1", "next-2"), pages.asked)
    }

    @Test
    fun aListThatEndsEmptyEndsWithoutAskingFurther() = runBlocking {
        val pages = Pages(Page(listOf(1), null))
        val page = PixivPages.visiblePage("start", pages::load) { emptyList() }
        assertEquals(Page(emptyList<Int>(), null), page)
        assertEquals(listOf<String?>("start"), pages.asked)
    }

    @Test
    fun noMoreThanSixPagesAreAskedForInOneGo() = runBlocking {
        val pages = Pages(*Array(10) { Page(listOf(it), "next-${it + 1}") })
        val page = PixivPages.visiblePage(null, pages::load) { emptyList() }
        assertEquals(6, pages.asked.size)
        assertEquals("next-6", page.next, "the list can go on from where this stopped")
    }

    @Test
    fun aLaterPageIsMergedWithoutRepeats() {
        assertEquals(listOf(1, 2, 3), PixivPages.merge(listOf(1, 2), listOf(2, 3)) { it })
        assertEquals(listOf(1), PixivPages.merge(listOf(1), emptyList()) { it })
    }
}
