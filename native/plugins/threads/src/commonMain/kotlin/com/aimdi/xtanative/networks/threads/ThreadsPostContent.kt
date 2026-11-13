package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * A ghost post: one Threads takes down after a while, as its author asked.
 * [endsAt] is when, as Meta wrote it, else a day after it was posted (Meta's
 * rule for ghost posts); null when neither is known.
 */
data class ThreadsGhost(val endsAt: Instant? = null) {
    /** How long it has left at [now]; null when not known, zero once ended. */
    fun left(now: Instant): Duration? = endsAt?.let { Duration.between(now, it).coerceAtLeast(Duration.ZERO) }

    companion object {
        /** How long Threads keeps a ghost post up: a day. */
        val LIFETIME: Duration = Duration.ofHours(24)
    }
}

/**
 * The long text an author attached to a post (Threads' text attachment, up
 * to ten thousand characters beside the post's own five hundred): its
 * [text], a [title] where Meta gave one, and the link it carries, over
 * https only.
 */
data class ThreadsTextAttachment(val text: String, val title: String? = null, val linkUrl: String? = null)

/**
 * Reads what a post carries beside its caption that this round draws: the
 * language Meta found its words in and whether Meta offers to translate
 * them (both seen live: `detected_language`, `caption.can_translate`), its
 * view count, whether it is a ghost post, its attached long text, and the
 * trends it is part of (`text_post_app_info.related_trends_info`, seen
 * live). No probe has seen the keys of a view count, a ghost post or a text
 * attachment yet, so each is read under every name it is likely to take and
 * only where it holds what it should; anything else is none. Nothing here
 * throws.
 */
object ThreadsPostContent {
    private const val MILLIS_FROM = 100_000_000_000L
    private val LANGUAGE = Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*")

    /** `detected_language`, or the caption's own `lang`: a language tag, or null for anything else. */
    fun language(post: J): String? = listOf(post["detected_language"], post["caption"]["lang"], post["lang"])
        .firstNotNullOfOrNull { it.string?.trim()?.takeIf(LANGUAGE::matches) }

    /** `caption.can_translate`: false where Meta says the words cannot be translated, null where it did not say. */
    fun canTranslate(post: J): Boolean? = post["caption"]["can_translate"].boolean

    /** The caption's own id (`caption.pk`), which Instagram's translation asks by; null without one. */
    fun captionId(post: J): String? {
        val pk = post["caption"]["pk"]
        return (pk.string ?: pk.integer?.toString())?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    /** How many times the post was viewed, where Meta gives a public count: a number under the names a count is likely to take. */
    fun viewCount(post: J): Long? {
        val info = post["text_post_app_info"]
        val direct = VIEW_KEYS.flatMap { listOf(post[it], info[it]) }
        val nested = VIEW_HOLDERS.flatMap { holder -> listOf(post[holder], info[holder]) }.flatMap { holder -> COUNT_KEYS.map { holder[it] } }
        return (direct + nested).firstNotNullOfOrNull { it.integer?.takeIf { count -> count >= 0 } }
    }

    private val VIEW_KEYS = listOf("view_count", "views_count", "public_view_count", "impression_count")
    private val VIEW_HOLDERS = listOf("view_count_info", "view_counts", "views_info", "public_view_count_info")
    private val COUNT_KEYS = listOf("count", "view_count", "value", "total")

    /**
     * The post's ghost mark: a ghost flag (`is_ghost_post` and its likely
     * kin) or a ghost object, on the post or its `text_post_app_info`; when
     * it ends, from the ghost object, else the post's own expiry once it is
     * a ghost, else a day after [postedAt]. Null for a post that is none.
     */
    fun ghost(post: J, postedAt: Instant?): ThreadsGhost? {
        val info = post["text_post_app_info"]
        val holders = listOf(post, info)
        val objects = holders.flatMap { holder -> GHOST_OBJECTS.map { holder[it] } }.filter { (it.raw as? JsonObject)?.isNotEmpty() == true }
        val flagged = holders.any { holder -> GHOST_FLAGS.any { holder[it].boolean == true } } ||
            objects.any { it["is_ghost_post"].boolean != false && it["is_ghost"].boolean != false }
        if (!flagged) return null
        val written = (objects + holders).firstNotNullOfOrNull { holder -> EXPIRY_KEYS.firstNotNullOfOrNull { instant(holder[it]) } }
        return ThreadsGhost(written ?: postedAt?.plus(ThreadsGhost.LIFETIME))
    }

    private val GHOST_FLAGS = listOf("is_ghost_post", "is_ghost", "ghost_post")
    private val GHOST_OBJECTS = listOf("ghost_post_info", "ghost_info", "ghost_post_metadata")
    private val EXPIRY_KEYS = listOf("expiration_timestamp", "expires_at", "expiring_at", "expiry_timestamp", "expiration_time", "ghost_post_expiration_timestamp")

    /** A time Meta writes as seconds (or milliseconds) since 1970. */
    private fun instant(value: J): Instant? {
        val n = value.integer?.takeIf { it > 0 } ?: return null
        return runCatching { if (n >= MILLIS_FROM) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n) }.getOrNull()
    }

    /**
     * The long text attached to the post: `text_attachment` (the public
     * API's name, with its `plaintext` and `link_attachment_url`) or its
     * likely kin, on `text_post_app_info` or the post; its words as plain
     * text, as a `text` or `body`, or as fragments like a caption's. Null
     * without words.
     */
    fun attachment(post: J): ThreadsTextAttachment? {
        val info = post["text_post_app_info"]
        val node = ATTACHMENT_KEYS.flatMap { listOf(info[it], post[it]) }.firstOrNull { it.raw is JsonObject || it.string != null } ?: return null
        val text = (node.string ?: ATTACHMENT_TEXT_KEYS.firstNotNullOfOrNull { said(node[it]) } ?: fragments(node))
            ?.trim()?.ifEmpty { null } ?: return null
        val link = listOf("link_attachment_url", "url", "link")
            .firstNotNullOfOrNull { node[it].string?.trim()?.ifEmpty { null } }
            ?.let(ThreadsLinks::unwrapOutbound)?.takeIf { it.startsWith("https://") }
        return ThreadsTextAttachment(text, said(node["title"]), link)
    }

    private val ATTACHMENT_KEYS = listOf("text_attachment", "long_text_attachment", "text_attachment_info", "attached_text")
    private val ATTACHMENT_TEXT_KEYS = listOf("plaintext", "text", "body", "content")

    private fun fragments(node: J): String? =
        node["text_fragments"]["fragments"].list.mapNotNull { it["plaintext"].string }.joinToString("").ifEmpty { null }

    /** Words Meta gives as a string or as `{ "text": … }`. */
    private fun said(node: J): String? = (node.string ?: node["text"].string)?.trim()?.ifEmpty { null }

    /** How many trends a post names at most. */
    const val MAX_TRENDS = 3

    /**
     * The trends the post is part of (`text_post_app_info.related_trends_info`,
     * seen live, its items' keys not), read as [ThreadsTrends.related]
     * reads one: each once, at most [MAX_TRENDS].
     */
    fun trends(post: J): List<ThreadsTrend> =
        ThreadsTrends.related(post["text_post_app_info"]["related_trends_info"].raw).take(MAX_TRENDS)
}
