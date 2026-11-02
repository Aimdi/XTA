package com.aimdi.xtanative.core.feed

/**
 * The searches the Following feed sends, as XTA's lib/group/feed_search_query.dart
 * builds them: the accounts of one chunk in one bracket, so the reply and
 * repost filters after it cover every account and not only the last.
 */
object FeedQueries {
    /** The longest query X reads for one search. */
    const val LIMIT = 512

    private const val SEPARATOR = " OR "

    fun term(screenName: String) = "from:$screenName"

    fun filters(includeReplies: Boolean, includeReposts: Boolean): List<String> = listOfNotNull(
        "-filter:replies".takeIf { !includeReplies },
        if (includeReposts) "include:nativeretweets" else "-filter:retweets",
    )

    fun query(terms: List<String>, includeReplies: Boolean, includeReposts: Boolean): String =
        (listOfNotNull(terms.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR, "(", ")")) + filters(includeReplies, includeReposts))
            .joinToString(" ")

    /**
     * Splits [members], in feed order, into the fewest chunks whose queries fit
     * [LIMIT]. Walking in order means a newly followed account, which comes
     * last, never moves anyone into another chunk; one too long to share a
     * query gets a chunk of its own.
     */
    fun <T> pack(members: List<T>, termOf: (T) -> String, includeReplies: Boolean, includeReposts: Boolean): List<List<T>> {
        // The brackets and the space before the filters.
        val overhead = filters(includeReplies, includeReposts).joinToString(" ").length + 3
        val chunks = ArrayList<MutableList<T>>()
        var termsLength = 0
        for (member in members) {
            val length = termOf(member).length
            if (chunks.isNotEmpty() && overhead + termsLength + SEPARATOR.length + length <= LIMIT) {
                chunks.last().add(member)
                termsLength += SEPARATOR.length + length
            } else {
                chunks.add(mutableListOf(member))
                termsLength = length
            }
        }
        return chunks
    }
}
