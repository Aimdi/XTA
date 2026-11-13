package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page

/**
 * The posts that quote a post, as Threads lists them under its Quotes for a
 * signed-in reader: asked for with the reader's session by the post's media
 * id ([ThreadsPeople.mediaId]), page after page with `max_id`. No probe has
 * seen the path the Threads app reads them at, so [paths] names the likely
 * ones, tried in turn (a 404 moves on), and CI's live probe
 * (ThreadsPostListsLiveTest) logs how each answers. Every field is read as
 * if it might be gone; nothing here quotes or reposts anything.
 */
object ThreadsQuotes {
    /** The paths the quotes of the post Meta keeps under [mediaId] may be read at, in the order they are tried. */
    fun paths(mediaId: String): List<String> = listOf("/api/v1/text_feed/$mediaId/quotes/", "/api/v1/media/$mediaId/quotes/")

    /** The query of one page: after [cursor] when it is not the first. */
    fun params(cursor: String?): List<Pair<String, String>> = listOfNotNull(cursor?.trim()?.ifEmpty { null }?.let { "max_id" to it })

    /**
     * One page of quotes: the feed's threads or items as a timeline carries
     * them, else a plain list of posts ([ThreadsListedPosts]), each post once,
     * and the cursor after them, null when Meta named none or said there is
     * no more. The post quoted is never one of its own quotes.
     */
    fun page(body: J, quotedId: String? = null): Page<ThreadsPost> = ThreadsListedPosts.page(body, without = quotedId)
}
