package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The shape of the media Threads shows a guest, for the next media round to
 * read in CI's log: the kinds of post (`media_type`), the keys under the
 * cover Meta lays over media, a post's music, its linked media and each
 * video file, and how many files a video's DASH manifest names beside
 * `video_versions`. Key names, kinds and counts only; never an address or
 * anything a post says. Skipped unless RUN_LIVE=true.
 */
class ThreadsMediaShapeLiveTest {
    @Test
    fun theShapeOfTheMediaAGuestIsShown(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        for (path in listOf("@zuck", "@instagram", "search?q=threads&serp_type=tags", "search?q=video")) {
            delay(GAP_MILLIS)
            val html = try {
                http.get(ThreadsLinks.web("/").resolve(path)!!, HEADERS, followRedirects = false).takeIf { it.code == 200 }?.body
            } catch (e: IOException) {
                null
            }
            if (html == null) println("LIVE Threads media $path: no page") else log(path, ThreadsShape.posts(ThreadsShape.scripts(html)))
        }
    }

    private fun log(label: String, posts: List<JsonObject>) {
        val kinds = posts.groupingBy { (it["media_type"] as? JsonPrimitive)?.content ?: "none" }.eachCount()
        println("LIVE Threads media $label: ${posts.size} posts, media_type ${ThreadsShape.told(kinds)}")
        for (key in listOf("media_overlay_info", "text_app_music_info", "audio", "video_versions", "carousel_media")) {
            val under = posts.mapNotNull { it[key] }
            if (under.isNotEmpty()) println("LIVE Threads media $label $key: ${ThreadsShape.told(ThreadsShape.paths(under, ANY, depth = 4, tail = 3), limit = 60)}")
        }
        val linked = posts.mapNotNull { (it["text_post_app_info"] as? JsonObject)?.get("linked_inline_media") }
        if (linked.isNotEmpty()) println("LIVE Threads media $label linked_inline_media: ${ThreadsShape.told(ThreadsShape.paths(linked, ANY, depth = 2, tail = 2), limit = 60)}")
        posts.filter { it["video_dash_manifest"] is JsonPrimitive }.take(5).forEach { post ->
            val manifest = (post["video_dash_manifest"] as JsonPrimitive).content
            val files = (post["video_versions"] as? JsonArray)?.size ?: 0
            val audio = Regex("""contentType="audio"|mimeType="audio""").containsMatchIn(manifest)
            println("LIVE Threads media $label dash: ${REPRESENTATION.findAll(manifest).count()} representations, audio ${if (audio) "apart" else "none named"}, ${files} video_versions, number_of_qualities ${number(post["number_of_qualities"])}, has_audio ${number(post["has_audio"])}")
        }
    }

    private fun number(value: JsonElement?): String = (value as? JsonPrimitive)?.content?.takeIf { it.length <= 5 } ?: "-"

    private companion object {
        const val GAP_MILLIS = 2_500L
        val ANY = Regex(".")
        val REPRESENTATION = Regex("<Representation\\b")
        val HEADERS = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
    }
}
