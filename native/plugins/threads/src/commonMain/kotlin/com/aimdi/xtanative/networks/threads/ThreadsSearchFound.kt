package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup

/**
 * A topic posts are filed under: its [name] without the `#`, and the mark
 * Meta gives its community (`tag_header.community_emoji`,
 * `community_favicon_url`) when the page carried one.
 */
data class ThreadsTopic(val name: String, val emoji: String? = null, val iconUrl: String? = null) {
    val key: String get() = name.lowercase()
}

/** What one public search page found: its posts, and the topics its posts are filed under. */
data class ThreadsSearchFound(val posts: List<ThreadsPost>, val topics: List<ThreadsTopic> = emptyList()) {
    /** The topic [name] names, with its mark when the page gave one. */
    fun topic(name: String): ThreadsTopic {
        val key = name.removePrefix("#").trim().lowercase()
        return topics.firstOrNull { it.key == key } ?: ThreadsTopic(name.removePrefix("#").trim())
    }

    /**
     * The topics to offer for [query]: the words themselves as a topic when
     * no post is filed under them, then the topics the posts are filed under,
     * those the words name first; a topic's own page leaves itself out.
     */
    fun related(query: String, tag: Boolean): List<ThreadsTopic> {
        val key = query.removePrefix("#").trim().lowercase()
        val words = key.split(' ').filter { it.isNotBlank() }
        val named = { topic: ThreadsTopic -> words.isNotEmpty() && words.all { topic.key.contains(it) } }
        val listed = topics.filterNot { tag && it.key == key }.sortedWith(compareByDescending<ThreadsTopic> { it.key == key }.thenByDescending(named))
        val own = if (tag || key.isEmpty() || topics.any { it.key == key }) emptyList() else listOf(ThreadsTopic(query.removePrefix("#").trim()))
        return own + listed
    }
}

/**
 * Reads a public search page past its posts, from what the posts already
 * carry: the accounts that wrote them and the topics they are filed under. A
 * guest has no people or topic search of its own; these are found among the
 * results, so nothing more is asked of Meta.
 */
object ThreadsSearchReading {
    private const val TOPIC_SCRIPTS_MAX = 64

    fun read(body: String, query: String, tag: Boolean): ThreadsSearchFound {
        val posts = ThreadsPages.searchResults(body, query, tag).distinctBy { it.id }
        return ThreadsSearchFound(posts, topics(posts, marks(body)))
    }

    /** Each topic [posts] are filed under, once, in the order first met, with the mark [marks] has for it. */
    fun topics(posts: List<ThreadsPost>, marks: Map<String, ThreadsTopic> = emptyMap()): List<ThreadsTopic> =
        posts.flatMap { listOfNotNull(it.topicTag, it.quoted?.topicTag) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .map { name -> marks[name.lowercase()]?.copy(name = name) ?: ThreadsTopic(name) }

    /** Every `tag_header` a page's scripts carry, by its name in lower case; one with a mark wins over one without. */
    fun marks(body: String): Map<String, ThreadsTopic> {
        val found = LinkedHashMap<String, ThreadsTopic>()
        Jsoup.parse(body).select("script[data-sjs]").asSequence()
            .map { it.data() }
            .filter { it.contains("tag_header") }
            .take(TOPIC_SCRIPTS_MAX)
            .mapNotNull(ThreadsJson::decode)
            .forEach { collectMarks(it, found) }
        return found
    }

    /**
     * The accounts that wrote [posts] (and the posts they quote), each once:
     * those whose handle or name holds every word of [query] first, then by
     * how many of the posts are theirs.
     */
    fun people(posts: List<ThreadsPost>, query: String): List<ThreadsProfile> {
        val words = query.removePrefix("#").removePrefix("@").lowercase().split(' ').filter { it.isNotBlank() }
        val written = posts.flatMap { listOfNotNull(it, it.quoted) }.filter { it.handle.isNotBlank() }
        val counts = written.groupingBy { it.handle.lowercase() }.eachCount()
        return written.distinctBy { it.handle.lowercase() }
            .sortedWith(compareByDescending<ThreadsPost> { matches(it, words) }.thenByDescending { counts[it.handle.lowercase()] ?: 0 })
            .map(::authorOf)
    }

    /** Whether [post]'s author is named by every one of [words]. */
    fun matches(post: ThreadsPost, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        val names = (post.handle + " " + post.authorName).lowercase()
        return words.all { names.contains(it) }
    }

    private fun authorOf(post: ThreadsPost) = ThreadsProfile(
        pk = "",
        id = "",
        username = post.handle.lowercase(),
        fullName = post.authorName,
        isVerified = post.isVerified,
        profilePicUrl = post.avatarUrl.orEmpty(),
    )

    private fun collectMarks(node: JsonElement, out: MutableMap<String, ThreadsTopic>) {
        when (node) {
            is JsonArray -> node.forEach { collectMarks(it, out) }
            is JsonObject -> {
                (node["tag_header"] as? JsonObject)?.let { mark(J(it)) }?.let { topic ->
                    val known = out[topic.key]
                    if (known == null || (known.emoji == null && known.iconUrl == null)) out[topic.key] = topic
                }
                node.values.forEach { collectMarks(it, out) }
            }
            else -> Unit
        }
    }

    private fun mark(header: J): ThreadsTopic? {
        val name = (header["display_name"].string ?: header["name"].string)?.trim()?.removePrefix("#")?.trim()
        if (name.isNullOrEmpty()) return null
        val emoji = header["community_emoji"].string?.trim()?.ifEmpty { null }
        val icon = header["community_favicon_url"].string?.trim()?.takeIf { it.startsWith("https://") }
        return ThreadsTopic(name, emoji, icon)
    }
}
