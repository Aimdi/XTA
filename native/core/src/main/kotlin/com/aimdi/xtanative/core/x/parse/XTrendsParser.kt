package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XTrend
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrends
import java.net.URLDecoder
import java.time.Instant

/** X's trends, as `trends/available.json` and `trends/place.json` answer them. */
object XTrendsParser {
    /** The places X has trends for, by name, as XTA lists them. */
    fun places(body: J): List<XTrendPlace> = body.list.mapNotNull(XTrendPlace::fromJson).sortedBy { it.name }

    /** A place's trends: X answers a list holding one entry for the place asked. */
    fun trends(body: J): XTrends {
        val first = body[0]
        return XTrends(
            trends = first["trends"].list.mapNotNull(::trend),
            asOf = time(first["as_of"].string),
            createdAt = time(first["created_at"].string),
            places = first["locations"].list.mapNotNull(XTrendPlace::fromJson),
        )
    }

    /** A trend; its query is decoded, as XTA hands it to search. */
    private fun trend(json: J): XTrend? {
        val name = json["name"].string ?: return null
        val query = json["query"].string?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8) }.getOrDefault(it) } ?: name
        return XTrend(name, query, json["tweet_volume"].long, json["url"].string, promoted = json["promoted_content"].exists)
    }

    private fun time(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() ?: XParser.instant(it) }
}
