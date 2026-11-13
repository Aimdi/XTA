package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** The store tests of XTA's test/eh_search_test.dart, the timing fix of #376 included. */
class EhSearchTest {
    /** Answers searches with one gallery and `tagsuggest` with the fixture's tags, for the terms it [known]s (all when null). */
    private class Site : XHttp {
        val searches: MutableList<HttpUrl> = CopyOnWriteArrayList()
        val suggested: MutableList<String> = CopyOnWriteArrayList()

        @Volatile
        var known: Set<String>? = null

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            searches += url
            return XResponse(200, LIST)
        }

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse {
            val text = J.parse(body)["text"].string.orEmpty()
            suggested += text
            val hit = known?.contains(text) ?: true
            return XResponse(200, if (hit) fixture("tagsuggest.json") else """{"tags":[]}""")
        }
    }

    private val site = Site()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val remembered: MutableList<String> = CopyOnWriteArrayList()
    private val search = EhSearch(EhClient(site), scope, remember = { remembered += it }, debounceMillis = 0)

    @AfterTest
    fun close() {
        search.close()
        scope.cancel()
    }

    @Test
    fun theLongestTermTheSiteKnowsWinsAndOnlyThatSpanIsReplaced() = runBlocking<Unit> {
        site.known = setOf("big b")

        search.type("summer big b")
        waitUntil { search.state.value.suggestions.isNotEmpty() }

        assertEquals(listOf("summer big b", "big b"), site.suggested)
        assertEquals(2, search.state.value.suggestions.size)
        assertEquals("summer female:\"big breasts$\" ", search.pick(search.state.value.suggestions.first()))
        assertTrue(search.state.value.suggestions.isEmpty())
    }

    @Test
    fun searchesAreRememberedAndAChangedFilterRunsTheSearchAgainUnawaited() = runBlocking<Unit> {
        search.search("summer")
        assertEquals(listOf("summer"), remembered)
        assertEquals("Summer", search.results!!.state.value.galleries.single().title)
        assertEquals("summer", search.state.value.query)

        search.setMinRating(4)
        // The changed filter runs the search again without being awaited: wait for its request, never a fixed sleep.
        waitUntil { site.searches.size == 2 }
        assertEquals("4", site.searches.last().queryParameter("f_srdd"))

        search.setMinRating(4)
        assertEquals(0, search.state.value.filters.minRating, "choosing the chosen rating again clears it")
    }

    @Test
    fun theLastCategoryCannotBeSwitchedOff() {
        EhCategory.entries.drop(1).forEach(search::toggleCategory)
        assertEquals(setOf(EhCategory.entries.first()), search.state.value.filters.categories)

        search.toggleCategory(EhCategory.entries.first())
        assertEquals(setOf(EhCategory.entries.first()), search.state.value.filters.categories)
        assertTrue(site.searches.isEmpty(), "no search is shown, so none runs again")
    }

    @Test
    fun aSearchForNothingRunsNothingAndAFinishedTermAsksForNoTags() = runBlocking<Unit> {
        search.search("   ")
        search.type("female:\"big breasts$\"")

        assertNull(search.state.value.query)
        assertNull(search.results)
        assertTrue(site.searches.isEmpty())
        assertTrue(site.suggested.isEmpty())
    }

    private companion object {
        const val LIST = """<table><tr><td class="gl1c glcat"><div class="cn ct2">Manga</div></td>""" +
            """<td><a href="https://e-hentai.org/g/1/abc/"><div class="glink">Summer</div></a></td></tr></table>"""
    }
}
