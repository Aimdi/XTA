package com.aimdi.xtanative.networks.threads

/** Who to suggest following on Threads, as XTA's threads_discovery.dart finds them. */
object ThreadsDiscovery {
    /** Public handles a reader can try before they know anyone: guests can read all three. */
    val STARTER_HANDLES = listOf("zuck", "mosseri", "meta")

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
}
