package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.reddit.FlatComment
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditMedia
import com.aimdi.xtanative.networks.reddit.RedditPost
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One row of a thread: the comment, and how many replies its fold is holding. */
internal data class VisibleComment(val entry: FlatComment, val hidden: Int)

/** What a row standing for comments Reddit held back does when tapped. */
internal enum class HeldBack {
    /** Loads them here, where Reddit said they are. */
    LOAD_HERE,

    /** The thread's own held-back comments, once Reddit would not hand them over: read on Reddit. */
    OPEN_ON_REDDIT,

    /** A reply's held-back subtree, on its own page. */
    OPEN_SUBTREE,

    /** Reddit named no page for them. */
    NOTHING,
}

/** Why a comment's words are gone, as Reddit puts it in their place. */
internal enum class Removed { DELETED, BY_MODERATORS, BY_REDDIT, UNAVAILABLE }

/** The thread view's rules (XTA's `reddit_comments.dart` and `reddit_thread_screen.dart`), apart from what draws them. */
internal object RedditThreadRules {
    /**
     * The rows a thread shows with [collapsed] comments folded: a folded
     * comment stays as its row, holding how many replies it hides, and
     * everything under it is skipped.
     */
    fun visible(all: List<FlatComment>, collapsed: Set<String>): List<VisibleComment> {
        val rows = ArrayList<VisibleComment>(all.size)
        var i = 0
        while (i < all.size) {
            val entry = all[i]
            var next = i + 1
            if (entry.comment.id in collapsed) {
                while (next < all.size && all[next].depth > entry.depth) next++
            }
            rows += VisibleComment(entry, next - i - 1)
            i = next
        }
        return rows
    }

    /** Every top-level comment with replies to fold: folding them all turns a long page into the arguments it is made of. */
    fun foldable(all: List<FlatComment>): Set<String> = all.filter { it.depth == 0 && it.comment.replies.isNotEmpty() }.mapTo(HashSet()) { it.comment.id }

    /** Whether everything foldable is folded; null when there is nothing to fold. */
    fun allFolded(all: List<FlatComment>, collapsed: Set<String>): Boolean? = foldable(all).takeIf { it.isNotEmpty() }?.let(collapsed::containsAll)

    /** Collapse all, or Expand all once everything is folded. */
    fun toggledAll(all: List<FlatComment>, collapsed: Set<String>): Set<String> {
        val foldable = foldable(all)
        return if (collapsed.containsAll(foldable)) collapsed - foldable else collapsed + foldable
    }

    /** The rows of the top-level comments, in order: where the jump button lands. */
    fun topLevelRows(rows: List<VisibleComment>): List<Int> = rows.indices.filter { rows[it].entry.depth == 0 && !rows[it].entry.comment.isStub }

    /** The jump button shows once there are two top-level comments to jump between. */
    fun showsJump(rows: List<VisibleComment>): Boolean = topLevelRows(rows).size >= 2

    /** A hairline above each top-level comment but the first: [index] is the row's place among the comments. */
    fun divided(depth: Int, index: Int): Boolean = depth == 0 && index > 0

    /** Every target in a thread is at least this tall and wide, in dp (XTA's kTweetTouchTarget). */
    const val TOUCH_TARGET_DP = 48

    /** How far a reply sits in per level, in dp (kRedditThreadColumn). */
    const val LINE_COLUMN_DP = 20

    /** The most levels of lines drawn, so a deep reply keeps room for its words (kRedditThreadMaxLines). */
    const val MAX_LINES = 6

    /** The thread lines by level, blue, violet, amber and green, repeating (kRedditThreadLineColours). */
    val LINE_COLOURS: List<Int> = listOf(0xFF3F78C4.toInt(), 0xFF9A7FE0.toInt(), 0xFFD9A646.toInt(), 0xFF56B88A.toInt())

    /** The lines drawn beside a reply at [depth]: one per level above it, no more than [MAX_LINES]. */
    fun lines(depth: Int): Int = minOf(depth, MAX_LINES)

    /** The line of [level] on a [page] of that colour: its colour, darkened or lightened to 3:1 against the page. */
    fun lineColour(level: Int, page: Int): Int = RedditContrast.ensure(LINE_COLOURS[level % LINE_COLOURS.size], page, 3.0)

    /** The score an upvote button shows: Reddit's, one more once this device upvoted. */
    fun shownScore(score: Int, upvoted: Boolean): Int = score + if (upvoted) 1 else 0

    /** The first top-level row after [from], the row at the top of the screen; null when none is left. */
    fun nextTopLevel(rows: List<VisibleComment>, from: Int): Int? = topLevelRows(rows).firstOrNull { it > from }

    /** Reddit's count, or what has loaded when that is more: a post from search arrives without one. */
    fun commentCount(post: RedditPost, comments: List<FlatComment>?): Int = maxOf(post.commentCount, comments.orEmpty().count { !it.comment.isStub })

    /** [comments] with [stub]'s row swapped for [rows]. */
    fun replacing(comments: List<FlatComment>, stub: RedditComment, rows: List<FlatComment>): List<FlatComment> =
        comments.flatMap { if (it.comment.id == stub.id) rows else listOf(it) }

    /** Whether [stub] stands for comments held back at the thread's own top, rather than under a reply. */
    fun isRoot(stub: RedditComment, post: RedditPost): Boolean =
        stub.permalink?.let { RedditVisits.threadKey(it) == RedditVisits.threadKey(post.permalink) } == true

    fun heldBack(stub: RedditComment, post: RedditPost, moreRefused: Boolean): HeldBack = when {
        stub.permalink == null -> HeldBack.NOTHING
        isRoot(stub, post) && stub.nextPage != null && !moreRefused -> HeldBack.LOAD_HERE
        isRoot(stub, post) -> HeldBack.OPEN_ON_REDDIT
        else -> HeldBack.OPEN_SUBTREE
    }

    fun removed(comment: RedditComment): Removed = when (comment.body.trim().lowercase()) {
        "[removed]" -> Removed.BY_MODERATORS
        "[ removed by reddit ]" -> Removed.BY_REDDIT
        "[unavailable]" -> Removed.UNAVAILABLE
        else -> Removed.DELETED
    }

    /** A comment's page on Reddit, or null when Reddit named none. */
    fun commentUrl(comment: RedditComment): String? = comment.permalink?.let { if (it.startsWith("http")) it else "https://www.reddit.com$it" }

    /** The public address of a post: what is shared and opened outside. */
    fun postUrl(post: RedditPost): String = "https://www.reddit.com" + post.permalink

    /**
     * [post] with what its own page says it points at, for one that arrived
     * without: a post from search names no link and no media. A link that is
     * the post's own permalink says nothing; a picture link is the picture,
     * not a gallery of its variants.
     */
    fun withPageMedia(post: RedditPost, url: String?, images: List<String>): RedditPost {
        if (post.imageUrl != null) return post
        val external = url?.takeUnless(::isOwnPermalink)
        val gallery = RedditMedia.collapseImageUrls(images)
        if (external == null && gallery.isEmpty()) return post
        val urlIsImage = external != null && RedditMedia.imageUrl(external) != null
        return post.copy(
            url = external ?: post.url,
            isSelf = if (external != null) false else post.isSelf,
            galleryImages = if (urlIsImage || gallery.isEmpty()) post.galleryImages else gallery,
        )
    }

    private fun isOwnPermalink(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.host.endsWith("reddit.com") && "/comments/" in parsed.encodedPath
    }

    /** The words worth printing under the post's media: without the links its pictures already show, and none when nothing is left. */
    fun visibleSelfText(post: RedditPost, raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val text = if (post.hasVisualMedia) RedditMedia.stripMediaLinks(raw) else raw
        return text.takeUnless { it.isEmpty() || RedditMedia.isPlaceholderTitle(it) }
    }
}
