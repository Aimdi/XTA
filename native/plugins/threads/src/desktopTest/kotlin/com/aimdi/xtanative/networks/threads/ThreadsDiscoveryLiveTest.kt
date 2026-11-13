package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.IOException
import java.net.URLEncoder
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What a guest is shown of communities, reposts and discovery, for the next
 * round to read in CI's log (lines `LIVE Threads discovery …`): the keys a
 * post's `tag_header` carries and what kind of value each holds, the address
 * forms a page names for a community and what such a page answers; how a
 * repost sits in a profile's page and in its `/reposts` page; the front
 * page's queries, whether a second read brings other posts, and what the
 * empty search page offers (the shape of its trending topics, and how many
 * ThreadsTrends reads), and whether a guest's search has a profiles page. Key names, paths, kinds and counts only; the
 * only values logged are public community ids, slugs, paths and names, and
 * query names and doc ids. Nothing a post or user says, no token or cursor.
 */
class ThreadsDiscoveryLiveTest {
    @Test
    fun communitiesRepostsAndTheFrontPageAsAGuestSeesThem(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val client = ThreadsDirectClient(http)
        val bodies = SOURCES.associateWith { page(http, it, it.ifEmpty { "front" }) }
        val headers = bodies.values.filterNotNull().flatMap { tagHeaders(it) }
        communities(http, client, headers, bodies.values.filterNotNull())
        reposts(http, client, bodies["@zuck"])
        front(http, client, bodies[""])
        search(http)
    }

    private suspend fun communities(http: OkHttpXHttp, client: ThreadsDirectClient, headers: List<JsonObject>, pages: List<String>) {
        val community = headers.filter { (it["is_community"] as? JsonPrimitive)?.content == "true" }
        println("LIVE Threads discovery tag_header: ${headers.size} on posts, ${community.size} is_community=true, ${headers.count { "community_emoji" in it || "community_favicon_url" in it }} with an emoji or favicon")
        println("LIVE Threads discovery tag_header keys: ${ThreadsShape.told(ThreadsShape.keyCounts(headers), limit = 60)}")
        println("LIVE Threads discovery tag_header kinds: ${kinds(headers)}")
        println("LIVE Threads discovery tag_header nested keys: ${ThreadsShape.told(ThreadsShape.paths(headers, Regex(".*"), depth = 4).filterKeys { '.' in it.trimStart('.') }, limit = 40)}")
        println("LIVE Threads discovery community public values: ${publicValues(community).ifEmpty { listOf("none") }.joinToString("; ")}")
        val roots = pages.flatMap { ThreadsShape.scripts(it) }
        println("LIVE Threads discovery community key paths: ${ThreadsShape.told(ThreadsShape.paths(roots, Regex("(?i)communit"), depth = 40), limit = 30)}")
        val forms = pages.flatMap { addressForms(it) }.groupingBy { it }.eachCount()
        println("LIVE Threads discovery community address forms: ${ThreadsShape.told(forms, limit = 20)}")
        pages.flatMap { queryDocIds(it, "Communit") }.distinct().let { println("LIVE Threads discovery community queries: ${it.ifEmpty { listOf("none") }.joinToString(", ")}") }
        candidates(community.ifEmpty { headers }, pages).take(MAX_COMMUNITY_PAGES).forEach { (label, path) ->
            page(http, path, "community $label")?.let { report(client, "community $label", path, it) }
        }
    }

    private suspend fun reposts(http: OkHttpXHttp, client: ThreadsDirectClient, zuck: String?) {
        zuck?.let { repostShape("@zuck", it, "zuck") }
        REPOSTERS.forEach { handle ->
            page(http, "@$handle/reposts", "@$handle/reposts")?.let {
                repostShape("@$handle/reposts", it, handle)
                report(client, "@$handle/reposts", "@$handle/reposts", it)
            }
        }
        try {
            delay(GAP_MILLIS)
            val read = client.guestAccount(REPOSTERS.first()).items
            println("LIVE Threads discovery reposts guest GraphQL @${REPOSTERS.first()}: ${read.size} threads, ${read.count { it.isRepost }} reposts")
        } catch (e: Exception) {
            println("LIVE Threads discovery reposts guest GraphQL: failed (${e.javaClass.simpleName})")
        }
    }

    private fun repostShape(label: String, html: String, handle: String) {
        val roots = ThreadsShape.scripts(html)
        val posts = ThreadsShape.posts(roots)
        val shares = posts.mapNotNull { (it["text_post_app_info"] as? JsonObject)?.get("share_info") as? JsonObject }
        val outer = posts.filter { (((it["text_post_app_info"] as? JsonObject)?.get("share_info") as? JsonObject)?.get("reposted_post") as? JsonObject)?.isNotEmpty() == true }
        val read = ThreadsPages.profileThreads(html, handle)
        println("LIVE Threads discovery reposts $label: ${posts.size} post objects, ${outer.size} with share_info.reposted_post, read ${read.size} threads of which ${read.count { it.isRepost }} reposts (${read.count { it.isRepost && it.repostedByHandle == handle }} by the page's account)")
        println("LIVE Threads discovery reposts $label share_info keys: ${ThreadsShape.told(ThreadsShape.keyCounts(shares))}")
        println("LIVE Threads discovery reposts $label outer post keys: ${ThreadsShape.told(ThreadsShape.keyCounts(outer), limit = 30)}")
        println("LIVE Threads discovery reposts $label outer caption: ${outer.count { filled(it["caption"]) }} filled of ${outer.size}")
        println("LIVE Threads discovery reposts $label repost key paths: ${ThreadsShape.told(ThreadsShape.paths(roots, Regex("(?i)repost|reshar"), depth = 40), limit = 30)}")
        println("LIVE Threads discovery reposts $label thread item keys: ${ThreadsShape.told(ThreadsShape.keyCounts(items(roots)))}")
    }

    private suspend fun front(http: OkHttpXHttp, client: ThreadsDirectClient, first: String?) {
        val html = first ?: return println("LIVE Threads discovery front: not read")
        report(client, "front", "", html)
        ThreadsNextPages.preloads(html).forEach { preload ->
            val variables = preload.variables.entries.filterNot { it.key.startsWith("__relay_internal") }
            println("LIVE Threads discovery front ${preload.name} doc_id=${preload.docId ?: "none"} own variables: ${variables.joinToString { "${it.key}:${kind(it.value)}" }.ifEmpty { "none" }}")
        }
        println("LIVE Threads discovery front pagination queries: ${queryDocIds(html, "Pagination|LoggedOutFeed").ifEmpty { listOf("none") }.joinToString(", ")}")
        val seen = ThreadsPages.recommended(html).map { it.id }.toMutableSet()
        repeat(FRONT_REREADS) { index ->
            delay(GAP_MILLIS)
            val again = page(http, "", "front again ${index + 1}") ?: return@repeat
            val posts = ThreadsPages.recommended(again)
            val fresh = posts.count { it.id !in seen }
            seen += posts.map { it.id }
            println("LIVE Threads discovery front again ${index + 1}: ${posts.size} posts, $fresh not seen before, ${seen.size} in all")
        }
    }

    private suspend fun search(http: OkHttpXHttp) {
        SEARCH_PAGES.forEach { path ->
            val html = page(http, path, path) ?: return@forEach
            val roots = ThreadsShape.scripts(html)
            println("LIVE Threads discovery $path: ${html.length} chars, queries ${ThreadsShape.queries(html).joinToString(", ").ifEmpty { "none" }}, ${ThreadsShape.users(roots).size} user objects, ${ThreadsShape.posts(roots).size} post objects")
            println("LIVE Threads discovery $path suggestion key paths: ${ThreadsShape.told(ThreadsShape.paths(roots, Regex("(?i)suggest|trend|recommend|popular|topic|tag|communit|explore|keyword"), depth = 40), limit = 30)}")
            trends(path, html, roots)
        }
        PROFILE_SEARCHES.forEach { path ->
            val html = page(http, path, path) ?: return@forEach
            val roots = ThreadsShape.scripts(html)
            println("LIVE Threads discovery $path: ${html.length} chars, queries ${ThreadsShape.queries(html).joinToString(", ").ifEmpty { "none" }}, ${ThreadsShape.users(roots).size} user objects, ${ThreadsShape.posts(roots).size} post objects")
        }
    }

    /**
     * What the search page's `data.trends` holds: the kind of the value, the
     * keys of its items (a connection's nodes unwrapped) and the kind of each,
     * and how many trends [ThreadsTrends] read with a context or a summary.
     * Kinds and counts only; no trend's words.
     */
    private fun trends(path: String, html: String, roots: List<JsonElement>) {
        val values = roots.flatMap(::objects).mapNotNull { (it["data"] as? JsonObject)?.get("trends") }
        if (values.isEmpty()) return println("LIVE Threads discovery $path trends: no data.trends")
        val items = values.flatMap { value ->
            val list = when (value) {
                is JsonArray -> value
                is JsonObject -> value.values.firstOrNull { it is JsonArray } as? JsonArray
                else -> null
            }
            list.orEmpty().filterIsInstance<JsonObject>().map { item -> (item["node"] as? JsonObject) ?: item }
        }
        val read = ThreadsTrends.read(html)
        val objectKeys = values.filterIsInstance<JsonObject>().flatMap { it.keys }.distinct().map(::plain).ifEmpty { listOf("none") }
        println("LIVE Threads discovery $path trends: ${values.size} data.trends (${values.joinToString { kind(it) }}), object keys $objectKeys, ${items.size} items")
        println("LIVE Threads discovery $path trend item kinds: ${kinds(items)}")
        println("LIVE Threads discovery $path trend item nested keys: ${ThreadsShape.told(ThreadsShape.paths(items, Regex(".*"), depth = 4).filterKeys { '.' in it.trimStart('.') }, limit = 40)}")
        println("LIVE Threads discovery $path trends read: ${read.size}, ${read.count { it.context != null }} with a context, ${read.count { it.summary != null }} with a summary, ${read.count { it.terms.tag }} topics")
        ThreadsNextPages.preloads(html).filter { "Trend" in it.name }.forEach { preload ->
            val variables = preload.variables.entries.filterNot { it.key.startsWith("__relay_internal") }
            println("LIVE Threads discovery $path ${preload.name} doc_id=${preload.docId ?: "none"} own variables: ${variables.joinToString { "${it.key}:${kind(it.value)}" }.ifEmpty { "none" }}")
        }
    }

    /** A page's queries, the next page asked once by /api/graphql: status and counts only. */
    private suspend fun report(client: ThreadsDirectClient, label: String, path: String, html: String) {
        val chains = ThreadsPages.chains(html)
        println("LIVE Threads discovery $label page: ${chains.size} chains, ${chains.sumOf { it.size }} posts, ${chains.count { it.first().topicTag != null }} with a topic, queries ${ThreadsShape.queries(html).joinToString(", ").ifEmpty { "none" }}")
        val infos = ThreadsNextPages.pageInfos(html)
        println("LIVE Threads discovery $label page_info: ${infos.take(8).joinToString("; ") { "${it.query}@${it.path} has_next=${it.hasNextPage} cursor=${if (it.endCursor.isNullOrEmpty()) "no" else "yes"}" }.ifEmpty { "none" }}")
        val next = ThreadsNextPages.first(html) { true } ?: return println("LIVE Threads discovery $label next: not tried (no page_info that goes on with a doc id)")
        val lsd = ThreadsPages.lsd(html) ?: return println("LIVE Threads discovery $label next: not tried (no LSD token)")
        delay(GAP_MILLIS)
        val response = try {
            client.guestQuery(GuestQuery(next.docId, next.query, next.requestVariables, GuestQuery.relayForm(next.query)), lsd, "/$path", "/api/graphql")
        } catch (e: Exception) {
            return println("LIVE Threads discovery $label next: failed (${e.javaClass.simpleName})")
        }
        val roots = ThreadsNextPages.answer(response.body)
        val got = ThreadsPages.chainsIn(roots)
        println("LIVE Threads discovery $label next ${next.query} variables ${next.variables.keys.filterNot { it.startsWith("__relay_internal") }}: ${response.code}, ${roots.size} JSON, ${got.sumOf { it.size }} posts, new cursor=${if (roots.any { ThreadsNextPages.after(next, it) != null }) "yes" else "no"}")
    }

    private suspend fun page(http: OkHttpXHttp, path: String, label: String): String? {
        delay(GAP_MILLIS)
        val url = ThreadsLinks.web("/").resolve(path) ?: return null
        val response = try {
            http.get(url, guestHeaders(), followRedirects = false)
        } catch (e: IOException) {
            println("LIVE Threads discovery $label: unreachable (${e.javaClass.simpleName})")
            return null
        }
        println("LIVE Threads discovery $label: ${response.code}${sentTo(url, response)}")
        return response.body.takeIf { response.code == 200 }
    }

    private fun tagHeaders(html: String): List<JsonObject> = ThreadsShape.posts(ThreadsShape.scripts(html))
        .mapNotNull { ((it["text_post_app_info"] as? JsonObject)?.get("tag_header") as? JsonObject)?.takeIf(JsonObject::isNotEmpty) }

    /** Each key and the kinds of value it held: `community_id=digits:4`. */
    private fun kinds(objects: List<JsonObject>): String = objects
        .flatMap { o -> o.entries.map { "${plain(it.key)}=${kind(it.value)}" } }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        .joinToString(", ") { "${it.key}:${it.value}" }.ifEmpty { "none" }

    /** What kind a value is, never what it says. */
    private fun kind(value: JsonElement?): String = when (value) {
        null, is JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            !value.isString -> if (value.content == "true" || value.content == "false") "bool" else "number"
            value.content.isEmpty() -> "empty"
            value.content.all(Char::isDigit) -> "digits"
            value.content.startsWith("http") -> if ("threads." in value.content) "threads-url" else "url"
            value.content.startsWith("/") -> "path"
            SLUG.matches(value.content) -> "slug"
            else -> "text"
        }
    }

    /** The ids, slugs, paths, links and names a community's header holds: public, as its page is. */
    private fun publicValues(headers: List<JsonObject>): List<String> = headers.flatMap { header ->
        header.entries.filter { (key, value) ->
            val k = kind(value)
            k in setOf("digits", "slug", "path", "threads-url") && Regex("(?i)id|slug|url|path|link|name|handle|uri").containsMatchIn(key) ||
                key in NAME_KEYS && k != "null"
        }.map { (key, value) -> "${plain(key)}=${(value as JsonPrimitive).content.take(120)}" }
    }.distinct().take(12)

    /** Where a community's own page might be: each path-like value of its header, then the forms the pages name. */
    private fun candidates(headers: List<JsonObject>, pages: List<String>): List<Pair<String, String>> {
        val fromHeaders = headers.flatMap { header ->
            header.entries.mapNotNull { (key, value) ->
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                when (kind(value)) {
                    "path" -> "${plain(key)} path" to text.removePrefix("/")
                    "threads-url" -> "${plain(key)} url" to text.substringAfter("threads.").substringAfter('/')
                    else -> null
                }
            }
        }
        val named = pages.flatMap { addressPaths(it) }.map { "named" to it }
        val byName = headers.firstNotNullOfOrNull { h -> NAME_KEYS.firstNotNullOfOrNull { (h[it] as? JsonPrimitive)?.content?.ifEmpty { null } } }
            ?.let { listOf("by name as topic" to "search?q=${URLEncoder.encode(it, Charsets.UTF_8)}&serp_type=tags") }.orEmpty()
        return (fromHeaders + named + byName).distinctBy { it.second }
    }

    /** The forms of community addresses a page names, ids and names masked: `/communities/<n>`. */
    private fun addressForms(html: String): List<String> = addressPaths(html).map { path ->
        path.split('/').mapIndexed { i, part -> if (i == 0) part else if (part.all(Char::isDigit)) "<n>" else "<x>" }.joinToString("/", prefix = "/")
    }

    private fun addressPaths(html: String): List<String> = ADDRESS.findAll(html).map { it.groupValues[1].replace("\\/", "/").trim('/') }.distinct().take(10).toList()

    /** `Name doc_id=…` for each query whose name matches [pattern], the doc id named within a little after it. */
    private fun queryDocIds(html: String, pattern: String): List<String> = Regex("Barcelona[A-Za-z]*(?:$pattern)[A-Za-z]*Query").findAll(html)
        .map { it.value }.distinct().map { name ->
            val at = html.indexOf(name)
            val id = DOC_ID.find(html.subSequence(maxOf(0, at - 300), minOf(html.length, at + 600)))?.groupValues?.get(1)
            "$name doc_id=${id ?: "none nearby"}"
        }.toList()

    private fun items(roots: List<JsonElement>): List<JsonObject> =
        roots.flatMap(::objects).flatMap { o -> (o["thread_items"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() }

    private fun objects(node: JsonElement): List<JsonObject> = when (node) {
        is JsonObject -> listOf(node) + node.values.flatMap(::objects)
        is JsonArray -> node.flatMap(::objects)
        else -> emptyList()
    }

    private fun filled(value: JsonElement?): Boolean = when (value) {
        null, is JsonNull -> false
        is JsonObject -> value.isNotEmpty()
        is JsonPrimitive -> value.content.isNotEmpty()
        is JsonArray -> value.isNotEmpty()
    }

    private fun plain(key: String) = if (Regex("[a-z_][a-z0-9_]{0,47}").matches(key)) key else "<other>"

    private fun sentTo(url: HttpUrl, response: XResponse): String =
        response.header("Location")?.let(url::resolve)?.let { " -> ${it.host}${it.encodedPath}" }.orEmpty()

    private fun guestHeaders() = mapOf(
        "User-Agent" to ThreadsRepairs().webUserAgent,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private companion object {
        const val GAP_MILLIS = 2_500L
        const val FRONT_REREADS = 2
        const val MAX_COMMUNITY_PAGES = 4
        val SOURCES = listOf("@zuck", "", "search?q=cosplay", "search?q=cosplay&serp_type=tags")
        val REPOSTERS = listOf("zuck", "mosseri")
        val SEARCH_PAGES = listOf("search", "search?q=")

        /** Whether a guest's search has a profiles page of its own: forms threads.com might use, probed for user objects. */
        val PROFILE_SEARCHES = listOf("search?q=cosplay&serp_type=accounts", "search?q=cosplay&filter=profiles")
        val NAME_KEYS = setOf("display_name", "name", "community_name", "title")
        val SLUG = Regex("[a-z0-9_.-]{1,80}")
        val DOC_ID = Regex(""""(?:queryID|queryId|docID|docId|doc_id|documentID|id)"\s*:\s*"?(\d{10,})""")
        val ADDRESS = Regex("""(?:threads\.(?:com|net))?(?:\\?/)([a-z_]*communit[a-z_]*(?:\\?/)[A-Za-z0-9_.%-]{1,80})""")
    }
}
