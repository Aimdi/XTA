package com.aimdi.xtanative.networks.threads

/**
 * What a conversation shows after its replies, and only what is honestly
 * beside it. Meta's post page preloads `BarcelonaLoggedOutRelatedPostsQuery`
 * ([ThreadsPostPage.related]); for a guest it holds a handful of popular
 * posts by anyone, which XTA once called "Similar threads". Of those only
 * the opened post's author's own are kept ([fromAuthor], "More from
 * @author"); the rest have nothing to do with the post and are dropped. Real
 * similarity is the post's topic or community ([topicOf]): a few posts from
 * its topic page ([onTopic], "More on …"), read only once the reader reached
 * the end of the conversation ([asksTopic]).
 */
object ThreadsBeside {
    /** How many of the topic's posts "More on …" shows. */
    const val ON_TOPIC = 5

    /** How many of the author's other threads "More from @author" shows. */
    const val FROM_AUTHOR = 6

    /**
     * The author's own other threads among Meta's suggestions [related]:
     * [focus]'s author's, none already in the conversation ([shown] ids),
     * once each. Meta's generic picks by others are left out.
     */
    fun fromAuthor(related: List<ThreadsPost>, focus: ThreadsPost, shown: Set<String> = emptySet()): List<ThreadsPost> {
        val handle = focus.handle.trim().lowercase()
        if (handle.isEmpty()) return emptyList()
        val seen = HashSet(shown).apply { add(focus.id) }
        return related.filter { it.handle.trim().lowercase() == handle && seen.add(it.id) }.take(FROM_AUTHOR)
    }

    /** The topic or community the opened post is filed under, without its `#`; null for a post in none, or one not read yet. */
    fun topicOf(conversation: ThreadsConversation): String? =
        conversation.focus.topicTag?.trim()?.removePrefix("#")?.trim()?.ifEmpty { null }?.takeIf { conversation.loaded }

    /**
     * Whether "More on …" is read now: the conversation is read and in a
     * topic, the reader has scrolled ([readerScrolled]) to its end
     * ([endShown]), and no further page of replies is still to come
     * ([repliesPending]) — that end is not the end yet. Opening a
     * conversation never asks.
     */
    fun asksTopic(conversation: ThreadsConversation, endShown: Boolean, readerScrolled: Boolean, repliesPending: Boolean): Boolean =
        topicOf(conversation) != null && endShown && readerScrolled && !repliesPending

    /**
     * The topic's posts [found] as "More on …" shows them: only those filed
     * under the opened post's topic (a topic page's chain may lead with a
     * post that is not), none of the conversation's own (the post, what it
     * answers, its thread, its replies, the author's threads shown above),
     * no reply to its authors (it may be one of its replies not read yet),
     * once each, at most [ON_TOPIC].
     */
    fun onTopic(conversation: ThreadsConversation, found: List<ThreadsPost>): List<ThreadsPost> {
        val topic = topicOf(conversation)?.lowercase() ?: return emptyList()
        val own = conversation.ancestors + conversation.focus + conversation.continuation + conversation.replies.flatten() + conversation.fromAuthor
        val seen = own.mapTo(HashSet()) { it.id }
        val codes = own.mapNotNullTo(HashSet()) { it.shortcode }
        val voices = (conversation.ancestors + conversation.focus + conversation.continuation).mapTo(HashSet()) { it.handle.lowercase() }
        return found
            .filter { post -> post.topicTag?.trim()?.removePrefix("#")?.trim()?.lowercase() == topic }
            .filter { post -> post.shortcode == null || post.shortcode !in codes }
            .filter { post -> !(post.isReply && post.replyToHandle?.lowercase() in voices) }
            .filter { seen.add(it.id) }
            .take(ON_TOPIC)
    }
}
