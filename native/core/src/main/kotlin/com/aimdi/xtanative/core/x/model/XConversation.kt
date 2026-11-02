package com.aimdi.xtanative.core.x.model

/** An opened post: what it answers, the post itself, and the replies under it. */
data class XConversation(val before: List<XPost>, val focal: XPost?, val replies: List<XThread>, val repliesCursor: String?) {
    companion object {
        /**
         * Splits a TweetDetail page around [focalId]: X sends the posts it
         * answers first, oldest first, then the post, then the reply threads.
         */
        fun of(page: XTimelinePage, focalId: String): XConversation {
            val index = page.threads.indexOfFirst { thread -> thread.posts.any { it.id == focalId } }
            if (index < 0) return XConversation(emptyList(), null, page.threads, page.bottomCursor)
            val thread = page.threads[index]
            val inThread = thread.posts.indexOfFirst { it.id == focalId }
            return XConversation(
                before = page.threads.take(index).flatMap { it.posts } + thread.posts.take(inThread),
                focal = thread.posts[inThread],
                replies = listOfNotNull(
                    thread.posts.drop(inThread + 1).takeIf { it.isNotEmpty() }?.let { XThread(it.first().id, it) },
                ) + page.threads.drop(index + 1),
                repliesCursor = page.bottomCursor,
            )
        }
    }
}
