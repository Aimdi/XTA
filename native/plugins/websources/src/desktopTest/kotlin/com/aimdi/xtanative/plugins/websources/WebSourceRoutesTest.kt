package com.aimdi.xtanative.plugins.websources

import com.aimdi.xtanative.networks.RssItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSourceRoutesTest {
    private val reading = RssItem(id = "lobsters:abc", title = "Some words & more", feedId = "websources:lobsters", feedTitle = "Lobsters", link = "https://lobste.rs/s/abc")

    @Test
    fun aRouteKeepsWhatItsScreenNeeds() {
        assertEquals(WebSourceRoute.Source("lobsters", "hottest"), WebSourceRoutes.parse(WebSourceRoutes.source("lobsters", "hottest")))
        assertEquals(WebSourceRoute.Source("lobsters", null), WebSourceRoutes.parse(WebSourceRoutes.source("lobsters")))
        assertEquals(WebSourceRoute.Search("lobsters"), WebSourceRoutes.parse(WebSourceRoutes.search("lobsters")))
        assertEquals(WebSourceRoute.Reader("lobsters", reading.id, reading.title, reading.link), WebSourceRoutes.parse(WebSourceRoutes.reader("lobsters", reading)))
        assertEquals(WebSourceRoute.Add, WebSourceRoutes.parse(WebSourceRoutes.ADD))
    }

    @Test
    fun aRouteWithoutWhatItNamesOpensNothing() {
        assertNull(WebSourceRoutes.parse("reader?key=x"))
        assertNull(WebSourceRoutes.parse("reader?source=lobsters"))
        assertNull(WebSourceRoutes.parse("source?list=hottest"))
        assertNull(WebSourceRoutes.parse("elsewhere"))
    }

    @Test
    fun theReadersStandInIsWhatTheRouteKept() {
        val route = WebSourceRoutes.parse(WebSourceRoutes.reader("lobsters", reading)) as WebSourceRoute.Reader
        assertEquals(RssItem(id = reading.id, title = reading.title, feedId = reading.feedId, feedTitle = "lobsters", link = reading.link), route.stub(null))
    }

    @Test
    fun theRoutesAreWrittenAsBefore() {
        assertEquals("source?id=lobsters&list=hottest", WebSourceRoutes.source("lobsters", "hottest"), "a screen kept on the back stack opens again after an update")
        assertEquals("reader?source=lobsters&key=lobsters%3Aabc&title=Some+words+%26+more&link=https%3A%2F%2Flobste.rs%2Fs%2Fabc", WebSourceRoutes.reader("lobsters", reading))
    }
}
