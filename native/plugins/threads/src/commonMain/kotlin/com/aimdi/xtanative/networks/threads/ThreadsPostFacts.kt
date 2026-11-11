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
 * it, whether Meta asks for a check before it is shared, and its poll.
 */
data class ThreadsPostFacts(
    val topicEmoji: String? = null,
    val topicIconUrl: String? = null,
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
) {
    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE = ThreadsPostFacts()
    }
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
        return ThreadsPostFacts(
            topicEmoji = header["community_emoji"].string?.trim()?.ifEmpty { null },
            topicIconUrl = header["community_favicon_url"].string?.trim()?.takeIf { it.startsWith("https://") },
            shareCount = info["reshare_count"].integer?.takeIf { it >= 0 },
            aiLabel = aiLabel(post["gen_ai_detection_method"]),
            pinnedToProfile = pinned["is_pinned_to_profile"].boolean == true,
            pinnedToParent = pinned["is_pinned_to_parent_post"].boolean == true,
            sharingFriction = post["sharing_friction_info"]["should_have_sharing_friction"].boolean == true,
            poll = poll(post),
        )
    }

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
