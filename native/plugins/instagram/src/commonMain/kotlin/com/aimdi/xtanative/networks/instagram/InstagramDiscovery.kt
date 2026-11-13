package com.aimdi.xtanative.networks.instagram

/**
 * What a reader sees on Instagram before following anyone, as XTA's
 * instagram_discovery.dart puts it together: a guest For you mixed from a
 * few public accounts, and a strip of people to follow drawn from it.
 */
object InstagramDiscovery {
    /** The public accounts a guest For you reads: anyone may see them without an account. */
    val HANDLES = listOf("instagram", "natgeo", "nasa")

    /**
     * Whether a search for [query] should open that profile straight away:
     * a guest cannot search people, but a typed username opens as a profile.
     */
    fun opensHandle(hasSession: Boolean, query: String): Boolean = !hasSession && InstagramParser.handle(query) != null

    /**
     * One post from each page in turn, round after round, each post once.
     * XTA stopped at the first round that held only repeats, dropping the
     * posts after it; every round is taken here.
     */
    fun interleave(pages: List<List<InstagramPost>>): List<InstagramPost> {
        val rounds = pages.maxOfOrNull { it.size } ?: 0
        return (0 until rounds).flatMap { round -> pages.mapNotNull { it.getOrNull(round) } }.distinctBy { it.id }
    }

    /**
     * The authors of [posts] the reader does not follow yet, each once, at
     * most [cap]: For you's strip of people to follow. [alreadyFollows] is
     * asked with lowercased usernames.
     */
    fun peopleToFollow(posts: List<InstagramPost>, alreadyFollows: (String) -> Boolean, cap: Int = 12): List<InstagramAuthor> =
        posts.map { it.author }
            .filter { handleOf(it).isNotEmpty() && !alreadyFollows(handleOf(it)) }
            .distinctBy(::handleOf)
            .take(cap)

    private fun handleOf(author: InstagramAuthor): String = author.username.trim().lowercase()
}
