package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page

/**
 * The lists of posts Threads keeps for the signed-in reader alone: the
 * Following feed (the accounts they follow on Threads, newest first), the
 * posts they saved, and the posts they liked. Read only: nothing here
 * follows, saves or likes anything on Threads.
 */
enum class ThreadsOwnFeed {
    FOLLOWING,
    SAVED,
    LIKED,
}

/**
 * How [ThreadsOwnFeed]s are asked for and read, with the reader's session
 * (the cookies on threads.com's `/api/v1`, else the Bearer on the app API).
 * The Following feed is the timeline the Bearer's For you is read from
 * (`feed/text_post_app_timeline/`), asked with `feed_type=following`; no
 * probe has seen the saved or the liked posts' paths, so [paths] names the
 * likely ones, tried in turn (a 404 moves on), and CI's live probe
 * (ThreadsSignedInLiveTest) logs how each answers a guest: a real session
 * path sends a guest to sign in, a wrong one is a 404. Every field is read
 * as if it might be gone.
 */
object ThreadsOwnFeeds {
    /** The timeline path the Threads app reads its home from, the Following feed's first candidate. */
    const val TIMELINE = "/api/v1/feed/text_post_app_timeline/"

    /** Every path [feed] may be read at, in the order they are tried. */
    fun paths(feed: ThreadsOwnFeed): List<String> = when (feed) {
        ThreadsOwnFeed.FOLLOWING -> listOf(TIMELINE, "/api/v1/text_feed/following_timeline/")
        ThreadsOwnFeed.SAVED -> listOf("/api/v1/text_feed/saved_posts/", "/api/v1/text_feed/saved/", "/api/v1/feed/text_post_app_saved/")
        ThreadsOwnFeed.LIKED -> listOf("/api/v1/text_feed/liked/", "/api/v1/text_feed/liked_posts/", "/api/v1/feed/text_post_app_liked/")
    }

    /**
     * The query of one page of [feed], after [cursor] when it is not the
     * first. The Following feed is asked as For you is, with
     * `feed_type=following` and [device] as the session's id; `feed_view_info`
     * is sent empty, so no post is reported as seen.
     */
    fun params(feed: ThreadsOwnFeed, cursor: String?, device: String): List<Pair<String, String>> {
        val after = cursor?.trim()?.ifEmpty { null }
        val timeline = listOf(
            "feed_type" to "following",
            "feed_view_info" to "[]",
            "reason" to (if (after == null) "cold_start_fetch" else "pagination"),
            "client_session_id" to device,
            "pagination_source_module" to "feed_unit",
        ).takeIf { feed == ThreadsOwnFeed.FOLLOWING }.orEmpty()
        return timeline + listOfNotNull(after?.let { "max_id" to it })
    }

    /** One page of posts: a timeline's threads, else a plain list of posts ([ThreadsListedPosts]). */
    fun page(body: J): Page<ThreadsPost> = ThreadsListedPosts.page(body)

    /**
     * Whether [body] can be the Following feed: false when most of the
     * authors Meta marks with the reader's `friendship_status` are marked as
     * not followed, which is what a timeline that took `feed_type` for
     * something else (For you) would answer. A page that marks no one, or
     * brings nothing, is taken at its word.
     */
    fun isFollowing(body: J): Boolean {
        val marks = body["threads"].list.ifEmpty { body["items"].list }
            .flatMap { bucket -> bucket["thread_items"].list.map { it["post"] }.ifEmpty { listOf(bucket["post"]) } }
            .mapNotNull { it["user"]["friendship_status"]["following"].boolean }
        return marks.isEmpty() || marks.count { it } * 2 >= marks.size
    }
}

/**
 * A page of posts as Meta lists them outside a timeline as well as inside
 * one: a timeline's `threads` or `items` with their `thread_items`, else a
 * plain list (`items`, `medias`, `posts`, `quotes`) of posts, each bare or
 * under `post` or `media`; and the cursor after them.
 */
internal object ThreadsListedPosts {
    /**
     * Each post once (by its short code, else its id), never [without]; the
     * cursor after them, null when Meta named none, said there is no more, or
     * the page brought nothing.
     */
    fun page(body: J, without: String? = null): Page<ThreadsPost> {
        val listed = LISTS.map { body[it].list }.firstOrNull { it.isNotEmpty() }.orEmpty()
            .mapNotNull { item -> ThreadsParser.post(WRAPS.map { item[it] }.firstOrNull { it.exists } ?: item) }
        val timeline = body["threads"].list.isNotEmpty() || body["items"].list.any { it["thread_items"].exists }
        val posts = (if (timeline) ThreadsParser.feed(body) else listed)
            .filter { it.id != without }
            .distinctBy { it.shortcode ?: it.id }
        val next = listOf(body["next_max_id"], body["paging_tokens"]["downwards"], body["max_id"])
            .firstNotNullOfOrNull { it.string ?: it.integer?.toString() }?.trim()?.ifEmpty { null }
        val ended = body["has_more"].boolean == false || body["more_available"].boolean == false
        return Page(posts, next.takeIf { !ended && posts.isNotEmpty() })
    }

    private val LISTS = listOf("items", "medias", "posts", "quotes")
    private val WRAPS = listOf("post", "media")
}
