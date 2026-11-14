package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.repairs.Repairs
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What Meta answers the reads `repairs.json` switches on, end to end, for
 * the next round to read in CI's log (`LIVE Threads switched …`). A guest,
 * through the plugin's own client with the published repairs in force, asks
 * each: About this profile for @zuck, the trending topics, the replies
 * under a reply of a post that has reply threads, and a next page by each
 * refetch query; then the two people reads the plugin prepares but ships
 * off, by the ids CI's probe found. Each request is told by the names of
 * the variables sent, its status, its answer's top-level and `data` key
 * paths with their types and counts, Meta's error messages (long numbers
 * struck out), and what the plugin read out of it, as counts. All are
 * queries. No token, cookie, LSD value, cursor, handle or word of a post is
 * logged, and it never fails on Meta's answer. Skipped unless RUN_LIVE=true.
 */
class ThreadsSwitchedReadsLiveTest {
    @Test
    fun whatMetaAnswersTheReadsRepairsJsonSwitchesOn(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        try {
            probe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("LIVE Threads switched: stopped (${e.javaClass.simpleName})")
        }
    }

    private suspend fun probe() {
        val file = repairsJson()
        val published = file?.let { runCatching { Repairs.parse(it.readText()) }.getOrNull() }.orEmpty()
        val repairs = ThreadsRepairs.published(published)
        val on = SWITCHES.map { (name, id) -> "$name=${if (id(repairs).isEmpty()) "off" else "on"}" }
        println("LIVE Threads switched repairs.json: ${if (file == null) "not found, as shipped" else "${published.keys.count { it.startsWith("threads.") }} Threads entries"}; ${on.joinToString(", ")}")

        // The people reads ship off: here they are asked by the ids the bundles named, to see what they answer.
        val probed = repairs.copy(
            searchUsersDocId = repairs.searchUsersDocId.ifEmpty { FOUND_SEARCH_USERS },
            recommendedUsersDocId = repairs.recommendedUsersDocId.ifEmpty { FOUND_RECOMMENDED_USERS },
        )
        val http = Recording(OkHttpXHttp())
        val pacing = ThreadsPacing(guestGapMillis = GAP_MILLIS, guestJitterMillis = 500)
        val client = ThreadsDirectClient(http, repairs = { probed }, pacing = pacing)

        about(client, http)
        trending(client, http, probed)
        val post = inlineReplies(client, http, probed)
        refetches(client, http, probed, post)
        people(client, http, prepared = repairs.searchUsersDocId.isEmpty(), preparedRecommended = repairs.recommendedUsersDocId.isEmpty())
    }

    private suspend fun about(client: ThreadsDirectClient, http: Recording) = read("about profile", http) {
        if (!client.readsAboutProfile) return@read "off"
        val about = client.aboutProfile("zuck")
        "parsed: ${if (about == null) "nothing" else "joined as a moment=${about.joinedAt != null}, in words=${about.joined != null}"}"
    }

    /** The trends the search page carries, then the query itself, which the client asks only of a page without any. */
    private suspend fun trending(client: ThreadsDirectClient, http: Recording, values: ThreadsRepairs) {
        read("trending page", http) { "parsed: ${client.trends(force = true).size} trends (the page's, else the query's)" }
        val docId = values.trendingTopicsDocId
        if (docId.isEmpty()) return println("LIVE Threads switched trending query: off")
        val variables = ThreadsRelayProviders.with(JsonObject(emptyMap()), only = ThreadsBundleReads.TRENDING_PROVIDED)
        asked("trending query", client, http, GuestQuery(docId, ThreadsBundleReads.TRENDING_TOPICS, variables, GuestQuery.relayForm(ThreadsBundleReads.TRENDING_TOPICS)), "/search") { roots ->
            "parsed: ${ThreadsTrends.fromRoots(roots).size} trends"
        }
    }

    /** A post of @zuck's with reply threads, and the replies under one of its replies; the post, for the refetch that follows. */
    private suspend fun inlineReplies(client: ThreadsDirectClient, http: Recording, values: ThreadsRepairs): Pair<ThreadsPost, ThreadsPostPage>? {
        val posts = try {
            client.guestAccount("zuck").items.filter { it.url != null }.take(POSTS_TRIED)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("LIVE Threads switched inline replies: no post on the profile (${e.javaClass.simpleName})")
            return null
        }
        var opened: Pair<ThreadsPost, ThreadsPostPage>? = null
        for (post in posts) {
            val page = attempt { client.postPage(post.url!!) } ?: continue
            println(
                "LIVE Threads switched post page: ${page.replies.size} reply threads, ${page.replies.count { it.hasMore }} Meta holds more of, " +
                    "${page.replies.count { it.pagingToken != null }} naming where the rest starts, more replies=${page.moreReplies}, next cursor=${page.repliesNext != null}",
            )
            if (page.replies.isNotEmpty()) {
                opened = post to page
                break
            }
        }
        val (_, page) = opened ?: return null.also { println("LIVE Threads switched inline replies: no post with reply threads among ${posts.size}") }
        val thread = page.replies.firstOrNull { it.hasMore } ?: page.replies.first()
        val reply = thread.posts.last()
        read("inline replies (unfinished thread=${thread.hasMore}, of ${thread.posts.size})", http) {
            if (!client.readsDeeperReplies) return@read "off"
            val brought = client.deeperReplies(reply)
            "parsed: ${if (brought == null) "nothing" else "${brought.posts.size} posts, ${brought.posts.count { it.id !in thread.posts.map(ThreadsPost::id) }} new, more=${brought.hasMore}, levels=${brought.levels.values.sorted().distinct()}"}"
        }
        // The same read as round 21 sent it and without the thread's token, to tell which variable Meta minds.
        val media = ThreadsPeople.mediaId(reply)
        val referer = reply.url?.let(ThreadsLinks::canonicalPostUrl)?.encodedPath
        if (values.inlineRepliesDocId.isNotEmpty() && media != null && referer != null) {
            val permalink = page.target?.let(ThreadsPeople::mediaId)
            val variants = listOf(
                "id alone" to ThreadsBundleReads.variables(values.inlineRepliesVariable, media),
                "no token" to ThreadsRelayProviders.with(ThreadsBundleReads.inlineReplies(values.inlineRepliesVariable, media, permalink, null, null)),
            )
            variants.forEach { (label, variables) ->
                asked("inline replies, $label", client, http, GuestQuery(values.inlineRepliesDocId, ThreadsBundleReads.INLINE_REPLIES, variables, GuestQuery.relayForm(ThreadsBundleReads.INLINE_REPLIES)), referer) { roots ->
                    "parsed: ${ThreadsPostPageReader.deeper(roots, reply.id)?.posts?.size ?: 0} posts"
                }
            }
        }
        return opened
    }

    /** One next page by each refetch query, asked as the client's fallback asks it: after the cursor the page named. */
    private suspend fun refetches(client: ThreadsDirectClient, http: Recording, values: ThreadsRepairs, opened: Pair<ThreadsPost, ThreadsPostPage>?) {
        suspend fun refetch(refetch: ThreadsRefetch, first: ThreadsNextPage?, referer: String, parsed: (List<JsonElement>) -> String) {
            val docId = values.refetchDocId(refetch)
            if (docId.isEmpty()) return println("LIVE Threads switched refetch ${refetch.query}: off")
            if (first == null) return println("LIVE Threads switched refetch ${refetch.query}: not tried (the page named no cursor)")
            val page = first.copy(query = refetch.query, docId = docId)
            val query = GuestQuery(docId, refetch.query, ThreadsRelayProviders.with(page.requestVariables), GuestQuery.relayForm(refetch.query))
            asked("refetch", client, http, query, referer) { roots ->
                "${parsed(roots)}, page_info=${roots.flatMap { ThreadsNextPages.pageInfos(it, refetch.query) }.take(4).map { "${it.path} has_next=${it.hasNextPage}" }}, new cursor=${roots.any { ThreadsNextPages.after(page, it) != null }}"
            }
        }
        refetch(ThreadsRefetch.PROFILE_THREADS, ThreadsNextPage.decode(client.profileNext("zuck")), "/@zuck") { roots ->
            "parsed: ${ThreadsPages.profileThreads(ThreadsPages.chainsIn(roots), "zuck").size} threads"
        }
        val (post, page) = opened ?: return println("LIVE Threads switched refetch ${ThreadsRefetch.REPLIES.query}: not tried (no post page)")
        refetch(ThreadsRefetch.REPLIES, ThreadsNextPage.decode(page.repliesNext), post.url?.let(ThreadsLinks::canonicalPostUrl)?.encodedPath ?: "/") { roots ->
            "parsed: ${ThreadsPostPageReader.replyThreads(roots, page.target?.id.orEmpty()).size} reply threads"
        }
        // A guest's search page names no cursor (run 114): the refetch is asked for its first page, to see what it gives a guest.
        val search = ThreadsNextPage(ThreadsRefetch.SEARCH.query, values.refetchDocId(ThreadsRefetch.SEARCH), ThreadsRefetch.SEARCH.seed(WORDS)!!, after = "")
        if (search.docId.isEmpty()) return println("LIVE Threads switched refetch ${ThreadsRefetch.SEARCH.query}: off")
        val variables = ThreadsRelayProviders.with(JsonObject(search.variables + ("first" to JsonPrimitive(ThreadsNextPage.PAGE_SIZE))))
        asked("refetch, first page", client, http, GuestQuery(search.docId, search.query, variables, GuestQuery.relayForm(search.query)), "/search?q=$WORDS") { roots ->
            "parsed: ${ThreadsPages.searchResults(ThreadsPages.chainsIn(roots), WORDS, false).size} posts"
        }
    }

    private suspend fun people(client: ThreadsDirectClient, http: Recording, prepared: Boolean, preparedRecommended: Boolean) {
        read("people search${if (prepared) " (prepared, off in the app)" else ""}", http) {
            val found = client.guestPeople(WORDS)
            "parsed: ${if (found == null) "refused" else "${found.size} accounts, ${found.count { it.fullName.isNotEmpty() }} named, ${found.count { it.profilePicUrl.isNotEmpty() }} pictured, ${found.count { it.isVerified }} verified"}"
        }
        read("recommended people${if (preparedRecommended) " (prepared, off in the app)" else ""}", http) {
            "parsed: ${client.recommendedPeople().size} accounts"
        }
    }

    /** One of the client's own reads: what it parsed, then the shape of every GraphQL request it sent for it. */
    private suspend fun read(label: String, http: Recording, parsed: suspend () -> String) {
        val before = http.calls.size
        val told = try {
            parsed()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            "failed: ${e.javaClass.simpleName}${(e as? ThreadsException)?.let { " ${it.failure}" }.orEmpty()}"
        }
        val sent = http.calls.drop(before)
        if (sent.isEmpty()) println("LIVE Threads switched $label: no GraphQL request sent")
        sent.forEach { println("LIVE Threads switched $label ${it.name}: ${shape(it)}") }
        println("LIVE Threads switched $label $told")
    }

    /** One query asked directly, as a page at [referer] asks it with the token of the page read last, and what [parsed] makes of its answer. */
    private suspend fun asked(label: String, client: ThreadsDirectClient, http: Recording, query: GuestQuery, referer: String, parsed: (List<JsonElement>) -> String) {
        val lsd = http.lsd ?: return println("LIVE Threads switched $label ${query.name}: not tried (no LSD token on the pages read)")
        val before = http.calls.size
        try {
            client.guestQuery(query, lsd, referer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return println("LIVE Threads switched $label ${query.name}: failed (${e.javaClass.simpleName})")
        }
        val call = http.calls.drop(before).lastOrNull() ?: return println("LIVE Threads switched $label ${query.name}: answered from a request sent before")
        val roots = ThreadsNextPages.answer(call.response.body).filter { (it as? JsonObject)?.get("data") is JsonObject }
        println("LIVE Threads switched $label ${query.name}: ${shape(call)}; ${runCatching { parsed(roots) }.getOrElse { "parsing failed (${it.javaClass.simpleName})" }}")
    }

    /** A request and its answer by shape: variable names, status, type, key paths with types and counts, error messages. */
    private fun shape(call: Call): String {
        val response = call.response
        val type = response.header("Content-Type")?.substringBefore(';')?.trim() ?: "no type"
        val sent = "sent ${call.variables.filterNot(ThreadsRelayProviders::isProvided).sorted()} +${call.variables.count(ThreadsRelayProviders::isProvided)} provided"
        val roots = ThreadsNextPages.answer(response.body)
        if (roots.isEmpty()) return "$sent; ${response.code} $type, ${response.body.length} chars, not JSON"
        val objects = roots.filterIsInstance<JsonObject>()
        val errors = objects.flatMap { it["errors"] as? JsonArray ?: emptyList() }.filterIsInstance<JsonObject>()
        val messages = errors.mapNotNull { error -> listOf("message", "summary", "description").firstNotNullOfOrNull { (error[it] as? JsonPrimitive)?.takeIf { text -> text.isString }?.content } }
            .map { message -> message.take(MESSAGE_CHARS).replace(LONG_NUMBER, "#").map { if (it in ' '..'~') it else '?' }.joinToString("") }.distinct().take(4)
        val codes = errors.mapNotNull { (it["code"] as? JsonPrimitive)?.content?.takeIf { code -> code.all(Char::isDigit) } }.distinct()
        val top = objects.flatMap { it.keys }.distinct().map(::name)
        val data = objects.mapNotNull { it["data"] }.flatMap { paths(it, "data", DEPTH) }.distinct().take(MOST_PATHS)
        return "$sent; ${response.code} $type, ${roots.size} JSON, errors=${errors.size}${if (codes.isEmpty()) "" else " codes=$codes"}${if (messages.isEmpty()) "" else " messages=$messages"}, " +
            "top=$top, data=$data, post objects=${ThreadsShape.posts(roots).size}, user objects=${roots.flatMap(::objects).count { "username" in it }}"
    }

    /** Every key path under [node] with what it holds (`obj`, `list[n]`, `str`, `num`, `bool`, `null`), a list told by its first item. */
    private fun paths(node: JsonElement, path: String, depth: Int): List<String> = when (node) {
        is JsonObject -> listOf("$path:obj") + if (depth <= 0) emptyList() else node.entries.flatMap { (key, value) -> paths(value, "$path.${name(key)}", depth - 1) }
        is JsonArray -> listOf("$path:list[${node.size}]") + if (depth <= 0) emptyList() else node.firstOrNull()?.let { paths(it, "$path[]", depth - 1) }.orEmpty().filterNot { it == "$path[]:obj" }
        is JsonNull -> listOf("$path:null")
        is JsonPrimitive -> listOf("$path:" + if (node.isString) "str" else if (node.content == "true" || node.content == "false") "bool" else "num")
    }

    private fun objects(node: JsonElement): List<JsonObject> = when (node) {
        is JsonObject -> listOf(node) + node.values.flatMap(::objects)
        is JsonArray -> node.flatMap(::objects)
        else -> emptyList()
    }

    /** A key as it may be logged: a plain identifier, else `<other>` (an id or a handle used as a key). */
    private fun name(key: String): String = if (PLAIN_KEY.matches(key)) key else "<other>"

    /** The repository's `repairs.json`, found from wherever the tests run; null when it is not there. */
    private fun repairsJson(): File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "repairs.json") }
        .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }

    /** A GraphQL request the client sent and what it was answered. */
    private class Call(val name: String, val variables: Set<String>, val response: XResponse)

    /** Passes every request on and keeps each GraphQL one's query name, variable names and answer, and the last page's token for [asked]. */
    private class Recording(private val inner: XHttp) : XHttp {
        val calls = CopyOnWriteArrayList<Call>()

        @Volatile
        var lsd: String? = null

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
            inner.get(url, headers, followRedirects).also { response -> ThreadsPages.lsd(response.body)?.let { lsd = it } }

        override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse = inner.post(url, headers)

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse {
            val response = inner.post(url, headers, body, contentType)
            val form = Asked("POST", url, emptyMap(), body, contentType).form
            val variables = (form["variables"]?.let(ThreadsJson::decode) as? JsonObject)?.keys.orEmpty()
            calls += Call(form["fb_api_req_friendly_name"] ?: headers["X-FB-Friendly-Name"] ?: "unnamed", variables, response)
            return response
        }
    }

    private companion object {
        /** `BarcelonaSearchUserResultsRefetchableQuery` and `BarcelonaSearchRecommendedUsersRefetchableQuery`, as run 114 read them out of the bundles. */
        const val FOUND_SEARCH_USERS = "27749314088076447"
        const val FOUND_RECOMMENDED_USERS = "38988761787435415"
        const val WORDS = "threads"
        const val GAP_MILLIS = 2_500L
        const val POSTS_TRIED = 3
        const val DEPTH = 5
        const val MOST_PATHS = 40
        const val MESSAGE_CHARS = 200
        val LONG_NUMBER = Regex("[0-9]{6,}")
        val PLAIN_KEY = Regex("[A-Za-z_][A-Za-z0-9_]{0,47}")
        val SWITCHES: List<Pair<String, (ThreadsRepairs) -> String>> = listOf(
            "inline_replies" to { it.inlineRepliesDocId },
            "about_profile" to { it.aboutProfileDocId },
            "trending_topics" to { it.trendingTopicsDocId },
            "search_users" to { it.searchUsersDocId },
            "recommended_users" to { it.recommendedUsersDocId },
            "replies_refetch" to { it.refetchDocId(ThreadsRefetch.REPLIES) },
            "search_refetch" to { it.refetchDocId(ThreadsRefetch.SEARCH) },
            "profile_refetch" to { it.refetchDocId(ThreadsRefetch.PROFILE_THREADS) },
        )
    }
}
