package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One of Meta's trending topics, as threads.com's search page lists them
 * under "Trending now": its [title], the search it opens ([terms]), and the
 * lines Meta writes under it when it writes them ([context], such as a
 * count of threads, and [summary], Meta's own few words on what is said).
 */
data class ThreadsTrend(
    val title: String,
    val terms: ThreadsSearchTerms,
    val context: String? = null,
    val summary: String? = null,
)

/**
 * Reads the trending topics threads.com's empty search page preloads for a
 * guest (`BarcelonaSearchTrendingTopicsSectionQuery`, `result.data.trends`,
 * seen by CI's discovery probe; the items' own keys were not), so each item
 * is read under every key it might use, and an item without a name is left
 * out. Nothing here throws: a page in another shape has no trends.
 */
object ThreadsTrends {
    /** How many trends are kept: threads.com shows a short list. */
    const val MAX = 20

    private const val MARKER = "\"trends\""
    private const val LONGEST_TITLE = 120
    private const val LONGEST_LINE = 400
    private val NAME_KEYS = listOf("title", "name", "display_name", "trend_name", "topic_name", "headline", "query_text", "search_query", "query", "keyword", "text", "label")
    private val QUERY_KEYS = listOf("search_query", "query_text", "query", "search_term", "serp_query", "keyword")
    private val LINK_KEYS = listOf("search_url", "url", "link", "uri", "destination_url", "permalink")
    private val CONTEXT_KEYS = listOf("social_context", "subtitle", "context", "context_text", "secondary_text", "formatted_post_count", "post_count_text", "formatted_count", "category")
    private val SUMMARY_KEYS = listOf("summary", "trend_summary", "ai_summary", "summary_text", "description", "snippet")
    private val LIST_KEYS = listOf("edges", "trends", "items", "nodes", "topics", "results", "trending_topics")
    private val WRAPPER_KEYS = listOf("node", "trend", "topic", "item")
    private val spaces = Regex("\\s+")

    /** The trends a threads.com page's scripts carry, each once, at most [MAX]; empty when there are none. */
    fun read(html: String): List<ThreadsTrend> =
        ThreadsScripts.embedded(html, MARKER).mapNotNull(ThreadsJson::decode).let(::fromRoots)

    /** The trends under `data.trends` in [roots] (a page's scripts, or a GraphQL answer), each once, at most [MAX]. */
    fun fromRoots(roots: List<JsonElement>): List<ThreadsTrend> = roots.asSequence()
        .flatMap { lists(it, 0) }
        .flatMap { items(it).asSequence() }
        .mapNotNull(::trend)
        .distinctBy { it.terms.text.lowercase() }
        .take(MAX)
        .toList()

    /** The trend [terms] opens among [trends], so its search page can say what Meta wrote of it. */
    fun matching(trends: List<ThreadsTrend>, terms: ThreadsSearchTerms): ThreadsTrend? =
        trends.firstOrNull { it.terms.tag == terms.tag && it.terms.query.equals(terms.query.trim(), ignoreCase = true) }

    /** Every `data.trends` value under [node]. */
    private fun lists(node: JsonElement, depth: Int): Sequence<JsonElement> = when {
        depth > ThreadsJson.MAX_DEPTH -> emptySequence()
        node is JsonArray -> node.asSequence().flatMap { lists(it, depth + 1) }
        node is JsonObject -> {
            val own = ((node["data"] as? JsonObject)?.get("trends"))?.let { sequenceOf(it) } ?: emptySequence()
            own + node.values.asSequence().flatMap { lists(it, depth + 1) }
        }
        else -> emptySequence()
    }

    /** The items a `trends` value holds: a list, a connection's edges, or the first list it names. */
    private fun items(value: JsonElement): List<JsonObject> {
        val list = when (value) {
            is JsonArray -> value
            is JsonObject -> LIST_KEYS.firstNotNullOfOrNull { value[it] as? JsonArray } ?: value.values.firstOrNull { it is JsonArray } as? JsonArray
            else -> null
        }
        return list.orEmpty().mapNotNull { (it as? JsonObject)?.let(::unwrapped) }
    }

    private fun unwrapped(item: JsonObject): JsonObject =
        WRAPPER_KEYS.firstNotNullOfOrNull { key -> (item[key] as? JsonObject)?.takeIf { it.isNotEmpty() } }?.let(::unwrapped) ?: item

    private fun trend(item: JsonObject): ThreadsTrend? {
        val j = J(item)
        val name = text(j, NAME_KEYS, LONGEST_TITLE) ?: return null
        val linked = LINK_KEYS.firstNotNullOfOrNull { j[it].string }?.let(::searchOf)
        val query = text(j, QUERY_KEYS, LONGEST_TITLE)
        val tagged = j["serp_type"].string == "tags" || name.startsWith("#") || query?.startsWith("#") == true
        val terms = linked ?: ThreadsSearchTerms((query ?: name).removePrefix("#").trim(), tag = tagged)
        if (terms.query.isEmpty()) return null
        val context = text(j, CONTEXT_KEYS, LONGEST_LINE)
        return ThreadsTrend(
            title = name.removePrefix("#").trim().ifEmpty { terms.query },
            terms = terms,
            context = context,
            summary = text(j, SUMMARY_KEYS, LONGEST_LINE)?.takeIf { it != context },
        )
    }

    /** The search a threads.com link opens; null for any other link. */
    private fun searchOf(link: String): ThreadsSearchTerms? {
        val absolute = if (link.startsWith("/")) ThreadsLinks.WEB + link else link
        return (ThreadsLinks.parse(absolute) as? ThreadsLink.Search)?.terms?.takeIf { it.query.isNotBlank() }
    }

    /** The first of [keys] holding text (a string, or an object's `text`), its spaces folded; null when none does or it runs past [longest]. */
    private fun text(item: J, keys: List<String>, longest: Int): String? = keys.firstNotNullOfOrNull { key ->
        val value = item[key]
        (value.string ?: value["text"].string ?: value["title"].string)
            ?.replace(spaces, " ")?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= longest }
    }
}
