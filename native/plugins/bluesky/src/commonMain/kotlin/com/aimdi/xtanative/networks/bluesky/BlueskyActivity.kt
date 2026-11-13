package com.aimdi.xtanative.networks.bluesky

import java.time.Instant

/** How a post's quotes are ordered: Bluesky ranks none, so the loaded ones are sorted on the device (conversation_sort.dart). */
enum class BlueskyQuoteSort {
    RECENT,
    OLDEST,
    MOST_LIKED,
    ;

    /** [quotes] in this order; equal ones keep the order they came in. */
    fun sorted(quotes: List<BlueskyPost>): List<BlueskyPost> = when (this) {
        RECENT -> quotes.sortedWith(compareBy<BlueskyPost, Instant?>(nullsLast(reverseOrder())) { it.publishedAt })
        OLDEST -> quotes.sortedWith(compareBy<BlueskyPost, Instant?>(nullsLast(naturalOrder())) { it.publishedAt })
        MOST_LIKED -> quotes.sortedByDescending { it.likeCount }
    }

    companion object {
        /** Whether a choice of order is offered: once two quotes are loaded. */
        fun offered(quotes: List<BlueskyPost>): Boolean = quotes.size >= 2
    }
}
