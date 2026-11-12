package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonPrimitive

class ThreadsNextPagesTest {
    private val html = fixture("paging_profile_page.html")

    @Test
    fun aPageNamesItsPreloadedQueriesWithTheirDocIdsAndVariables() {
        val preloads = ThreadsNextPages.preloads(html).associateBy { it.name }

        assertEquals("24000000000000001", preloads["BarcelonaProfileThreadsTabDirectQuery"]?.docId)
        assertEquals(setOf("userID", "__relay_internal__pv__BarcelonaIsLoggedInrelayprovider"), preloads["BarcelonaProfileThreadsTabDirectQuery"]?.variables?.keys)
        assertEquals("24000000000000002", preloads["BarcelonaProfileRepliesTabDirectQuery"]?.docId, "a doc id kept apart is found after the preloader's name")
    }

    @Test
    fun aListThatGoesOnIsAskedForWithTheCursorAfterIt() {
        val infos = ThreadsNextPages.pageInfos(html)
        assertEquals(listOf(true, false), infos.map { it.goesOn })
        assertEquals("data.mediaData.page_info", infos.first().path)

        val next = ThreadsNextPages.first(html) { "ThreadsTab" in it.query.orEmpty() }!!
        assertEquals("BarcelonaProfileThreadsTabDirectQuery", next.query)
        assertEquals(JsonPrimitive("QVFIcursor1"), next.requestVariables["after"])
        assertEquals(JsonPrimitive(ThreadsNextPage.PAGE_SIZE), next.requestVariables["first"])
        assertEquals(next, ThreadsNextPage.decode(next.encoded()))
        assertNull(ThreadsNextPages.first(html) { "RepliesTab" in it.query.orEmpty() }, "a list that ends asks for nothing")
    }

    @Test
    fun anAnswerGivesItsPostsAndTheCursorAfterThem() {
        val next = ThreadsNextPages.first(html) { true }!!
        val roots = ThreadsNextPages.answer(fixture("paging_next_answer.json"))

        assertEquals(listOf("3400000000000000102", "3400000000000000103"), ThreadsPages.chainsIn(roots).flatten().map { it.id })
        assertEquals("QVFIcursor2", roots.firstNotNullOfOrNull { ThreadsNextPages.after(next, it) }?.after)
    }

    @Test
    fun whatIsNotAnAnswerOrACursorIsNothing() {
        assertEquals(emptyList(), ThreadsNextPages.answer("<!DOCTYPE html><html></html>"))
        assertNull(ThreadsNextPage.decode("guest:1"))
        assertNull(ThreadsNextPage.decode("threads-next:{\"q\":\"x\",\"d\":\"not digits\",\"a\":\"c\"}"))
        assertNull(ThreadsNextPages.first(fixture("search_page.html")) { true })
        val sameCursor = ThreadsNextPages.first(html) { true }!!.copy(after = "QVFIcursor2")
        assertNull(ThreadsNextPages.answer(fixture("paging_next_answer.json")).firstNotNullOfOrNull { ThreadsNextPages.after(sameCursor, it) }, "a cursor that comes round again ends the list")
    }
}
