package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.networks.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/** When a paged list ends: a repeated cursor, or several pages in a row with nothing new, but not one page of repeats. */
class PagedItemsTest {
    private suspend fun settle() = repeat(10) { yield() }

    @Test
    fun onePageOfRepeatsDoesNotEndTheList() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val pages = mapOf(null to Page(listOf("a", "b"), "c1"), "c1" to Page(listOf("b"), "c2"), "c2" to Page(listOf("c"), null))
        val list = PagedItems(scope, key = { it }) { cursor -> pages.getValue(cursor) }
        settle()
        list.more()
        settle()
        assertEquals("c2", list.state.value.next, "a ranking that shifted under the cursor repeats a page")
        list.more()
        settle()
        assertEquals(listOf("a", "b", "c"), list.state.value.items)
        scope.cancel()
    }

    @Test
    fun pagesThatKeepBringingNothingNewEndTheList() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        var asked = 0
        val list = PagedItems(scope, key = { it }) { cursor -> asked++; Page(listOf("a"), (cursor ?: "") + "n") }
        settle()
        repeat(6) {
            list.more()
            settle()
        }
        assertNull(list.state.value.next)
        assertEquals(4, asked, "the first page and three that brought nothing")
        scope.cancel()
    }
}
