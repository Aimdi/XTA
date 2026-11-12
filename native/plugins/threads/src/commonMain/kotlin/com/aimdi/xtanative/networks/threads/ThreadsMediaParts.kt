package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The media a post carries beyond its own `image_versions2` and carousel,
 * read as harmlessly as the rest: each answers nothing when the key is gone.
 */
object ThreadsMediaParts {
    /**
     * A post's own pictures and videos, else the media Meta links into it
     * (`text_post_app_info.linked_inline_media`, seen live with `has_audio`):
     * read as a post's media is, so a link that carries no picture adds nothing.
     */
    fun of(post: J): List<ThreadsMedia> =
        ThreadsParser.media(post).ifEmpty { ThreadsParser.media(post["text_post_app_info"]["linked_inline_media"]) }

    /**
     * Whether Meta lays a cover over the post's media (`media_overlay_info`,
     * the key Instagram's sensitive-media and fact-check covers use, seen live
     * on Threads): any non-empty object there. What it says is not read.
     */
    fun covered(post: J): Boolean = (post["media_overlay_info"].raw as? JsonObject)?.isNotEmpty() == true

    /**
     * Meta's own words on that cover: its `title` and `description`, else the
     * first of its `buttons` that carries a `headline_text`, with its
     * `secondary_text` (the keys seen live; their values were not logged, so
     * only text is read, each line at most [COVER_WORDS] characters). Null
     * without any, and the cover then says it in the plugin's words.
     */
    fun coverWords(post: J): String? {
        val cover = post["media_overlay_info"].takeIf { it.raw is JsonObject } ?: return null
        val button = cover["buttons"].list.firstOrNull { words(it["headline_text"]) != null }
        val lines = listOf(words(cover["title"]), words(cover["description"])).filterNotNull()
            .ifEmpty { listOfNotNull(button?.let { words(it["headline_text"]) }, button?.let { words(it["secondary_text"]) }) }
        return lines.distinct().joinToString("\n").ifEmpty { null }
    }

    private fun words(value: J): String? =
        (value.raw as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.take(COVER_WORDS)?.ifEmpty { null }

    private const val COVER_WORDS = 200
}
