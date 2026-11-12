package com.aimdi.xtanative.networks.threads

/** Who to suggest following on Threads, as XTA's threads_discovery.dart finds them. */
object ThreadsDiscovery {
    /** Public handles a reader can try before they know anyone: guests can read all three. */
    val STARTER_HANDLES = listOf("zuck", "mosseri", "meta")

    /** Topics For you explores when the posts shown name too few: broad ones whose pages every guest can read. */
    val STARTER_TOPICS = listOf("Photography", "Books", "Art", "Science", "Music", "Movies")

    /**
     * The authors of [posts] not followed yet, at most [cap]: the reposted ones
     * first, since a followed account boosting someone new is the best hint.
     */
    fun peopleToFollow(posts: List<ThreadsPost>, alreadyFollows: (String) -> Boolean, cap: Int = 12): List<ThreadsPerson> {
        val seen = HashSet<String>()
        val people = posts.mapNotNull { post ->
            val handle = post.handle.trim().lowercase()
            if (handle.isEmpty() || alreadyFollows(handle) || !seen.add(handle)) return@mapNotNull null
            ThreadsPerson(handle, post.authorName.ifBlank { handle }, post.avatarUrl, fromRepost = post.isRepost)
        }
        val (reposted, others) = people.partition { it.fromRepost }
        return (reposted + others).take(cap)
    }

    /**
     * Topics and communities to explore, at most [cap]: those [followed]
     * here first, then those [posts] were posted in, communities first and
     * the most posted in before the rest; each once, with the mark its posts
     * carried.
     */
    fun topics(posts: List<ThreadsPost>, followed: List<ThreadsTopic> = emptyList(), cap: Int = 12): List<ThreadsTopic> {
        val seen = posts.mapNotNull(::topicOf)
        val counts = seen.groupingBy { it.key }.eachCount()
        val ranked = seen.distinctBy { it.key }.sortedWith(compareByDescending<ThreadsTopic> { it.community }.thenByDescending { counts[it.key] ?: 0 })
        return (followed + ranked).distinctBy { it.key }.take(cap)
    }

    /** What [explore][ThreadsForYou] reads for [topic]: a community's own page where known, else its name. */
    fun page(topic: ThreadsTopic): String = topic.url ?: topic.name

    /** The topic or community [post] was posted in; null for none. */
    fun topicOf(post: ThreadsPost): ThreadsTopic? = post.topicTag?.trim()?.removePrefix("#")?.ifEmpty { null }?.let {
        ThreadsTopic(it, post.facts.topicEmoji, post.facts.topicIconUrl, post.facts.community, post.facts.communityUrl)
    }
}
