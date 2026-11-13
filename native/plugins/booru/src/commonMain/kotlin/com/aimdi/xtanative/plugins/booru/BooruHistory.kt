package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruQuery
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The searches run, newest first, as XTA keeps them (plugin_search_history.dart): one entry per set of tags, at most [CAP]. */
internal object BooruHistory {
    const val CAP = 20

    /** [query] first, in place of an older search of the same tags in any order. */
    fun remembered(history: List<String>, query: String): List<String> {
        val value = query.trim()
        if (value.isEmpty()) return history
        val id = BooruQuery.tagSetKey(value)
        return (listOf(value) + history.filter { BooruQuery.tagSetKey(it) != id }).take(CAP)
    }

    fun forgotten(history: List<String>, query: String): List<String> = history.filterNot { it == query }
}

/**
 * What the reader has seen of Following: each site's newest post id there
 * (booru_visits.dart). Ids rise with every upload on all four engines, so a
 * post with a higher id than any seen on the last visit came in since.
 */
internal object BooruVisits {
    /** Each site's newest post id seen on Following, by host, from what XTA keeps. */
    fun seen(text: String): Map<String, Long> {
        val root = J.parse(text)
        return root.keys.mapNotNull { host -> root[host].long?.let { host to it } }.toMap()
    }

    /** [seen] with [posts] noted: each site keeps the newest id seen on it. */
    fun marked(seen: Map<String, Long>, posts: Iterable<BooruPost>): Map<String, Long> = posts.fold(seen) { kept, post ->
        val id = post.id.toLongOrNull()
        if (id != null && id > (kept[post.host] ?: -1)) kept + (post.host to id) else kept
    }

    /** Whether [post] came in after the newest post [since] holds for its site. Nothing is new on a site not visited before. */
    fun isNew(since: Map<String, Long>, post: BooruPost): Boolean {
        val seen = since[post.host] ?: return false
        val id = post.id.toLongOrNull() ?: return false
        return id > seen
    }

    fun encode(seen: Map<String, Long>): String = JsonObject(seen.mapValues { JsonPrimitive(it.value) }).toString()
}
