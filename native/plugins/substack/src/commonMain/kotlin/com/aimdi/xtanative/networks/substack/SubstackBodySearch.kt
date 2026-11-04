package com.aimdi.xtanative.networks.substack

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Full-text search over the bodies of posts this device keeps, as XTA's
 * SubstackBodySearchStore: nothing is fetched. [bodyOf] gives a post's kept
 * body, or null when the device has none (XTA Native's own storage answers it).
 * Each body is read and turned into text once; a post found without a body is
 * asked again only after the search is cleared.
 */
class SubstackBodySearch(private val bodyOf: suspend (SubstackPost) -> String?) {
    private class Text(val text: String, val lower: String)

    private val texts = HashMap<String, Text>()
    private val missing = HashSet<String>()
    private val lock = Mutex()

    /** For each of [posts] whose body holds [query], the passage around the match, by post key. Under two letters, nothing. */
    suspend fun search(query: String, posts: List<SubstackPost>): Map<String, String> = lock.withLock {
        val q = query.trim().lowercase()
        if (q.length < 2) {
            missing.clear()
            return@withLock emptyMap()
        }
        for (post in posts) {
            if (post.key in texts || post.key in missing) continue
            val body = bodyOf(post)
            if (body.isNullOrEmpty()) missing += post.key else texts[post.key] = bodyText(body).let { Text(it, it.lowercase()) }
        }
        posts.mapNotNull { post -> texts[post.key]?.let { kept -> snippet(kept.text, kept.lower, q)?.let { post.key to it } } }.toMap()
    }

    companion object {
        /** The plain text of a post body, as the search reads it. */
        fun bodyText(bodyHtml: String): String = SubstackHtml.plainTextOf(SubstackHtml.fragment(bodyHtml))

        /** The words around the first match of [query] in [text], marked where cut; null without a match. */
        fun snippet(text: String, lowerText: String, query: String, around: Int = 60): String? {
            val at = lowerText.indexOf(query)
            if (at < 0) return null
            val start = (at - around).coerceIn(0, text.length)
            val end = (at + query.length + around).coerceIn(0, text.length)
            return "${if (start > 0) "…" else ""}${text.substring(start, end).trim()}${if (end < text.length) "…" else ""}"
        }
    }
}
