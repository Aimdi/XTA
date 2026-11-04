package com.aimdi.xtanative.ui.trends

import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrendPlaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrendPlacesLogicTest {
    private val world = XTrendPlace("Worldwide", 1, country = "", parentId = 0)
    private val germany = XTrendPlace("Germany", 23424829, country = "Germany", parentId = 1)
    private val berlin = XTrendPlace("Berlin", 638242, country = "Germany", parentId = 23424829)
    private val france = XTrendPlace("France", 23424819, country = "France", parentId = 1)

    @Test
    fun thePickerListsTheWorldThenCountriesByNameWithTheirPlaces() {
        val rows = TrendPlacesLogic.rows(listOf(germany, world, berlin, france))
        assertEquals(
            listOf(
                PlaceRow.Place(XTrendPlace.WORLDWIDE),
                PlaceRow.Country("France", listOf(france)),
                PlaceRow.Country("Germany", listOf(berlin, germany)),
            ),
            rows,
        )
        assertEquals(listOf("place-1", "country-France", "country-Germany"), rows.map { it.key })
    }

    @Test
    fun everyPlaceButTheWorldCanBeRemoved() {
        assertFalse(TrendPlacesLogic.canRemove(XTrendPlace.WORLDWIDE))
        assertTrue(TrendPlacesLogic.canRemove(berlin))
    }

    @Test
    fun theShownPlaceIsTheActiveOne() {
        val places = XTrendPlaces().added(berlin)
        assertTrue(TrendPlacesLogic.isShown(places, berlin))
        assertFalse(TrendPlacesLogic.isShown(places, XTrendPlace.WORLDWIDE))
    }
}
