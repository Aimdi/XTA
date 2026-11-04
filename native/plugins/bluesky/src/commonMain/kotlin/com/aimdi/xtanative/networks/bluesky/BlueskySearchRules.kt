package com.aimdi.xtanative.networks.bluesky

/** What XTA's Bluesky search looks for: people, or posts. */
enum class BlueskySearchKind { PEOPLE, POSTS }

/** How a post search is narrowed (bluesky_search_store.dart): latest or top, by one author, with one tag. */
data class BlueskyPostFilters(val top: Boolean = false, val author: String = "", val tag: String = "") : java.io.Serializable {
    val sort: String get() = if (top) "top" else "latest"
}

/** The search's own rules, as XTA's search screen applies them. */
object BlueskySearchRules {
    /** A lone `#tag`: searched as that exact tag. */
    internal val hashtag = Regex("#[^$JS_SPACE#]+")

    /** XTA keeps this many recent searches. */
    const val HISTORY_CAP = 20

    /** Where a query opens: a post link or a lone #tag reads posts, anything else people first, as XTA's search does. */
    fun kindFor(query: String): BlueskySearchKind {
        val text = query.trim()
        return if (BlueskyUrls.searchPostTarget(text) != null || hashtag.matches(text)) BlueskySearchKind.POSTS else BlueskySearchKind.PEOPLE
    }

    /** The author a filter names: empty for none, null when [input] names no account (a handle, a DID, a profile link). */
    fun author(input: String): String? = input.trim().let { if (it.isEmpty()) "" else BlueskyUrls.searchActor(it) }

    /** The tag a filter names, without its leading #s. */
    fun tag(input: String): String = input.trim().trimStart('#').trim()

    /** [history] with [query] first, once, at most [HISTORY_CAP]. */
    fun remembered(history: List<String>, query: String): List<String> {
        val text = query.trim().ifEmpty { return history }
        return (listOf(text) + history.filter { it != text }).take(HISTORY_CAP)
    }
}
