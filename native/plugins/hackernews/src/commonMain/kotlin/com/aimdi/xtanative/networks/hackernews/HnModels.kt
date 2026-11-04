package com.aimdi.xtanative.networks.hackernews

import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The lists Hacker News shows: its front page, newest, best, Ask HN, Show HN and jobs. */
enum class HnFeed { TOP, NEWEST, BEST, ASK, SHOW, JOBS }

/** One story, Ask HN, Show HN or job. */
data class HnStory(
    val id: Long,
    val title: String,
    /** Where the story links to; null for a text post (Ask HN, most jobs). */
    val url: String? = null,
    /** A text post's body as plain text; empty for a link. */
    val text: String = "",
    val author: String? = null,
    val score: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Instant? = null,
    /** `story`, `ask`, `show` or `job` from a list; Hacker News' own type from an item. */
    val type: String = "story",
) {
    /** The story's page on Hacker News, where its comments are. */
    val hnUrl: String get() = "https://news.ycombinator.com/item?id=$id"

    /** The linked site without `www.`, as HN shows it beside the title. */
    val host: String? get() = url?.toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.")
}

/**
 * One comment and its replies. A comment whose author or text is gone was
 * deleted or flagged: it stays in the tree so its replies keep their place.
 */
data class HnComment(
    val id: Long,
    val author: String? = null,
    /** The comment as plain text, paragraphs kept. */
    val text: String = "",
    val createdAt: Instant? = null,
    val children: List<HnComment> = emptyList(),
    val deleted: Boolean = false,
) {
    companion object {
        /**
         * [comments] depth first, each with its depth, leaving out the replies
         * of the comments in [collapsed]: the rows a thread shows.
         */
        fun visible(comments: List<HnComment>, collapsed: Set<Long>): List<Pair<HnComment, Int>> =
            comments.flatMap { walk(it, 0, collapsed) }

        private fun walk(comment: HnComment, depth: Int, collapsed: Set<Long>): List<Pair<HnComment, Int>> =
            listOf(comment to depth) +
                if (comment.id in collapsed) emptyList() else comment.children.flatMap { walk(it, depth + 1, collapsed) }
    }
}

/** A story and its comment tree. */
data class HnThread(val story: HnStory, val comments: List<HnComment>)

/** A Hacker News account as its profile shows it. */
data class HnUser(
    val id: String,
    val karma: Int = 0,
    /** The profile's about text, as plain text. */
    val about: String = "",
    val createdAt: Instant? = null,
    /** How many stories and comments it has posted. */
    val submittedCount: Int = 0,
)

/** A Hacker News address the app can open on its own screen. */
sealed interface HnLink {
    data class Story(val id: Long) : HnLink
    data class User(val id: String) : HnLink
}
