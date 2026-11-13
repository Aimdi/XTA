package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant

/** What happened in a line of the signed-in reader's activity, as Threads marks it. */
enum class ThreadsActivityKind {
    REPLY,
    MENTION,
    QUOTE,
    REPOST,
    LIKE,
    FOLLOW,

    /** Anything Meta names in words no kind above matches. */
    OTHER,
}

/** The part of the activity asked for: everything, or one of the filters Threads' Activity offers. */
enum class ThreadsActivityFilter(val selected: String?) {
    ALL(null),
    REPLIES("text_post_app_replies"),
    MENTIONS("text_post_app_mentions"),
}

/**
 * One line of the reader's activity: Meta's own sentence for it ("ann
 * replied to your thread"), who did it and their face, when, and the post it
 * is about, by its short code, where Meta names one.
 */
data class ThreadsActivityItem(
    val id: String,
    val kind: ThreadsActivityKind,
    val text: String,
    val handle: String? = null,
    val picture: String? = null,
    val at: Instant? = null,
    val postCode: String? = null,
    val thumbnail: String? = null,
) {
    /** The post's short link on threads.com, which Threads sends on to the post; null when Meta named none. */
    val postUrl: String? get() = postCode?.let { "${ThreadsLinks.WEB}/t/$it" }
}

/**
 * The signed-in reader's activity (Threads' heart tab): replies, mentions,
 * quotes, reposts, likes on their posts and new followers, as the Threads
 * app reads its notifications (`text_feed/text_app_notifications/`, the
 * shape of Instagram's `news/inbox/`, which is tried after it). Always asked
 * with `mark_as_seen=false`: reading the list here marks nothing seen on
 * Threads, and nothing is answered or followed back. No probe has seen
 * either path answer, so CI's live probe (ThreadsSignedInLiveTest) logs how
 * each answers a guest. Every field is read as if it might be gone.
 */
object ThreadsActivity {
    /** The paths the activity may be read at, in the order they are tried. */
    val PATHS = listOf("/api/v1/text_feed/text_app_notifications/", "/api/v1/news/inbox/")

    /** The query of one page of [filter], after [cursor] when it is not the first; never one that marks anything seen. */
    fun params(filter: ThreadsActivityFilter, cursor: String?): List<Pair<String, String>> = listOfNotNull(
        "feed_type" to "all",
        "mark_as_seen" to "false",
        filter.selected?.let { "selected_filters" to it },
        cursor?.trim()?.ifEmpty { null }?.let { "max_id" to it },
    )

    /**
     * One page of activity: the new stories, then the older ones (or a plain
     * `stories`, `notifications` or `items` list), each once; a line with no
     * words is left out. The cursor after them is Meta's `next_max_id` (or
     * its downwards paging token), null when it named none, said there is no
     * more, or the page brought nothing.
     */
    fun page(body: J): Page<ThreadsActivityItem> {
        val stories = LISTS.flatMap { body[it].list }
        val items = stories.mapIndexedNotNull(::item).distinctBy { it.id }
        val next = listOf(body["next_max_id"], body["paging_tokens"]["downwards"], body["max_id"])
            .firstNotNullOfOrNull { it.string ?: it.integer?.toString() }?.trim()?.ifEmpty { null }
        val ended = body["has_more"].boolean == false || body["more_available"].boolean == false || body["is_last_page"].boolean == true
        return Page(items, next.takeIf { !ended && items.isNotEmpty() })
    }

    /** One story as a line; null without words. */
    private fun item(index: Int, story: J): ThreadsActivityItem? {
        val args = story["args"].takeIf { it.exists } ?: story
        val text = (args["text"].string ?: args["rich_text"].string?.let(::plain))?.trim()?.ifEmpty { null } ?: return null
        val handle = listOf(args["profile_name"], args["inline_follow"]["user_info"]["username"], args["user"]["username"])
            .firstNotNullOfOrNull { it.string }?.let(ThreadsLinks::normaliseHandle)
        val at = (args["timestamp"].double ?: story["timestamp"].double)?.let(::instant)
        val media = args["media"][0]
        val code = media["shortcode"].string ?: media["code"].string ?: mediaId(media, args)?.let(ThreadsPeople::shortcodeOf)
        val id = listOf(args["tuuid"], story["pk"], story["id"]).firstNotNullOfOrNull { it.string ?: it.integer?.toString() }
            ?: "${handle.orEmpty()}:${at?.toEpochMilli() ?: index}:${text.hashCode()}"
        return ThreadsActivityItem(
            id = id,
            kind = kindOf(story, args),
            text = text,
            handle = handle,
            picture = args["profile_image"].string?.takeIf { it.startsWith("https://") },
            at = at,
            postCode = code?.takeIf { it.isNotBlank() && it.all(::isCodeLetter) },
            thumbnail = media["image"].string?.takeIf { it.startsWith("https://") },
        )
    }

    /** The kind Meta's name for a story says (`notif_name`, `type_name`); [ThreadsActivityKind.OTHER] for one that says none. */
    internal fun kindOf(story: J, args: J = story["args"]): ThreadsActivityKind {
        val name = listOf(story["notif_name"], args["notif_name"], story["type_name"], story["story_name"])
            .firstNotNullOfOrNull { it.string }?.lowercase().orEmpty()
        return KINDS.firstOrNull { (words, _) -> words.any { it in name } }?.second ?: ThreadsActivityKind.OTHER
    }

    /** The numeric media id a story names: its media's `id` (`media_user`), else `media_id=` in its destination. */
    private fun mediaId(media: J, args: J): String? {
        val listed = (media["id"].string ?: media["pk"].string ?: media["id"].integer?.toString())?.substringBefore('_')
        val linked = args["destination"].string?.let { MEDIA_ID.find(it)?.groupValues?.get(1) }
        return (listed ?: linked)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    /** Instagram's rich text (`{ann|000000|1|user?id=1} liked your post`) as the words it shows. */
    internal fun plain(rich: String): String = RICH.replace(rich) { it.groupValues[1] }

    /** Seconds since the epoch, or milliseconds where the number is that large. */
    private fun instant(value: Double): Instant? = runCatching {
        if (value > MILLIS_FROM) Instant.ofEpochMilli(value.toLong()) else Instant.ofEpochMilli((value * 1000).toLong())
    }.getOrNull()?.takeIf { value > 0 }

    private fun isCodeLetter(letter: Char) = letter.isLetterOrDigit() || letter == '-' || letter == '_'

    private val LISTS = listOf("new_stories", "old_stories", "stories", "notifications", "items")

    /** Words in Meta's story names, the more particular first: a like of a reply is a like, a reply that mentions is a reply's mention. */
    private val KINDS = listOf(
        listOf("like") to ThreadsActivityKind.LIKE,
        listOf("repost", "reshare") to ThreadsActivityKind.REPOST,
        listOf("quote") to ThreadsActivityKind.QUOTE,
        listOf("mention") to ThreadsActivityKind.MENTION,
        listOf("repl", "comment") to ThreadsActivityKind.REPLY,
        listOf("follow") to ThreadsActivityKind.FOLLOW,
    )
    private val RICH = Regex("""\{([^{}|]+)\|[^{}]*\}""")
    private val MEDIA_ID = Regex("""media_id=(\d+)""")
    private const val MILLIS_FROM = 1e11
}
