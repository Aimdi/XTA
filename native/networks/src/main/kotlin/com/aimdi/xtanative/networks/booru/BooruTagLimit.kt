package com.aimdi.xtanative.networks.booru

/** A search shared out: [host] goes in the request, [device] is checked on each post the host sends back. */
data class BooruTagSplit(val host: List<String>, val device: List<String>)

/**
 * Searches with more tags than a host takes at once. Danbooru takes two from guests and Members;
 * past that it answers 422, and XTA then sends what fits and checks the rest on the device. As
 * Danbooru's answers to a guest showed on 2026-10-29: plain, excluded and either tags count, and so
 * does `order:`; `rating:`, `status:`, `limit:`, `is:`, `parent:`, `filetype:`, `score:` and `age:` do
 * not. Any other metatag counts, to be safe.
 */
object BooruTagLimit {
    private val FREE_METATAGS = setOf("rating", "status", "limit", "is", "parent", "filetype", "score", "age")

    /** The lowest limit Danbooru gives anyone. */
    private const val GUEST_LIMIT = 2

    private val METATAG = Regex("^([a-z_]+):", RegexOption.IGNORE_CASE)
    private val STATED_LIMIT = Regex("more than (\\d+) tags", RegexOption.IGNORE_CASE)

    /** How many of [tokens] count towards a host's limit. */
    fun counted(tokens: List<String>): Int = tokens.count { !isFree(it) }

    /**
     * [tokens] shared out so that at most [limit] counted ones go to the host: every metatag and
     * wildcard, then the plain tags it has the fewest posts for ([uses], by lower-case name; a tag
     * it does not list has none, so an alias or a typo goes first), then an either group if all of
     * it fits, then excluded tags. The rest are left to the device, in the order typed. Whole when
     * they fit; null when the metatags alone do not.
     */
    fun split(tokens: List<String>, limit: Int, uses: Map<String, Int> = emptyMap()): BooruTagSplit? {
        if (counted(tokens) <= limit) return BooruTagSplit(tokens, emptyList())
        val pinned = tokens.filter(::isPinned)
        val room = limit - counted(pinned)
        if (room < 0) return null
        val movable = tokens.filterNot(::isPinned)
        val plain = leastUsedFirst(movable.withOperator(BooruTagOperator.NONE), uses).take(room)
        val allEither = movable.withOperator(BooruTagOperator.EITHER)
        val either = if (allEither.size <= room - plain.size) allEither else emptyList()
        val excluded = movable.withOperator(BooruTagOperator.EXCLUDE).take((room - plain.size - either.size).coerceAtLeast(0))
        val toHost = (pinned + plain + either + excluded).toSet()
        val (host, device) = tokens.partition { it in toHost }
        return BooruTagSplit(host, device)
    }

    /**
     * How many tags one search may have, from a host's answer that a search had too many:
     * Danbooru says "You cannot search for more than 2 tags at a time.", and its error's name alone
     * means its lowest limit. Null when the answer says neither.
     */
    fun limitIn(body: String): Int? {
        val stated = STATED_LIMIT.find(body)
        return when {
            stated != null -> stated.groupValues[1].toIntOrNull()
            "TagLimitError" in body -> GUEST_LIMIT
            else -> null
        }
    }

    private fun body(token: String): String = token.substring(BooruTagOperator.of(token).prefix.length)

    private fun isFree(token: String): Boolean = METATAG.find(body(token))?.groupValues?.get(1)?.lowercase() in FREE_METATAGS

    /** A token the device cannot check by a tag's name: a metatag, or a tag with a wildcard. */
    private fun isPinned(token: String): Boolean = body(token).let { METATAG.containsMatchIn(it) || '*' in it }

    private fun List<String>.withOperator(operator: BooruTagOperator): List<String> = filter { BooruTagOperator.of(it) == operator }

    /** [tags] by how few posts carry them, in their own order where that is the same. */
    private fun leastUsedFirst(tags: List<String>, uses: Map<String, Int>): List<String> =
        tags.sortedBy { uses[it.lowercase()] ?: 0 }
}
