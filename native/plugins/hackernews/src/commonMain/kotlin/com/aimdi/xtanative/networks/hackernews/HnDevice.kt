package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the plugin keeps of Hacker News on the device, as XTA's hn_store.dart
 * keeps it in its preferences: the stories hearted (their ids), the stories
 * saved (whole, newest first) and the accounts followed (their names). Nothing
 * of it reaches Hacker News; anything damaged reads as nothing kept.
 */
object HnDevice {
    const val LIKES_CAP = 400
    const val SAVED_CAP = 200
    const val FOLLOWS_CAP = 80

    /** How many stories Following shows: two pages' worth. */
    const val FOLLOWING_LIMIT = HackerNewsClient.PAGE_SIZE * 2

    /** A JSON list of strings, as the hearts and the follows are kept. */
    fun strings(raw: String?): List<String> = J.parse(raw.orEmpty()).list.mapNotNull { it.string }.distinct()

    fun stringsText(items: Collection<String>): String = JsonArray(items.map(::JsonPrimitive)).toString()

    /** [id] hearted, or no longer; past [LIKES_CAP], the newest heart is the one that does not stay, as in XTA. */
    fun toggleLike(liked: List<String>, id: Long): List<String> {
        val key = "$id"
        return (if (key in liked) liked - key else liked + key).take(LIKES_CAP)
    }

    /** The accounts followed, by name, as XTA lists them. */
    fun follows(raw: String?): List<String> = strings(raw).sorted()

    fun isFollowing(follows: List<String>, id: String): Boolean = follows.any { it.equals(id.trim(), ignoreCase = true) }

    /** [id] followed, first, or no longer, whatever its case; nothing changes for a blank name. */
    fun toggleFollow(follows: List<String>, id: String): List<String> {
        val key = id.trim()
        if (key.isEmpty()) return follows
        return if (isFollowing(follows, key)) follows.filterNot { it.equals(key, ignoreCase = true) } else (listOf(key) + follows).take(FOLLOWS_CAP)
    }

    /** The stories saved, newest first. */
    fun saved(raw: String?): List<HnStory> = J.parse(raw.orEmpty()).list.mapNotNull(::story)

    fun savedText(stories: List<HnStory>): String = JsonArray(stories.map(::json)).toString()

    fun isSaved(saved: List<HnStory>, id: Long): Boolean = saved.any { it.id == id }

    /** [story] saved first, or no longer; at most [SAVED_CAP] are kept. */
    fun toggleSaved(saved: List<HnStory>, story: HnStory): List<HnStory> =
        (listOfNotNull(story.takeUnless { isSaved(saved, it.id) }) + saved.filter { it.id != story.id }).take(SAVED_CAP)

    /** The followed accounts' stories, one each, newest first, at most [FOLLOWING_LIMIT]. */
    fun following(pages: List<List<HnStory>>): List<HnStory> =
        pages.flatten().associateBy { it.id }.values.sortedByDescending { it.createdAt ?: Instant.EPOCH }.take(FOLLOWING_LIMIT)

    /** A story as XTA's HnStory.toJson writes it. */
    fun json(story: HnStory): JsonObject = buildJsonObject {
        put("id", story.id)
        put("title", story.title)
        put("url", story.url)
        put("text", story.text)
        put("author", story.author)
        put("score", story.score)
        put("commentCount", story.commentCount)
        put("createdAt", story.createdAt?.toString())
        put("type", story.type)
    }

    /** A story XTA saved; null without an id. */
    fun story(json: J): HnStory? {
        val id = json["id"].long ?: return null
        return HnStory(
            id = id,
            title = json["title"].string.orEmpty(),
            url = json["url"].string,
            text = json["text"].string.orEmpty(),
            author = json["author"].string,
            score = json["score"].int ?: 0,
            commentCount = json["commentCount"].int ?: 0,
            createdAt = json["createdAt"].string?.let { runCatching { Instant.parse(it) }.getOrNull() },
            type = json["type"].string ?: "story",
        )
    }
}
