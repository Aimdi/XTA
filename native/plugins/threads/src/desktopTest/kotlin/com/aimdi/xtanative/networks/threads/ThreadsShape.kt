package com.aimdi.xtanative.networks.threads

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup

/**
 * The shape of what a Threads page embeds, for the live probes: key names,
 * key paths and counts only. No value is ever read out, so nothing a post
 * says, no name and no token reaches a log. A key that is not a plain
 * lower-case identifier (an id, a handle used as a key) is told as `<other>`.
 */
object ThreadsShape {
    /** The post fields [ThreadsParser] reads. */
    val READ_POST = setOf(
        "caption", "user", "carousel_media", "image_versions2", "original_width", "original_height",
        "accessibility_caption", "video_versions", "giphy_media_info", "text_post_app_info", "code", "pk", "id",
        "like_count", "taken_at", "caption_is_edited", "text_app_music_info",
    )

    /** The `text_post_app_info` fields [ThreadsParser] reads. */
    val READ_INFO = setOf(
        "share_info", "tag_header", "text_fragments", "link_preview_attachment", "direct_reply_count",
        "repost_count", "quote_count", "reply_to_author", "is_reply", "reply_level", "direct_replies",
        "self_thread", "containing_thread", "is_liked_by_root_author", "reply_control",
    )

    /** What a reader would notice if it were drawn: the key names worth a path. */
    val FEATURES = Regex("poll|giphy|gif|quote|link_preview|carousel|audio|music|sticker|attachment|translat|spoiler|label|badge|verified|ai_|community|reply_control|hide|pinned")

    /** Where a page might name a next page of replies or posts. */
    val PAGING = Regex("cursor|has_next|next_max|more_available|page_info|paging")

    private val plainKey = Regex("[a-z_][a-z0-9_]{0,47}")
    private val preloaded = Regex(""""adp_([A-Za-z]+)""")

    /** The relay queries a page preloaded (`adp_BarcelonaPostPageDirectQuery…`), by name. */
    fun queries(html: String): List<String> = preloaded.findAll(html).map { it.groupValues[1] }.distinct().toList()

    /** Every JSON tree a page's `data-sjs` scripts hold. */
    fun scripts(html: String): List<JsonElement> =
        Jsoup.parse(html).select("script[data-sjs]").mapNotNull { ThreadsJson.decode(it.data().trim()) }

    /** Every object under [roots] that looks like a Meta post: an author and a time. */
    fun posts(roots: List<JsonElement>): List<JsonObject> = roots.flatMap { objects(it) }
        .filter { "user" in it && "taken_at" in it && ("caption" in it || "code" in it) }

    /** Every object under [roots] that looks like a user: a username and a picture. */
    fun users(roots: List<JsonElement>): List<JsonObject> = roots.flatMap { objects(it) }
        .filter { "username" in it && "profile_pic_url" in it }

    /** How many of [objects] carry each key with something in it, most first. */
    fun keyCounts(objects: List<JsonObject>): Map<String, Int> = objects
        .flatMap { o -> o.entries.filter { (_, v) -> filled(v) }.map { name(it.key) } }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.associate { it.key to it.value }

    /** The key paths under [roots] whose last key matches [pattern] and holds something, with how often. */
    fun paths(roots: List<JsonElement>, pattern: Regex, depth: Int = 8, tail: Int = 6): Map<String, Int> = roots
        .flatMap { walk(it, "", depth) }
        .filter { (path, value) -> pattern.containsMatchIn(path.substringAfterLast('.')) && filled(value) }
        .groupingBy { it.first.split('.').filter(String::isNotEmpty).takeLast(tail).joinToString(".") }.eachCount()

    /** `a=3, b=1`, at most [limit] entries. */
    fun told(counts: Map<String, Int>, limit: Int = 40): String =
        counts.entries.take(limit).joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "none" }

    private fun objects(node: JsonElement): List<JsonObject> = when (node) {
        is JsonObject -> listOf(node) + node.values.flatMap(::objects)
        is JsonArray -> node.flatMap(::objects)
        else -> emptyList()
    }

    private fun walk(node: JsonElement, path: String, depth: Int): List<Pair<String, JsonElement>> {
        if (depth < 0) return emptyList()
        return when (node) {
            is JsonObject -> node.entries.flatMap { (key, value) ->
                val here = "$path.${name(key)}"
                listOf(here to value) + walk(value, here, depth - 1)
            }
            is JsonArray -> node.flatMap { walk(it, "$path[]", depth - 1) }
            else -> emptyList()
        }
    }

    private fun name(key: String): String = if (plainKey.matches(key)) key else "<other>"

    private fun filled(value: JsonElement): Boolean = when (value) {
        is JsonNull -> false
        is JsonObject -> value.isNotEmpty()
        is JsonArray -> value.isNotEmpty()
        is JsonPrimitive -> value.content.isNotEmpty() && value.content != "false"
    }
}
