package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What a guest is answered for a post's lists and marks, for the next round
 * to read in CI's log (`LIVE Threads post lists …`): the sub-pages a post's
 * Quotes and Reposts might live at, the session paths [ThreadsQuotes] and
 * [ThreadsPeopleList.REPOSTERS] try (a 404 there, where the likers' path
 * asks for a session, says the path is not Meta's), the post page's
 * queries about quotes, reposts, replies held back and inline expansion
 * with any doc id beside them, and how many posts carry the marks this round
 * reads. Status codes, key names, counts and Meta's own words for who may
 * reply only; no session is sent, and nothing a post says is logged. It
 * never fails on what it finds. Skipped unless RUN_LIVE=true.
 */
class ThreadsPostListsLiveTest {
    @Test
    fun whatAGuestIsAnsweredForAPostsListsAndMarks(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val client = ThreadsDirectClient(http)
        val post = try {
            val posts = client.guestAccount("zuck").items.filter { it.url != null }
            posts.firstOrNull { (it.quoteCount ?: 0) > 0 } ?: posts.firstOrNull()
        } catch (e: Exception) {
            println("LIVE Threads post lists: no post on @zuck's page (${e.javaClass.simpleName})")
            return@runBlocking
        }
        val url = post?.url?.let(ThreadsLinks::canonicalPostUrl)
        val media = post?.let(ThreadsPeople::mediaId)
        if (url == null || media == null) {
            println("LIVE Threads post lists: no post link or media id")
            return@runBlocking
        }
        println("LIVE Threads post lists: quotes ${post.quoteCount != null}, reposts ${post.repostCount != null}")
        page(http, url, "post")?.let(::marks)
        for (tail in listOf("quotes", "reposts", "likes")) page(http, url.newBuilder().addPathSegment(tail).build(), tail)
        val asked = ThreadsPeople.paths(ThreadsPeopleList.LIKERS, media).map { "likers" to it } +
            ThreadsPeople.paths(ThreadsPeopleList.REPOSTERS, media).map { "reposters" to it } +
            ThreadsQuotes.paths(media).map { "quotes" to it } +
            CANDIDATES.map { (list, template) -> list to template.replace("{m}", media) }
        asked.forEachIndexed { index, (list, path) -> api(http, list, index, ThreadsLinks.web(path, emptyList())) }
    }

    /** One page's answer as a guest: its status or where it sends them, and its queries about a post's lists. */
    private suspend fun page(http: OkHttpXHttp, url: HttpUrl, label: String): String? {
        delay(GAP_MILLIS)
        val response = try {
            http.get(url, HTML, followRedirects = false)
        } catch (e: IOException) {
            println("LIVE Threads post lists page $label: unreachable (${e.javaClass.simpleName})")
            return null
        }
        val sent = response.header("Location")?.let(url::resolve)?.let { " -> ${it.host}${it.encodedPath.replace(CODE, "/<code>")}" }.orEmpty()
        val names = if (response.code == 200) LIST_QUERY.findAll(response.body).map { it.value }.distinct().toList() else emptyList()
        val told = names.joinToString(", ") { name -> "$name${docIdNear(response.body, name)?.let { " doc_id=$it" } ?: ""}" }.ifEmpty { "none" }
        println("LIVE Threads post lists page $label: ${response.code}$sent, queries: $told")
        return response.body.takeIf { response.code == 200 }
    }

    /** How many posts of the page carry the marks this round reads, and the words Meta uses for who may reply. */
    private fun marks(html: String) {
        val posts = ThreadsShape.posts(ThreadsShape.scripts(html))
        val infos = posts.mapNotNull { it["text_post_app_info"] as? JsonObject }
        val edited = posts.count { (it["caption_is_edited"] as? JsonPrimitive)?.content == "true" }
        val liked = infos.count { (it["is_liked_by_root_author"] as? JsonPrimitive)?.content == "true" }
        val controls = infos.mapNotNull { info ->
            when (val value = info["reply_control"]) {
                is JsonPrimitive -> value.content.takeIf { WORD.matches(it) } ?: "<${if (value.isString) "text" else "number"}>"
                is JsonObject -> "{${value.keys.sorted().joinToString(",")}}"
                null -> null
                else -> "<other>"
            }
        }.groupingBy { it }.eachCount()
        val approvals = ThreadsShape.keyCounts(infos.mapNotNull { it["reply_approval_info"] as? JsonObject })
        println(
            "LIVE Threads post lists marks: ${posts.size} posts, caption_is_edited ${edited}, is_liked_by_root_author $liked, " +
                "reply_control ${ThreadsShape.told(controls)}, reply_approval_info keys ${ThreadsShape.told(approvals)}, " +
                "music ${posts.count { it["text_app_music_info"] is JsonObject }}",
        )
    }

    /** A session path asked as a guest: its status and top-level key names. */
    private suspend fun api(http: OkHttpXHttp, list: String, index: Int, url: HttpUrl) {
        delay(GAP_MILLIS)
        val shape = try {
            val response = http.get(url, JSON, followRedirects = false)
            val keys = (ThreadsJson.decode(response.body) as? JsonObject)?.keys?.sorted()?.joinToString(",") ?: "not JSON"
            "${response.code} keys: $keys"
        } catch (e: IOException) {
            "unreachable (${e.javaClass.simpleName})"
        }
        println("LIVE Threads post lists api $list path ${index + 1} (${url.encodedPath.replace(DIGITS, "<id>")}) as a guest: $shape")
    }

    /** A doc id written within a short reach after [name] in [html], as Relay's preloaders write one. */
    private fun docIdNear(html: String, name: String): String? {
        val at = html.indexOf("\"$name\"").takeIf { it >= 0 } ?: return null
        return DOC_ID.find(html.substring(at, minOf(html.length, at + REACH)))?.groupValues?.get(1)
    }

    private companion object {
        /**
         * Where the reposters' and quotes' lists might be, asked as a guest: a
         * real session path sends a guest to sign in (302), as the likers' does;
         * a wrong one is a 404. `replies` is a control that exists.
         */
        val CANDIDATES = listOf(
            "control" to "/api/v1/text_feed/{m}/replies/",
            "reposters" to "/api/v1/text_feed/{m}/reposts/",
            "reposters" to "/api/v1/text_feed/{m}/repost_users/",
            "reposters" to "/api/v1/media/{m}/reposts/",
            "reposters" to "/api/v1/media/{m}/reshares/",
            "quotes" to "/api/v1/text_feed/{m}/quoted_posts/",
            "quotes" to "/api/v1/text_feed/{m}/quote_posts/",
            "quotes" to "/api/v1/media/{m}/quoted_posts/",
        )

        const val GAP_MILLIS = 2_500L
        const val REACH = 400
        val LIST_QUERY = Regex("Barcelona[A-Za-z]*(Quote|Repost|Liker|Inline|Pending|Activity)[A-Za-z]*Query")
        val DOC_ID = Regex(""""(?:queryID|docID|doc_id|id)"\s*:\s*"(\d{8,})"""")
        val WORD = Regex("[a-z_]{1,40}", RegexOption.IGNORE_CASE)
        val DIGITS = Regex("\\d{4,}")
        val CODE = Regex("/post/[A-Za-z0-9_-]+")
        val HTML = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        val JSON = mapOf("User-Agent" to ThreadsRepairs().webUserAgent, "Accept" to "application/json", "X-IG-App-ID" to ThreadsRepairs().appId)
    }
}
