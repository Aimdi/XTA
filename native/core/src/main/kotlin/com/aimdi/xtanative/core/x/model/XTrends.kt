package com.aimdi.xtanative.core.x.model

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One trend: what a tap searches for is [query], already decoded; [volume] is X's post count, when it says. */
data class XTrend(val name: String, val query: String, val volume: Long? = null, val url: String? = null, val promoted: Boolean = false)

/** The trends of one place, as X ranked them at [asOf]. */
data class XTrends(
    val trends: List<XTrend>,
    val asOf: Instant? = null,
    val createdAt: Instant? = null,
    val places: List<XTrendPlace> = emptyList(),
)

/**
 * A place X has trends for, by its Yahoo! Where On Earth id ([woeid]); 1 is
 * the whole world. [parentId] 1 marks a country.
 */
data class XTrendPlace(
    val name: String,
    val woeid: Long,
    val country: String? = null,
    val countryCode: String? = null,
    val parentId: Long? = null,
    val url: String? = null,
    val placeTypeCode: Int? = null,
    val placeTypeName: String? = null,
) {
    val isCountry: Boolean get() = parentId == 1L

    /** The shape XTA keeps a place in (dart_twitter_api's TrendLocation), so a backup reads back. */
    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("woeid", woeid)
        put("country", country)
        put("countryCode", countryCode)
        put("parentid", parentId)
        put("url", url)
        if (placeTypeCode != null || placeTypeName != null) {
            put("placeType", buildJsonObject { put("code", placeTypeCode); put("name", placeTypeName) })
        }
    }

    companion object {
        val WORLDWIDE = XTrendPlace("Worldwide", 1)

        /** A place from X's `trends/available` or from XTA's own JSON, which share their keys. */
        fun fromJson(json: J): XTrendPlace? {
            val woeid = json["woeid"].long ?: return null
            return XTrendPlace(
                name = json["name"].string.orEmpty(),
                woeid = woeid,
                country = json["country"].string,
                countryCode = json["countryCode"].string,
                parentId = json["parentid"].long,
                url = json["url"].string,
                placeTypeCode = json["placeType"]["code"].int,
                placeTypeName = json["placeType"]["name"].string,
            )
        }

        /**
         * [places] by country, as XTA's place picker lists them: countries by
         * name, each country's places by name; the places of no country (the
         * world) under an empty name.
         */
        fun byCountry(places: List<XTrendPlace>): List<Pair<String, List<XTrendPlace>>> =
            places.sortedBy { it.name }.groupBy { it.country.orEmpty() }.toSortedMap().toList()
    }
}

/**
 * The places whose trends the reader keeps, and the one shown, as XTA's
 * UserTrendLocations holds them: the world is always there and cannot be
 * removed; removing a place shows the one before it.
 */
data class XTrendPlaces(val active: XTrendPlace = XTrendPlace.WORLDWIDE, val places: List<XTrendPlace> = listOf(XTrendPlace.WORLDWIDE)) {
    /** Where [active] is among [places]; the first when it is not there. */
    val activeIndex: Int get() = places.indexOfFirst { it.woeid == active.woeid }.coerceAtLeast(0)

    /** Shows [place], keeping it among the places. */
    fun added(place: XTrendPlace): XTrendPlaces =
        XTrendPlaces(place, if (places.any { it.woeid == place.woeid }) places else places + place)

    fun shown(place: XTrendPlace): XTrendPlaces = copy(active = place)

    fun removed(place: XTrendPlace): XTrendPlaces {
        val index = places.indexOfFirst { it.woeid == place.woeid }
        if (index <= 0 || places[index].woeid == XTrendPlace.WORLDWIDE.woeid) return this
        return XTrendPlaces(places[index - 1], places.filterIndexed { i, _ -> i != index })
    }

    /** The JSON XTA stores these in. */
    fun encode(): String = buildJsonObject {
        put("active", active.toJson())
        put("locations", JsonArray(places.map { it.toJson() }))
    }.toString()

    companion object {
        /** What XTA stored; the world alone when [text] is unreadable. */
        fun decode(text: String): XTrendPlaces {
            val json = J.parse(text)
            val places = json["locations"].list.mapNotNull(XTrendPlace::fromJson)
            val active = XTrendPlace.fromJson(json["active"]) ?: return XTrendPlaces()
            return XTrendPlaces(active, places.ifEmpty { listOf(XTrendPlace.WORLDWIDE) })
        }
    }
}
