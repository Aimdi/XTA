package com.aimdi.xtanative.core.feed

/**
 * One member of a feed, as XTA's group feed searches it: an X account,
 * searched as `from:` its handle, or a saved search ([search]), searched as its
 * query in brackets.
 */
data class FeedMember(
    val id: String,
    val screenName: String,
    /** When it was followed, in epoch milliseconds: the feed's order. */
    val followedAt: Long = 0,
    /** A saved search: [id] and [screenName] are its query. */
    val search: Boolean = false,
    /** The most of this account's posts one load keeps (XTA's "max posts per load"); null or 0 keeps them all. */
    val maxPosts: Int? = null,
) {
    /** What it adds to its chunk's search. */
    val term: String get() = if (search) FeedQueries.savedSearchTerm(id) else FeedQueries.term(screenName)

    companion object {
        /**
         * Oldest follow first, ties by id: a new member lands in the last chunk
         * instead of reshuffling every chunk of the feed.
         */
        val ORDER: Comparator<FeedMember> = compareBy<FeedMember> { it.followedAt }.thenBy { it.id }

        /** A saved search as a member. */
        fun search(query: String, followedAt: Long = 0) = FeedMember(query, query, followedAt, search = true)
    }
}
