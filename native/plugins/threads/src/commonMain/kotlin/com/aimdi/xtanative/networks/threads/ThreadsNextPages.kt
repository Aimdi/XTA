package com.aimdi.xtanative.networks.threads

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A query a page preloaded: its name (`BarcelonaProfileThreadsTabDirectQuery`), its doc id when the page names one, and its variables. */
data class ThreadsPreload(val name: String, val docId: String?, val variables: JsonObject)

/** A `page_info` a page or an answer carries: the query whose answer holds it, its key path under `data`, and whether a page follows. */
data class ThreadsPageInfo(val query: String?, val path: String, val endCursor: String?, val hasNextPage: Boolean) {
    val goesOn: Boolean get() = hasNextPage && !endCursor.isNullOrEmpty()
}

/**
 * How a list's next page is asked for, as threads.com's own client asks it:
 * the query that read the first page, by its doc id, with its variables and
 * the cursor after what was shown. Carried in a [com.aimdi.xtanative.networks.Page.next]
 * as [encoded].
 */
data class ThreadsNextPage(val query: String, val docId: String, val variables: JsonObject, val after: String, val path: String = "") {
    /** The first page's variables with the cursor after it, and a page size when the query named none. */
    val requestVariables: JsonObject
        get() = JsonObject(variables + ("after" to JsonPrimitive(after)) + if ("first" in variables) emptyMap() else mapOf("first" to JsonPrimitive(PAGE_SIZE)))

    fun encoded(): String = PREFIX + JsonObject(
        mapOf("q" to JsonPrimitive(query), "d" to JsonPrimitive(docId), "v" to variables, "a" to JsonPrimitive(after), "p" to JsonPrimitive(path)),
    ).toString()

    companion object {
        const val PAGE_SIZE = 10
        private const val PREFIX = "threads-next:"

        /** [cursor] as a next page; null when it is not one of [encoded]'s. */
        fun decode(cursor: String?): ThreadsNextPage? {
            val json = cursor?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.let(ThreadsJson::decode) as? JsonObject ?: return null
            fun text(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val query = text("q")?.ifEmpty { null } ?: return null
            val docId = text("d")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: return null
            val after = text("a")?.ifEmpty { null } ?: return null
            return ThreadsNextPage(query, docId, json["v"] as? JsonObject ?: JsonObject(emptyMap()), after, text("p").orEmpty())
        }
    }
}

/**
 * Reads where a threads.com page's lists go on: the relay queries it
 * preloaded (`adp_<Name>RelayPreloader_<n>`) with their doc ids and
 * variables, and each `page_info` their answers carry. Every field is read
 * as if it might be gone: a page that names no doc id or no cursor has no
 * next page, and the list ends where it did before.
 */
object ThreadsNextPages {
    private const val PRELOAD = "adp_"
    private val DOC_ID_KEYS = setOf("queryID", "queryId", "docID", "docId", "doc_id", "documentID")
    private val plainKey = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    private val rawDocId = Regex(""""(?:queryID|queryId|docID|docId|doc_id|documentID)"\s*:\s*"?(\d{6,})""")
    private const val RAW_WINDOW = 4_000
    private const val MAX_DEPTH = 80

    /** Every query [html] preloaded with variables, by name; a doc id the page names apart from them is filled in. */
    fun preloads(html: String): List<ThreadsPreload> {
        val found = mutableListOf<ThreadsPreload>()
        roots(html, "\"variables\"").forEach { walk(it, null, "", 0) { node, query, _ -> preloadOf(node, query)?.let(found::add) } }
        return found.map { if (it.docId == null) it.copy(docId = rawDocId(html, it.name)) else it }.distinctBy { it.name to it.docId }
    }

    /** Every `page_info` [html]'s preloaded answers carry, in page order. */
    fun pageInfos(html: String): List<ThreadsPageInfo> = roots(html, "\"page_info\"").flatMap { pageInfos(it, null) }

    /** Every `page_info` under [root], each named by the query whose answer holds it ([query] at the top). */
    fun pageInfos(root: JsonElement, query: String?): List<ThreadsPageInfo> {
        val found = mutableListOf<ThreadsPageInfo>()
        walk(root, query, "", 0) { node, name, path ->
            val info = node["page_info"] as? JsonObject ?: return@walk
            found += ThreadsPageInfo(name, dataPath("$path.page_info"), text(info["end_cursor"]), (info["has_next_page"] as? JsonPrimitive)?.booleanOrNull == true)
        }
        return found
    }

    /** The next page of the first list [html] says goes on whose query [wanted] takes; null when the page names no cursor or no doc id for it. */
    fun first(html: String, wanted: (String) -> Boolean): ThreadsNextPage? {
        val infos = pageInfos(html).filter { it.goesOn && it.query != null && wanted(it.query) }
        if (infos.isEmpty()) return null
        val preloads = preloads(html)
        return infos.firstNotNullOfOrNull { info ->
            val preload = preloads.firstOrNull { it.name == info.query && it.docId != null } ?: return@firstNotNullOfOrNull null
            ThreadsNextPage(preload.name, preload.docId!!, preload.variables, info.endCursor!!, info.path)
        }
    }

    /** The page after [asked] that [answer] says follows: its `page_info` at the same path, else the shallowest one; null at the end. */
    fun after(asked: ThreadsNextPage, answer: JsonElement): ThreadsNextPage? {
        val infos = pageInfos(answer, asked.query)
        val info = infos.firstOrNull { it.path == asked.path } ?: infos.minByOrNull { it.path.count { c -> c == '.' } } ?: return null
        val cursor = info.endCursor?.takeIf { info.goesOn && it != asked.after } ?: return null
        return asked.copy(after = cursor, path = info.path)
    }

    /**
     * A GraphQL answer's JSON objects: Meta may put `for (;;);` before it, and
     * answers a deferred query as one object per line. Empty when it is not JSON.
     */
    fun answer(body: String): List<JsonElement> {
        val text = body.trim().removePrefix("for (;;);").trim()
        if (text.startsWith("<")) return emptyList()
        ThreadsJson.decode(text)?.let { return listOf(it) }
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull(ThreadsJson::decode).toList()
    }

    private fun roots(html: String, marker: String): List<JsonElement> = ThreadsScripts.embedded(html, marker).mapNotNull(ThreadsJson::decode)

    private fun preloadOf(node: JsonObject, query: String?): ThreadsPreload? {
        val variables = node["variables"] as? JsonObject ?: return null
        val name = query ?: return null
        val docId = DOC_ID_KEYS.firstNotNullOfOrNull { key -> text(node[key])?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } }
        return ThreadsPreload(name, docId, variables)
    }

    /** A doc id named within a few thousand characters after the preloader's name, where the page keeps it apart. */
    private fun rawDocId(html: String, name: String): String? {
        var at = html.indexOf("\"$PRELOAD$name")
        while (at >= 0) {
            rawDocId.find(html.subSequence(at, minOf(html.length, at + RAW_WINDOW)))?.let { return it.groupValues[1] }
            at = html.indexOf("\"$PRELOAD$name", at + 1)
        }
        return null
    }

    /**
     * Visits every object under [node] with the preloaded query whose answer
     * or definition holds it (an `adp_` name beside it in an array or object)
     * and its key path.
     */
    private fun walk(node: JsonElement, query: String?, path: String, depth: Int, visit: (JsonObject, String?, String) -> Unit) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JsonArray -> {
                val here = node.firstNotNullOfOrNull(::preloadName) ?: query
                node.forEach { walk(it, here, "$path[]", depth + 1, visit) }
            }
            is JsonObject -> {
                val here = node.values.firstNotNullOfOrNull(::preloadName) ?: query
                visit(node, here, path)
                node.forEach { (key, value) -> walk(value, here, "$path.${keyName(key)}", depth + 1, visit) }
            }
            else -> Unit
        }
    }

    private fun preloadName(element: JsonElement): String? =
        text(element)?.takeIf { it.startsWith(PRELOAD) }?.removePrefix(PRELOAD)?.substringBefore("RelayPreloader")?.substringBefore('_')?.ifEmpty { null }

    /** [path] from its `data` on: `result.data.mediaData.page_info` is `data.mediaData.page_info`. */
    private fun dataPath(path: String): String {
        val at = path.lastIndexOf("result.data")
        return when {
            at >= 0 -> path.substring(at + "result.".length)
            path.startsWith(".data") -> path.removePrefix(".")
            else -> path.removePrefix(".")
        }
    }

    private fun keyName(key: String): String = if (plainKey.matches(key)) key else "<other>"

    private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull
}
