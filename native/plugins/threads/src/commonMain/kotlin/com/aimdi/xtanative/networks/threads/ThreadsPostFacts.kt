package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject

/**
 * What Meta says about a post beside its words and media, each read as if
 * it might be gone: the emoji or picture of the community its topic belongs
 * to, how often it was shared, Meta's AI label, whether its author pinned
 * it, whether Meta asks for a check before it is shared, its poll, whether
 * its words were edited, whether the opened post's author liked it, who may
 * answer it, the song it plays, the language of its words and whether
 * Meta translates them, its views, whether it is a ghost post, the long
 * text attached to it and the trends it is part of ([ThreadsPostContent]).
 */
data class ThreadsPostFacts(
    val topicEmoji: String? = null,
    val topicIconUrl: String? = null,
    /** The topic is a community (`tag_header.is_community`, or a community's mark or id): shown beside the author, followable here. */
    val community: Boolean = false,
    /** The community's own id, when Meta named one. */
    val communityId: String? = null,
    /** The community's own page on threads.com, when Meta named one. */
    val communityUrl: String? = null,
    /** `reshare_count`: how often it was sent on, beside its reposts and quotes. */
    val shareCount: Long? = null,
    /** Meta named a way it found the post made or changed with AI (`gen_ai_detection_method`, not "NONE"). */
    val aiLabel: Boolean = false,
    val pinnedToProfile: Boolean = false,
    /** The author pinned this reply under their own post. */
    val pinnedToParent: Boolean = false,
    /** `sharing_friction_info.should_have_sharing_friction`: Meta asks for a check before it is shared. */
    val sharingFriction: Boolean = false,
    val poll: ThreadsPoll? = null,
    /** `caption_is_edited`: the author changed the words after posting them. */
    val edited: Boolean = false,
    /** `text_post_app_info.is_liked_by_root_author`: the author of the post this answers liked it, as threads.com marks a reply. */
    val likedByAuthor: Boolean = false,
    /** Who the author lets answer (`text_post_app_info.reply_control`); null for everyone, or a value not known. */
    val replyAudience: ThreadsReplyAudience? = null,
    /** The song the post plays (`text_app_music_info`), named only: nothing of it is played here. */
    val music: ThreadsMusic? = null,
    /** `detected_language` (seen live): the language Meta found its words in, what translation starts from. */
    val language: String? = null,
    /** `caption.can_translate` (seen live): false where Meta says its words cannot be translated; null where it did not say. */
    val canTranslate: Boolean? = null,
    /** `caption.pk`: the caption's own id, which Instagram's translation asks by. */
    val captionId: String? = null,
    /** How many times it was viewed, where Meta gives a public count. */
    val viewCount: Long? = null,
    /** Set on a ghost post, which Threads takes down after a day. */
    val ghost: ThreadsGhost? = null,
    /** The long text its author attached. */
    val attachment: ThreadsTextAttachment? = null,
    /** The trends it is part of (`text_post_app_info.related_trends_info`), at most [ThreadsPostContent.MAX_TRENDS]. */
    val trends: List<ThreadsTrend> = emptyList(),
) {
    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE = ThreadsPostFacts()
    }
}

/** Who the author of a post lets answer it, past everyone. */
enum class ThreadsReplyAudience {
    /** Only the accounts the author follows. */
    FOLLOWED,

    /** Only the author's followers. */
    FOLLOWERS,

    /** Only the accounts the post mentions. */
    MENTIONED,

    /** Only the author of the post this one answers. */
    AUTHOR,
    ;

    companion object {
        /**
         * The audience [value] names, as the app's and the public API's words
         * have it (`accounts_you_follow`, `followers_only`, `mentioned_only`,
         * `parent_post_author_only`); null for everyone, a number, or a word
         * not known, so nothing is claimed that Meta did not say.
         */
        fun of(value: String?): ThreadsReplyAudience? {
            val word = value?.trim()?.lowercase()?.ifEmpty { null } ?: return null
            return when {
                "everyone" in word || "anyone" in word || word.all(Char::isDigit) -> null
                "mention" in word -> MENTIONED
                "follower" in word -> FOLLOWERS
                "follow" in word -> FOLLOWED
                "author" in word || "parent" in word -> AUTHOR
                else -> null
            }
        }
    }
}

/** The song a post plays, as Meta names it; [imageUrl] its cover, over https only. */
data class ThreadsMusic(val title: String, val artist: String? = null, val imageUrl: String? = null) {
    /** "Title · Artist", or the title alone. */
    val line: String get() = listOfNotNull(title, artist).joinToString(" · ")
}

/** A poll's results as a guest sees them, each answer with its votes; nothing is voted from here. */
data class ThreadsPoll(val options: List<Option>, val endsAt: Instant? = null) {
    data class Option(val text: String, val votes: Long)

    fun closed(now: Instant): Boolean = endsAt?.let { !it.isAfter(now) } ?: false
}

/** Reads a post's [ThreadsPostFacts]; every field optional, an unknown shape none. */
object ThreadsPostFactsReader {
    fun of(post: J): ThreadsPostFacts {
        if (!post.exists) return ThreadsPostFacts.NONE
        val info = post["text_post_app_info"]
        val header = info["tag_header"]
        val pinned = info["pinned_post_info"]
        val emoji = header["community_emoji"].string?.trim()?.ifEmpty { null }
        val icon = header["community_favicon_url"].string?.trim()?.takeIf { it.startsWith("https://") }
        val id = ThreadsCommunities.id(header)
        return ThreadsPostFacts(
            topicEmoji = emoji,
            topicIconUrl = icon,
            community = ThreadsCommunities.isCommunity(header),
            communityId = id,
            communityUrl = ThreadsCommunities.url(header),
            shareCount = info["reshare_count"].integer?.takeIf { it >= 0 },
            aiLabel = aiLabel(post["gen_ai_detection_method"]),
            pinnedToProfile = pinned["is_pinned_to_profile"].boolean == true,
            pinnedToParent = pinned["is_pinned_to_parent_post"].boolean == true,
            sharingFriction = post["sharing_friction_info"]["should_have_sharing_friction"].boolean == true,
            poll = poll(post),
            edited = post["caption_is_edited"].boolean == true || post["caption"]["is_edited"].boolean == true,
            likedByAuthor = info["is_liked_by_root_author"].boolean == true,
            replyAudience = ThreadsReplyAudience.of(info["reply_control"].string ?: info["reply_control"]["mode"].string),
            music = music(post),
            language = ThreadsPostContent.language(post),
            canTranslate = ThreadsPostContent.canTranslate(post),
            captionId = ThreadsPostContent.captionId(post),
            viewCount = ThreadsPostContent.viewCount(post),
            ghost = ThreadsPostContent.ghost(post, postedAt(post)),
            attachment = ThreadsPostContent.attachment(post),
            trends = ThreadsPostContent.trends(post),
        )
    }

    private fun postedAt(post: J): Instant? = post["taken_at"].integer?.takeIf { it > 0 }?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }

    /**
     * The song a post plays: `text_app_music_info.music_consumable_video`'s
     * `audio_metadata.audio_asset` as CI's media probe saw it (its
     * `display_title.text`, `display_artist.text`, `display_image`), or
     * Instagram's `music_asset_info` where Meta answers in that shape. Null
     * without a title.
     */
    fun music(post: J): ThreadsMusic? {
        val info = post["text_app_music_info"]
        if (!info.exists) return null
        val asset = listOf(
            info["music_consumable_video"]["audio_metadata"]["audio_asset"],
            info["audio_metadata"]["audio_asset"],
            info["music_asset_info"],
            info["music_info"]["music_asset_info"],
        ).firstOrNull { it.raw is JsonObject } ?: return null
        val title = said(asset["display_title"]) ?: said(asset["title"]) ?: return null
        val artist = said(asset["display_artist"]) ?: said(asset["artist_name"]) ?: said(asset["display_artist_name"])
        val image = listOf(asset["display_image"], asset["large_display_image"]).firstNotNullOfOrNull { image ->
            (image["downloadable_uri"].string ?: image["uri"].string ?: image.string)?.trim()?.takeIf { it.startsWith("https://") }
        } ?: asset["cover_artwork_thumbnail_uri"].string?.trim()?.takeIf { it.startsWith("https://") }
        return ThreadsMusic(title, artist, image)
    }

    /** Words Meta gives as a string or as `{ "text": … }`. */
    private fun said(node: J): String? = (node.string ?: node["text"].string)?.trim()?.ifEmpty { null }

    /** The method as a word, or under `detection_method`; "NONE" and nothing are no label. */
    fun aiLabel(method: J): Boolean {
        val word = method.string ?: method["detection_method"].string
        return !word.isNullOrBlank() && !word.trim().equals("NONE", ignoreCase = true)
    }

    /**
     * The poll a post carries, under any name it has been seen with: a list
     * of options, or Threads' public API's `option_a` … `option_d` with
     * their percentages and `total_votes`. Null for fewer than two answers,
     * or when an answer's votes cannot be told.
     */
    fun poll(post: J): ThreadsPoll? {
        val info = post["text_post_app_info"]
        val node = listOf(info["poll"], info["poll_attachment"], post["poll"], post["poll_attachment"]).firstOrNull { it.raw is JsonObject }
            ?: return null
        val total = (node["total_votes"].integer ?: node["total_vote_count"].integer)?.takeIf { it >= 0 }
        val answers = listed(node).ifEmpty { lettered(node) }
        if (answers.size < 2) return null
        val percent = answers.any { (it.share ?: 0.0) > 1.0 }
        val options = answers.map { answer ->
            val votes = answer.votes ?: answer.share?.let { share -> total?.let { (it * (if (percent) share / 100 else share)).roundToLong() } }
            ThreadsPoll.Option(answer.text, votes?.coerceAtLeast(0) ?: return null)
        }
        return ThreadsPoll(options, endsAt(node))
    }

    private class Answer(val text: String, val votes: Long?, val share: Double?)

    private fun listed(node: J): List<Answer> =
        listOf("options", "poll_options", "tallies").map { node[it].list }.firstOrNull { it.isNotEmpty() }.orEmpty().mapNotNull { option ->
            val text = (option["text"].string ?: option["option_text"].string ?: option["label"].string)?.trim()?.ifEmpty { null }
            val votes = option["vote_count"].integer ?: option["count"].integer ?: option["votes"].integer
            text?.let { Answer(it, votes, option["percentage"].double ?: option["vote_percentage"].double) }
        }

    private fun lettered(node: J): List<Answer> = listOf("a", "b", "c", "d").mapNotNull { letter ->
        node["option_$letter"].string?.trim()?.ifEmpty { null }?.let { Answer(it, null, node["option_${letter}_votes_percentage"].double) }
    }

    /** When the poll ends: seconds since 1970, or a time as Meta's public API writes one. */
    private fun endsAt(node: J): Instant? {
        val value = listOf("expiration_timestamp", "expires_at", "end_time").map { node[it] }.firstOrNull { it.exists } ?: return null
        value.integer?.takeIf { it > 0 }?.let { seconds -> return runCatching { Instant.ofEpochSecond(seconds) }.getOrNull() }
        val text = value.string ?: return null
        return runCatching { OffsetDateTime.parse(text.replace(compactOffset, "$1:$2")).toInstant() }.getOrNull()
    }

    /** `+0000`, as Meta's public API writes an offset, for the `+00:00` [OffsetDateTime] reads. */
    private val compactOffset = Regex("""([+-]\d{2})(\d{2})$""")
}
