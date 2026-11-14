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
 * `video_versions`; and the shape of the songs a few pages carry
 * ([theShapeOfTheSongsAGuestIsShown]). Key names, kinds and counts only; never an address or
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

    /**
     * The songs of a few pages likely to carry them (searches for "music"
     * and "song", the Music Threads community's topic, the topic page where
     * CI saw one): the key paths under `text_app_music_info` with their
     * kinds, Meta's words for why a song is muted, what its DASH manifest
     * holds (audio sets, single files, segments, a length), and what
     * [ThreadsMusicReader] made of them. Names, kinds and counts only.
     */
    @Test
    fun theShapeOfTheSongsAGuestIsShown(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val pages = listOf(
            "search music" to ThreadsLinks.searchUrl("music"),
            "search song" to ThreadsLinks.searchUrl("song"),
            "topic Music Threads" to ThreadsLinks.searchUrl("Music Threads", tag = true),
            "topic threads" to ThreadsLinks.searchUrl("threads", tag = true),
        )
        for ((label, url) in pages) {
            delay(GAP_MILLIS)
            val html = try {
                http.get(url, HEADERS, followRedirects = false).takeIf { it.code == 200 }?.body
            } catch (e: IOException) {
                null
            }
            if (html == null) println("LIVE Threads songs $label: no page") else songs(label, ThreadsShape.posts(ThreadsShape.scripts(html)))
        }
    }

    private fun songs(label: String, posts: List<JsonObject>) {
        val infos = posts.mapNotNull { it["text_app_music_info"] as? JsonObject }
        println("LIVE Threads songs $label: ${posts.size} posts, ${infos.size} with text_app_music_info")
        if (infos.isEmpty()) return
        println("LIVE Threads songs $label keys: ${ThreadsShape.told(kinds(infos), limit = 80)}")
        val muted = infos.mapNotNull { (it["muted_reason"] as? JsonPrimitive)?.content?.let { word -> if (WORD.matches(word)) word else "<other>" } }.groupingBy { it }.eachCount()
        val available = infos.mapNotNull { (it["is_available_for_consumption"] as? JsonPrimitive)?.content }.groupingBy { it }.eachCount()
        println("LIVE Threads songs $label muted_reason ${ThreadsShape.told(muted)}, is_available_for_consumption ${ThreadsShape.told(available)}")
        manifests(infos).take(5).forEach { manifest ->
            val xml = manifest.contains("<MPD", ignoreCase = true)
            println(
                "LIVE Threads songs $label dash: ${if (xml) "inline" else if (manifest.startsWith("https://")) "address" else "other"}, " +
                    "${Regex("<AdaptationSet\\b").findAll(manifest).count()} sets, ${Regex("""contentType="audio"|mimeType="audio""").findAll(manifest).count()} audio marks, " +
                    "${REPRESENTATION.findAll(manifest).count()} representations, ${Regex("<BaseURL").findAll(manifest).count()} BaseURL, " +
                    "SegmentTemplate ${manifest.contains("<SegmentTemplate")}, SegmentBase ${manifest.contains("<SegmentBase")}, " +
                    "length ${ThreadsDash.durationMs(manifest) != null}, file read ${ThreadsDash.audioFile(manifest) != null}",
            )
        }
        val read = posts.mapNotNull { ThreadsMusicReader.of(com.aimdi.xtanative.core.json.J(it)) }
        println(
            "LIVE Threads songs $label read: ${read.size} songs, ${read.count { it.playable }} playable, ${read.count { it.artist != null }} with artists, " +
                "${read.count { it.largeImageUrl != null }} large covers, ${read.count { it.durationMs != null }} lengths, ${read.count { it.startMs > 0 }} starts, " +
                "${read.count { it.lengthMs != null }} preview lengths, ${read.count { it.explicit }} explicit, ${read.count { it.pageUrl != null }} pages",
        )
    }

    /** Every key path under [roots] (ids told as `<other>`) with the kinds it holds: `a.b=text:2`. */
    private fun kinds(roots: List<JsonElement>): Map<String, Int> {
        fun walk(node: JsonElement, path: String, depth: Int): List<String> = when {
            depth < 0 -> emptyList()
            node is JsonObject -> node.entries.flatMap { (key, value) ->
                val here = if (path.isEmpty()) name(key) else "$path.${name(key)}"
                listOf("$here=${kind(value)}") + walk(value, here, depth - 1)
            }
            node is JsonArray -> node.take(3).flatMap { walk(it, "$path[]", depth - 1) }
            else -> emptyList()
        }
        return roots.flatMap { walk(it, "", 6).distinct() }.groupingBy { it }.eachCount()
    }

    private fun name(key: String): String = if (Regex("[a-z_][a-z0-9_]{0,47}").matches(key)) key else "<other>"

    private fun kind(value: JsonElement): String = when (value) {
        is JsonObject -> "object"
        is JsonArray -> "list"
        is JsonPrimitive -> when {
            value is kotlinx.serialization.json.JsonNull -> "null"
            value.isString -> if (value.content.isEmpty()) "empty" else "text"
            value.content == "true" || value.content == "false" -> "flag"
            else -> "number"
        }
    }

    private fun manifests(infos: List<JsonObject>): List<String> = infos.flatMap { info ->
        listOf(
            listOf("music_consumable_video", "audio_metadata", "audio_asset", "dash_manifest"),
            listOf("music_asset_info", "dash_manifest"),
        ).mapNotNull { path ->
            path.fold(info as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
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
        val WORD = Regex("[A-Za-z_]{1,40}")
        val HEADERS = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
    }
}
