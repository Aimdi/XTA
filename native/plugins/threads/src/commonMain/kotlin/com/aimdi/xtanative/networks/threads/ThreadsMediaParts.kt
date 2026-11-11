package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject

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
}
