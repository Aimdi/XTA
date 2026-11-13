package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What a guest is given of round 19's post content, for the next round to
 * read in CI's log (`LIVE Threads post content …`): the languages Meta
 * names (codes only) and the caption's keys, the shape of
 * `related_trends_info` and `community_badge` (key paths, and a badge's
 * type where it is one word), the key paths that may hold a view count, a
 * ghost post or attached text, how many posts [ThreadsParser] reads each
 * of these from, the post page's queries about them with any doc id beside
 * them, and how the translation paths [ThreadsTranslation] tries answer a
 * guest (a real session path sends a guest to sign in, a wrong one is a
 * 404). Key names, kinds, counts and status codes only: no session is sent
 * and nothing a post says is logged. It never fails on what it finds.
 * Skipped unless RUN_LIVE=true.
 */
class ThreadsPostContentLiveTest {
    @Test
    fun whatAGuestIsGivenOfAPostsContent(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        var postUrl: HttpUrl? = null
        for ((label, path) in PAGES) {
            val html = page(http, ThreadsLinks.web("/").resolve(path)!!, label) ?: continue
            val posts = ThreadsShape.posts(ThreadsShape.scripts(html))
            content(label, posts)
            postUrl = postUrl ?: posts.firstNotNullOfOrNull { ThreadsParser.post(J(it))?.url }?.let(ThreadsLinks::canonicalPostUrl)
        }
        val url = postUrl ?: run {
            println("LIVE Threads post content: no post link on the pages read")
            return@runBlocking
        }
        page(http, url, "post")?.let { html ->
            content("post", ThreadsShape.posts(ThreadsShape.scripts(html)))
            val names = CONTENT_QUERY.findAll(html).map { it.value }.distinct().toList()
            val told = names.joinToString(", ") { name -> "$name${docIdNear(html, name)?.let { " doc_id=$it" } ?: ""}" }.ifEmpty { "none" }
            println("LIVE Threads post content post page queries: $told")
        }
        val media = ThreadsLinks.shortcode(url.toString())?.let(ThreadsPeople::idOfShortcode) ?: run {
            println("LIVE Threads post content: no media id")
            return@runBlocking
        }
        ThreadsTranslation.paths(media).forEachIndexed { index, path ->
            api(http, index, ThreadsLinks.web(path, ThreadsTranslation.params(media, null)))
        }
    }

    /** The content keys of one page's posts: kinds, key paths and counts. */
    private fun content(label: String, posts: List<JsonObject>) {
        val infos = posts.mapNotNull { it["text_post_app_info"] as? JsonObject }
        val captions = posts.mapNotNull { it["caption"] as? JsonObject }
        val languages = posts.mapNotNull { (it["detected_language"] as? JsonPrimitive)?.content?.takeIf(LANGUAGE::matches) ?: it["detected_language"]?.let { "<other>" } }
            .groupingBy { it }.eachCount()
        val translatable = captions.mapNotNull { (it["can_translate"] as? JsonPrimitive)?.content?.takeIf(WORD::matches) }.groupingBy { it }.eachCount()
        println(
            "LIVE Threads post content $label: ${posts.size} posts, detected_language ${ThreadsShape.told(languages)}, " +
                "caption keys ${ThreadsShape.told(ThreadsShape.keyCounts(captions))}, can_translate ${ThreadsShape.told(translatable)}",
        )
        val trends = infos.mapNotNull { it["related_trends_info"] }
        println(
            "LIVE Threads post content $label related_trends_info: kinds ${ThreadsShape.told(trends.groupingBy(::kind).eachCount())}, " +
                "paths ${ThreadsShape.told(ThreadsShape.paths(trends, ANY, depth = 4))}",
        )
        val badges = infos.mapNotNull { it["community_badge"] as? JsonObject }
        val badgeTypes = badges.mapNotNull { (it["badge_type"] as? JsonPrimitive)?.content?.let { type -> if (WORD.matches(type)) type else "<other>" } }
        println(
            "LIVE Threads post content $label community_badge: keys ${ThreadsShape.told(ThreadsShape.keyCounts(badges))}, " +
                "badge_type ${ThreadsShape.told(badgeTypes.groupingBy { it }.eachCount())}",
        )
        println("LIVE Threads post content $label candidates: ${ThreadsShape.told(ThreadsShape.paths(posts, CANDIDATES, depth = 6))}")
        val read = posts.mapNotNull { ThreadsParser.post(J(it))?.facts }
        println(
            "LIVE Threads post content $label read: language ${read.count { it.language != null }}, canTranslate ${read.count { it.canTranslate == true }}, " +
                "captionId ${read.count { it.captionId != null }}, views ${read.count { it.viewCount != null }}, ghost ${read.count { it.ghost != null }}, " +
                "attachment ${read.count { it.attachment != null }}, trends ${read.count { it.trends.isNotEmpty() }} (${read.sumOf { it.trends.size }} in all)",
        )
    }

    private fun kind(node: JsonElement): String = when (node) {
        is JsonArray -> "list"
        is JsonObject -> "object"
        is JsonPrimitive -> if (node.isString) "text" else "value"
    }

    private suspend fun page(http: OkHttpXHttp, url: HttpUrl, label: String): String? {
        delay(GAP_MILLIS)
        val response = try {
            http.get(url, HTML, followRedirects = false)
        } catch (e: IOException) {
            println("LIVE Threads post content page $label: unreachable (${e.javaClass.simpleName})")
            return null
        }
        if (response.code != 200) println("LIVE Threads post content page $label: ${response.code}")
        return response.body.takeIf { response.code == 200 }
    }

    /** A translation path asked as a guest: its status, where it sends them, and its top-level key names. */
    private suspend fun api(http: OkHttpXHttp, index: Int, url: HttpUrl) {
        delay(GAP_MILLIS)
        val shape = try {
            val response = http.get(url, JSON, followRedirects = false)
            val sent = response.header("Location")?.let(url::resolve)?.let { " -> ${it.host}${it.encodedPath}" }.orEmpty()
            val keys = (ThreadsJson.decode(response.body) as? JsonObject)?.keys?.sorted()?.joinToString(",") ?: "not JSON"
            "${response.code}$sent keys: $keys"
        } catch (e: IOException) {
            "unreachable (${e.javaClass.simpleName})"
        }
        println("LIVE Threads post content api translation path ${index + 1} (${url.encodedPath.replace(DIGITS, "<id>")}) as a guest: $shape")
    }

    private fun docIdNear(html: String, name: String): String? {
        val at = html.indexOf("\"$name\"").takeIf { it >= 0 } ?: return null
        return DOC_ID.find(html.substring(at, minOf(html.length, at + REACH)))?.groupValues?.get(1)
    }

    private companion object {
        val PAGES = listOf("search?q=threads&serp_type=tags" to "/search?q=threads&serp_type=tags", "@zuck" to "/@zuck")
        const val GAP_MILLIS = 2_500L
        const val REACH = 400
        val ANY = Regex(".")
        val CANDIDATES = Regex("view|impression|ghost|expir|text_attach|long_text|attached|stitch|translat")
        val CONTENT_QUERY = Regex("Barcelona[A-Za-z]*(Translat|ViewCount|Views|Ghost|Attachment|Stitch|Trend)[A-Za-z]*Query")
        val DOC_ID = Regex(""""(?:queryID|docID|doc_id|id)"\s*:\s*"(\d{8,})"""")
        val WORD = Regex("[a-z_]{1,40}", RegexOption.IGNORE_CASE)
        val LANGUAGE = Regex("[a-z]{2,3}([-_][A-Za-z0-9]{2,8})?")
        val DIGITS = Regex("\\d{4,}")
        val HTML = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        val JSON = mapOf("User-Agent" to ThreadsRepairs().webUserAgent, "Accept" to "application/json", "X-IG-App-ID" to ThreadsRepairs().appId)
    }
}
