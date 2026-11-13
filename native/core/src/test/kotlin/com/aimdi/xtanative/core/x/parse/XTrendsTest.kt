package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrendPlaces
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** X's trends and the places the reader keeps for them, as XTA's trends_model.dart holds them. */
class XTrendsTest {
    private val paris = XTrendPlace("Paris", 615702, "France", "FR", 23424819)
    private val france = XTrendPlace("France", 23424819, "France", "FR", 1)

    @Test
    fun theWorldIsTheFirstPlaceAndShown() {
        val places = XTrendPlaces()
        assertEquals(XTrendPlace.WORLDWIDE, places.active)
        assertEquals(listOf(XTrendPlace.WORLDWIDE), places.places)
        assertEquals(0, places.activeIndex)
    }

    @Test
    fun aPlaceAddedIsShownAndKeptOnce() {
        val places = XTrendPlaces().added(paris).added(france).added(paris)
        assertEquals(paris, places.active)
        assertEquals(listOf(XTrendPlace.WORLDWIDE, paris, france), places.places)
        assertEquals(1, places.activeIndex)
        assertEquals(france, places.shown(france).active)
    }

    @Test
    fun removingAPlaceShowsTheOneBeforeItAndTheWorldStays() {
        val places = XTrendPlaces().added(paris).added(france).shown(paris)
        val removed = places.removed(france)
        assertEquals(listOf(XTrendPlace.WORLDWIDE, paris), removed.places)
        assertEquals(paris, removed.active, "the one before the removed place is shown, as XTA does")
        assertEquals(places.removed(XTrendPlace.WORLDWIDE), places, "the world cannot be removed")
        assertEquals(places.removed(XTrendPlace("Nowhere", 9)), places)
    }

    @Test
    fun thePlacesReadBackFromTheJsonXtaKeepsThemIn() {
        val places = XTrendPlaces().added(paris.copy(placeTypeCode = 7, placeTypeName = "Town"))
        assertEquals(places, XTrendPlaces.decode(places.encode()))
        val xta = """{"active":{"name":"Paris","woeid":615702,"country":"France","countryCode":"FR","parentid":23424819,"url":null,"placeType":{"code":7,"name":"Town"}},
            "locations":[{"name":"Worldwide","woeid":1},{"name":"Paris","woeid":615702,"country":"France","countryCode":"FR","parentid":23424819,"url":null,"placeType":{"code":7,"name":"Town"}}]}"""
        assertEquals(615702L, XTrendPlaces.decode(xta).active.woeid)
        assertEquals(2, XTrendPlaces.decode(xta).places.size)
        assertEquals(XTrendPlaces(), XTrendPlaces.decode("garbage"))
    }

    @Test
    fun placesAreListedByCountryAndByName() {
        val grouped = XTrendPlace.byCountry(listOf(paris, XTrendPlace.WORLDWIDE.copy(country = ""), france, XTrendPlace("Lyon", 609125, "France")))
        assertEquals(listOf("", "France"), grouped.map { it.first })
        assertEquals(listOf("France", "Lyon", "Paris"), grouped.last().second.map { it.name })
        assertTrue(france.isCountry && !paris.isCountry)
    }

    @Test
    fun trendsAreReadWithTheirQueriesDecoded() {
        val trends = XTrendsParser.trends(
            J.parse(
                """[{"trends":[{"name":"#Flutter","query":"%23Flutter","tweet_volume":12000,"promoted_content":null},{"name":"Broken","query":"%E0%A4%A"},{"query":"no name"}],
                    "as_of":"2026-10-30T12:00:00Z","created_at":"Fri Oct 30 11:55:00 +0000 2026"}]""",
            ),
        )
        assertEquals(listOf("#Flutter", "%E0%A4%A"), trends.trends.map { it.query }, "a query that does not decode is searched as sent")
        assertEquals(Instant.parse("2026-10-30T12:00:00Z"), trends.asOf)
        assertEquals(Instant.parse("2026-10-30T11:55:00Z"), trends.createdAt)
        assertNull(trends.trends.last().volume)
        assertTrue(XTrendsParser.trends(J.parse("""{"errors":[]}""")).trends.isEmpty())
    }
}
