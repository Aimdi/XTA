package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant

/** The kind of work a comment thread hangs off: artworks and novels answer on parallel paths, each with its own id parameter. */
enum class PixivCommentWork(val listPath: String, val idField: String, val repliesPath: String) {
    ILLUST("/v3/illust/comments", "illust_id", "/v2/illust/comment/replies"),
    NOVEL("/v3/novel/comments", "novel_id", "/v2/novel/comment/replies"),
}

/** The work whose comments are read: an artwork or a novel. */
data class PixivCommentTarget(val work: PixivCommentWork, val id: Long) {
    companion object {
        fun illust(id: Long) = PixivCommentTarget(PixivCommentWork.ILLUST, id)

        fun novel(id: Long) = PixivCommentTarget(PixivCommentWork.NOVEL, id)
    }
}

/** One comment as the comment and reply endpoints send it. */
data class PixivComment(
    val id: Long,
    val text: String,
    val date: Instant? = null,
    /** Null when Pixiv sent no usable author, as for a withdrawn account. */
    val user: PixivUser? = null,
    /** Whom this comment answers, when it is a reply. */
    val replyTo: PixivUser? = null,
    val hasReplies: Boolean = false,
    /** The sticker a comment is made of instead of text. */
    val stampUrl: String? = null,
) {
    /** The name its author goes by: their name, else their @account. */
    val authorName: String?
        get() = user?.let { author -> author.name.ifEmpty { author.account.takeIf { it.isNotEmpty() }?.let { "@$it" } } }

    /** Links away from Pixiv are how comment spam reaches readers. */
    val linksOutsidePixiv: Boolean get() = PixivCommentText.linksOutside(text)

    /** The text in runs of words and Pixiv's emoji. */
    val parts: List<PixivCommentPart> get() = PixivCommentText.parts(text)

    companion object {
        /** One comment object, or null without an id to tell it apart. */
        fun parse(data: J): PixivComment? {
            val id = data["id"].long ?: return null
            return PixivComment(
                id = id,
                text = data["comment"].string.orEmpty(),
                date = PixivText.instant(data["date"].string),
                user = author(data["user"]),
                replyTo = author(data["parent_comment"]["user"]),
                hasReplies = data["has_replies"].boolean == true,
                stampUrl = PixivText.nonEmpty(data["stamp"]["stamp_url"].string),
            )
        }

        private fun author(user: J): PixivUser? = PixivUser.parse(user).takeIf { it.id > 0 }
    }
}

/** One page of comments, and how many the work has: only a work's first page says. */
data class PixivCommentPage(val comments: Page<PixivComment>, val total: Long? = null) {
    companion object {
        /** Comments without an id are skipped; a missing or reshaped page is empty. */
        fun parse(json: J): PixivCommentPage = PixivCommentPage(
            Page(json["comments"].list.mapNotNull(PixivComment::parse), nextOf(json)),
            json["total_comments"].long,
        )
    }
}

/** A run of a comment: plain text, or one of Pixiv's emoji. */
sealed interface PixivCommentPart {
    data class Text(val text: String) : PixivCommentPart

    data class Emoji(val name: String, val id: Int) : PixivCommentPart {
        /** Where Pixiv's own site loads it from, so no picture ships with the app. */
        val url: String get() = "https://s.pximg.net/common/images/emoji/$id.png"

        /** The code as the commenter typed it. */
        val code: String get() = "($name)"
    }
}

/** Reading a comment's text: Pixiv's emoji codes, and links that lead away from Pixiv. */
object PixivCommentText {
    /** Pixiv's comment emoji, written `(name)`, in the five sets Pixiv numbers them in: the n-th of set s is s × 100 + n. */
    private val sets = listOf(
        listOf("normal", "surprise", "serious", "heaven", "happy", "excited", "sing", "cry"),
        listOf("normal2", "shame2", "love2", "interesting2", "blush2", "fire2", "angry2", "shine2", "panic2"),
        listOf("normal3", "satisfaction3", "surprise3", "smile3", "shock3", "gaze3", "wink3", "happy3", "excited3", "love3"),
        listOf("normal4", "surprise4", "serious4", "love4", "shine4", "sweat4", "shame4", "sleep4"),
        listOf("heart", "teardrop", "star"),
    )

    /** Every emoji name with the number Pixiv serves its picture under. */
    val emojiIds: Map<String, Int> = sets.flatMapIndexed { set, names ->
        names.mapIndexed { index, name -> name to (set + 1) * 100 + index + 1 }
    }.toMap()

    private val emojiCode = Regex("""\(([a-z0-9]+)\)""")

    /** [text] as text and emoji runs. A `(name)` Pixiv does not know stays text, as do parentheses around words. */
    fun parts(text: String): List<PixivCommentPart> {
        val known = emojiCode.findAll(text).mapNotNull { match ->
            emojiIds[match.groupValues[1]]?.let { match.range to PixivCommentPart.Emoji(match.groupValues[1], it) }
        }.toList()
        val (parts, plainFrom) = known.fold(emptyList<PixivCommentPart>() to 0) { (done, from), (range, emoji) ->
            val before = if (range.first > from) listOf(PixivCommentPart.Text(text.substring(from, range.first))) else emptyList()
            done + before + emoji to range.last + 1
        }
        return if (plainFrom < text.length) parts + PixivCommentPart.Text(text.substring(plainFrom)) else parts
    }

    /** Pixiv's own sites; a link to any of them is a reader pointing at a work. */
    private val pixivHosts = setOf("pixiv.net", "pixiv.me", "pximg.net", "pixivision.net", "fanbox.cc", "booth.pm")

    /**
     * A link with its scheme, a bare `www.` address, or a bare `host.tld/`;
     * groups 1 to 3 hold each one's host. The `www.` form needs a dotted host
     * after it, since "www" is also how Japanese comments laugh; the bare form
     * needs the slash, so "pixiv.netで見ました" is not a link.
     */
    private val link = Regex(
        """https?://([^${PixivText.SPACE}/?#<>"\\]*)""" +
            """|(?<![A-Za-z0-9_])www\.([a-z0-9\-]+(?:\.[a-z0-9\-]+)+[^${PixivText.SPACE}/?#<>"\\]*)""" +
            """|(?<![a-z0-9.\-/])([a-z0-9\-]+(?:\.[a-z0-9\-]+)*\.[a-z]{2,})(?=/)""",
        RegexOption.IGNORE_CASE,
    )

    private val asciiHost = Regex("""^[a-z0-9.\-]*""")

    /** A dot a browser reads into a host (plain, full-width or percent-encoded) followed by another label. */
    private val hostGoesOn = Regex("""(?:[.。．｡]|%2e)[\p{L}\p{N}]""")

    /** Whether [text] links anywhere but Pixiv's own sites: the spam guard, with no list of bad domains to keep. */
    fun linksOutside(text: String): Boolean = link.findAll(text).any { match ->
        !isPixivHost(match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value.orEmpty())
    }

    /**
     * A browser goes to what follows the last `@` (`pixiv.net@evil.example` is
     * evil.example), and a dot after the plain ASCII start carries the host on
     * to another domain (`pixiv.net。evil.example`). A sentence glued to a
     * host without such a dot, as Japanese comments write, does not.
     */
    private fun isPixivHost(authority: String): Boolean {
        val host = authority.substring(authority.lastIndexOf('@') + 1).lowercase()
        val ascii = asciiHost.find(host)?.value.orEmpty()
        if (hostGoesOn.containsMatchIn(host.substring(if (ascii.isEmpty()) 0 else ascii.length - 1))) return false
        val bare = ascii.removePrefix("www.").trimEnd('.')
        return bare.isNotEmpty() && pixivHosts.any { bare == it || bare.endsWith(".$it") }
    }
}

/** Comments and replies on artworks and novels, read. There is deliberately no way to post one. */
class PixivCommentsApi(private val client: PixivClient) {
    /** [target]'s comments, in the order Pixiv lists them. */
    suspend fun comments(target: PixivCommentTarget, next: String? = null): PixivCommentPage =
        PixivCommentPage.parse(client.getPage(target.work.listPath, mapOf(target.work.idField to "${target.id}"), next))

    /** The replies under comment [commentId] on [target]. */
    suspend fun replies(target: PixivCommentTarget, commentId: Long, next: String? = null): PixivCommentPage =
        PixivCommentPage.parse(client.getPage(target.work.repliesPath, mapOf("comment_id" to "$commentId"), next))
}
