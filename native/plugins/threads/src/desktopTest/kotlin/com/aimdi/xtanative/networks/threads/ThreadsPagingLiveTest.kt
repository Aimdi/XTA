package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * How a guest might page Threads' lists, for the next round to read in CI's
 * log (lines `LIVE Threads paging …`): for each page a guest opens, the
 * queries it preloaded with their doc ids and the names of their variables,
 * where the page keeps the LSD token and the app id (key paths only), each
 * `page_info` it carries, and then one next-page request as threads.com's
 * own client would send it, told by its status, content type, how many posts
 * came back and whether a new cursor did. Doc ids and query names are public
 * constants; no token, cookie, cursor or post text is ever logged.
 */
class ThreadsPagingLiveTest {
    @Test
    fun howAGuestIsGivenTheNextPage(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val client = ThreadsDirectClient(http)
        var post: String? = null
        PAGES.forEach { (label, path) ->
            val html = page(http, label, path) ?: return@forEach
            if (post == null) post = ThreadsPages.chains(html).flatten().firstNotNullOfOrNull { it.url }?.substringAfter("threads.com/")
            report(client, label, path, html)
        }
        val postPath = post ?: return@runBlocking println("LIVE Threads paging post: no post link on the pages read")
        page(http, "post", postPath)?.let { report(client, "post", postPath, it) }
    }

    private suspend fun page(http: OkHttpXHttp, label: String, path: String): String? {
        delay(GAP_MILLIS)
        val url = ThreadsLinks.web("/").resolve(path)!!
        val response = try {
            http.get(url, guestHeaders(), followRedirects = false)
        } catch (e: IOException) {
            println("LIVE Threads paging $label: unreachable (${e.javaClass.simpleName})")
            return null
        }
        if (response.code != 200) println("LIVE Threads paging $label: ${response.code}${sentTo(url, response)}")
        return response.body.takeIf { response.code == 200 }
    }

    private suspend fun report(client: ThreadsDirectClient, label: String, path: String, html: String) {
        val preloads = ThreadsNextPages.preloads(html)
        println("LIVE Threads paging $label queries: ${preloads.joinToString("; ") { "${it.name} doc_id=${it.docId ?: "none"} variables=${it.variables.keys.sorted()}" }.ifEmpty { "none with variables" }}")
        println("LIVE Threads paging $label preloaded names: ${ThreadsShape.queries(html).joinToString(", ").ifEmpty { "none" }}")
        println("LIVE Threads paging $label other query names: ${otherQueryNames(html).ifEmpty { listOf("none") }.joinToString(", ")}")
        println("LIVE Threads paging $label tokens: ${tokenPlaces(html)}")
        val infos = ThreadsNextPages.pageInfos(html)
        println("LIVE Threads paging $label page_info: ${infos.take(12).joinToString("; ") { "${it.query}@${it.path} has_next=${it.hasNextPage} cursor=${if (it.endCursor.isNullOrEmpty()) "no" else "yes"}" }.ifEmpty { "none" }}")
        val next = ThreadsNextPages.first(html) { true }
        if (next == null) return println("LIVE Threads paging $label next: not tried (no page_info that goes on with a doc id for its query)")
        val lsd = ThreadsPages.lsd(html) ?: return println("LIVE Threads paging $label next: not tried (no LSD token)")
        for (endpoint in ENDPOINTS) {
            delay(GAP_MILLIS)
            val query = GuestQuery(next.docId, next.query, next.requestVariables, relayForm(next.query))
            val response = try {
                client.guestQuery(query, lsd, "/$path", endpoint)
            } catch (e: Exception) {
                println("LIVE Threads paging $label next ${next.query} via $endpoint: failed (${e.javaClass.simpleName})")
                continue
            }
            println("LIVE Threads paging $label next ${next.query} via $endpoint: ${answered(response, next)}")
            if (response.code == 200 && ThreadsNextPages.answer(response.body).isNotEmpty()) break
        }
    }

    /** The answer told by shape: status, type, JSON objects, error codes, top keys, posts, and whether it goes on. */
    private fun answered(response: XResponse, asked: ThreadsNextPage): String {
        val type = response.header("Content-Type")?.substringBefore(';')?.trim() ?: "no type"
        val roots = ThreadsNextPages.answer(response.body)
        if (roots.isEmpty()) return "${response.code} $type, ${response.body.length} chars, not JSON"
        val errors = roots.flatMap { (it as? kotlinx.serialization.json.JsonObject)?.get("errors") as? kotlinx.serialization.json.JsonArray ?: emptyList() }
        val codes = errors.mapNotNull { ((it as? kotlinx.serialization.json.JsonObject)?.get("code") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { c -> c.all(Char::isDigit) } }
        val keys = roots.flatMap { ThreadsShape.paths(listOf(it), Regex(".*"), depth = 2, tail = 3).keys }.distinct().take(16)
        val posts = ThreadsShape.posts(roots).size
        val chains = ThreadsPages.chainsIn(roots)
        val infos = roots.flatMap { ThreadsNextPages.pageInfos(it, asked.query) }
        val after = roots.firstNotNullOfOrNull { ThreadsNextPages.after(asked, it) }
        return "${response.code} $type, ${roots.size} JSON, errors=${errors.size}${if (codes.isEmpty()) "" else " codes=$codes"}, keys=$keys, " +
            "post objects=$posts, chains=${chains.size} (${chains.sumOf { it.size }} posts), page_info=${infos.map { "${it.path} has_next=${it.hasNextPage}" }.take(4)}, " +
            "new cursor=${if (after != null) "yes" else "no"}"
    }

    /** Where the page keeps what its GraphQL calls send: by key path or by the name it is spelled under, never by value. */
    private fun tokenPlaces(html: String): String {
        val spelled = TOKEN_NAMES.map { it to Regex(Regex.escape(it)).findAll(html).count() }.filter { it.second > 0 }
        val roots = ThreadsShape.scripts(html)
        val paths = ThreadsShape.paths(roots, Regex("(?i)lsd|app_?id|asbd|dtsg|jazoest|token"), depth = 30).keys.take(12)
        return "spelled ${spelled.joinToString { "${it.first}=${it.second}" }.ifEmpty { "nothing" }}; key paths ${paths.ifEmpty { listOf("none") }}"
    }

    private fun otherQueryNames(html: String): List<String> =
        Regex("""Barcelona[A-Za-z]*(?:Refetchable|Pagination|Paginated|Connection|More|Feed|Replies)[A-Za-z]*Query""").findAll(html)
            .map { it.value }.distinct().take(20).toList()

    private fun relayForm(name: String) = listOf("fb_api_caller_class" to "RelayModern", "fb_api_req_friendly_name" to name, "server_timestamps" to "true")

    private fun sentTo(url: HttpUrl, response: XResponse): String =
        response.header("Location")?.let(url::resolve)?.let { " -> ${it.host}${it.encodedPath}" }.orEmpty()

    private fun guestHeaders() = mapOf(
        "User-Agent" to ThreadsRepairs().webUserAgent,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private companion object {
        const val GAP_MILLIS = 2_500L
        val ENDPOINTS = listOf("/graphql/query", "/api/graphql")
        val TOKEN_NAMES = listOf("\"LSD\",[]", "\"X-IG-App-ID\"", "\"APP_ID\"", "\"appId\"", "\"app_id\"", "\"IG_APP_ID\"", "\"appID\"", "\"DTSGInitialData\"", "\"ASBD_ID\"", "\"asbd_id\"")
        val PAGES = listOf(
            "@zuck" to "@zuck",
            "@zuck/replies" to "@zuck/replies",
            "@zuck/media" to "@zuck/media",
            "@zuck/reposts" to "@zuck/reposts",
            "front" to "",
            "search" to "search?q=threads",
            "search recent" to "search?q=threads&filter=recent",
            "topic" to "search?q=threads&serp_type=tags",
        )
    }
}
