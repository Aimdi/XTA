package com.aimdi.xtanative.core.x.model

/**
 * An opened post: what it answers, the post itself, and the replies under it.
 * [focalWithheld] says why there is no [focal] when X withheld the post
 * itself, and [withheldBefore] holds the posts it answers that X withheld, by
 * id, so they can be shown in their place; [showMoreCursor] asks for the
 * replies X hides as low quality.
 */
data class XConversation(
    val before: List<XPost>,
    val focal: XPost?,
    val replies: List<XThread>,
    val repliesCursor: String?,
    val showMoreCursor: String? = null,
    val focalWithheld: XWithheld? = null,
    val withheldBefore: List<XWithheld> = emptyList(),
) {
    /** Whether replies show under the post: later in its own thread, or in threads after it. */
    val hasReplies: Boolean get() = focal != null && replies.any { it.posts.isNotEmpty() || it.withheld.isNotEmpty() }

    /**
     * Whether the post has replies, or a cursor that can still load them: a
     * page with neither is not worth keeping, as XTA judges before caching it.
     */
    val hasRepliesOrMore: Boolean get() = repliesCursor != null || showMoreCursor != null || hasReplies

    companion object {
        /**
         * Splits a TweetDetail page around [focalId]: X sends the posts it
         * answers first, oldest first, then the post, then the reply threads.
         */
        fun of(page: XTimelinePage, focalId: String): XConversation {
            val index = page.threads.indexOfFirst { thread -> thread.posts.any { it.id == focalId } }
            if (index < 0) {
                val withheld = page.threads.firstNotNullOfOrNull { thread -> thread.withheld.firstOrNull { it.id == focalId } }
                val replies = page.threads.filterNot { thread -> thread.posts.isEmpty() && thread.withheld.any { it.id == focalId } }
                return XConversation(emptyList(), null, replies, page.bottomCursor, page.showMoreCursor, withheld)
            }
            val earlier = page.threads.take(index)
            val thread = page.threads[index]
            val inThread = thread.posts.indexOfFirst { it.id == focalId }
            return XConversation(
                before = earlier.flatMap { it.posts } + thread.posts.take(inThread),
                focal = thread.posts[inThread],
                replies = listOfNotNull(
                    thread.posts.drop(inThread + 1).takeIf { it.isNotEmpty() }?.let { XThread(it.first().id, it) },
                ) + page.threads.drop(index + 1),
                repliesCursor = page.bottomCursor,
                showMoreCursor = page.showMoreCursor,
                withheldBefore = earlier.flatMap { it.withheld },
            )
        }
    }
}
