package com.aimdi.xtanative.networks.mastodon

/**
 * How XTA puts Mastodon posts together: across pages, across the instances a
 * post was federated to, around a profile's pins, and into timelines shared
 * with other networks.
 */
object MastodonFeeds {
    /** How many posts each followed account adds to a shared timeline (home, a group). */
    const val INTERLEAVED_PAGE_SIZE = 10

    /**
     * [current] then [more], one post per address ([MastodonUrls.postKey]),
     * so copies federated to other instances collapse. A post keeps its first
     * place, but an original replaces a boost of it there, so hiding boosts
     * cannot also hide a followed author's own post.
     */
    fun unique(current: List<MastodonPost>, more: List<MastodonPost>): List<MastodonPost> {
        val positions = HashMap<String, Int>()
        val posts = ArrayList<MastodonPost>()
        for (post in current + more) {
            val key = MastodonUrls.postKey(post)
            val position = positions[key]
            when {
                position == null -> {
                    positions[key] = posts.size
                    posts += post
                }
                posts[position].boosted && !post.boosted -> posts[position] = post
            }
        }
        return posts
    }

    /** [pinned] first, then [posts] without repeating an id. */
    fun withPinned(pinned: List<MastodonPost>, posts: List<MastodonPost>): List<MastodonPost> {
        val ids = pinned.map { it.id }.toSet()
        return pinned + posts.filter { it.id !in ids }
    }

    /**
     * Whether [post] belongs in a shared feed that takes [replies] and
     * [reposts]: a boost follows the repost setting whatever it shares, and
     * any other reply counts as one, an author's own thread included.
     */
    fun takes(post: MastodonPost, replies: Boolean = true, reposts: Boolean = true): Boolean =
        if (post.boosted) reposts else replies || !post.isReply

    /**
     * What an account's posts add to a timeline shared with other networks:
     * those the feed [takes], the first [limit] of them, then only the dated
     * ones, each to be placed by [MastodonPost.publishedAt].
     */
    fun interleaved(posts: List<MastodonPost>, limit: Int = INTERLEAVED_PAGE_SIZE, replies: Boolean = true, reposts: Boolean = true): List<MastodonPost> =
        posts.filter { takes(it, replies, reposts) }.take(limit).filter { it.publishedAt != null }
}

/**
 * The filters and order a reader sets on a Mastodon timeline, applied to the
 * posts already loaded, as XTA's timeline controls apply them: nothing is
 * asked of the instance again.
 */
data class MastodonTimelineFilter(
    /** Words that must all appear in the text, the warning, the author or the link's title. */
    val query: String = "",
    val content: Content = Content.ALL,
    val order: Order = Order.FEED,
    val hideBoosts: Boolean = false,
    val hideReplies: Boolean = false,
) {
    /** [LINKS] keeps posts with a preview card or an address in their text. */
    enum class Content { ALL, MEDIA, LINKS }

    /** [FEED] keeps the instance's order; the others sort by when each post reached the timeline, undated last. */
    enum class Order { FEED, NEWEST, OLDEST }

    /** How many filters are set, for a badge; the order is not one. */
    val activeFilters: Int get() = listOf(query.isNotBlank(), content != Content.ALL, hideBoosts, hideReplies).count { it }

    /** The posts the filters keep, one per address, in the chosen order. */
    fun apply(posts: List<MastodonPost>): List<MastodonPost> {
        val words = query.lowercase().trim().split(MastodonText.whiteSpace).filter { it.isNotEmpty() }
        val seen = HashSet<String>()
        val matches = posts.filter { post -> keeps(post) && words.all { it in haystack(post) } && seen.add(MastodonUrls.postKey(post)) }
        val dated = compareBy<MastodonPost> { it.timelineDate == null }
        return when (order) {
            Order.FEED -> matches
            Order.NEWEST -> matches.sortedWith(dated.thenByDescending { it.timelineDate })
            Order.OLDEST -> matches.sortedWith(dated.thenBy { it.timelineDate })
        }
    }

    private fun keeps(post: MastodonPost): Boolean = when {
        hideBoosts && post.boosted -> false
        hideReplies && (post.replyToId != null || post.replyToAcct != null) -> false
        content == Content.MEDIA -> post.hasMedia
        content == Content.LINKS -> post.linkCard != null || address.containsMatchIn(post.text)
        else -> true
    }

    private fun haystack(post: MastodonPost): String =
        "${post.text} ${post.spoilerText} ${post.acct} ${post.authorName} ${post.linkCard?.title.orEmpty()}".lowercase()

    private companion object {
        val address = Regex("https?://", RegexOption.IGNORE_CASE)
    }
}
