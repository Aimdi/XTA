package com.aimdi.xtanative.ui.trends

import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrendPlaces

/** A row of the place picker: a place to pick, or a country that opens onto its places. */
sealed interface PlaceRow {
    val key: String

    data class Place(val place: XTrendPlace) : PlaceRow {
        override val key: String get() = "place-" + place.woeid
    }

    data class Country(val name: String, val places: List<XTrendPlace>) : PlaceRow {
        override val key: String get() = "country-$name"
    }
}

/** XTA's place picker and place tabs, on the places X has trends for. */
object TrendPlacesLogic {
    /**
     * The picker's rows, as XTA lists them: countries by name, each opening
     * onto its places by name; the places of no country stand for the world,
     * which is one row of its own.
     */
    fun rows(available: List<XTrendPlace>): List<PlaceRow> = XTrendPlace.byCountry(available).map { (country, places) ->
        if (country.isEmpty()) PlaceRow.Place(XTrendPlace.WORLDWIDE) else PlaceRow.Country(country, places)
    }

    /** Whether a place's tab offers Delete: every place but the world, which is always kept. */
    fun canRemove(place: XTrendPlace): Boolean = place.woeid != XTrendPlace.WORLDWIDE.woeid

    /** Whether [place] is the one whose trends are shown. */
    fun isShown(places: XTrendPlaces, place: XTrendPlace): Boolean = places.active.woeid == place.woeid
}
