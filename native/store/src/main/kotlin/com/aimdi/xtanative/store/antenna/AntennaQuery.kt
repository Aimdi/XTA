package com.aimdi.xtanative.store.antenna

/**
 * How an antenna reads X: one search of its terms, newest first, and for a
 * following antenna only what followed accounts posted. Nothing is sent but
 * the search.
 */
object AntennaQuery {
    /** The search X is asked for: its newest posts. */
    const val PRODUCT = "Latest"

    /**
     * XTA's query: the include terms joined by OR (in parentheses when there
     * are several), then each exclude term negated. A term holding a space
     * is quoted. Empty when there is nothing to search for.
     */
    fun search(antenna: Antenna): String {
        val include = antenna.includeTerms.takeIf { it.isNotEmpty() }?.let { terms ->
            terms.joinToString(" OR ", transform = ::quoted).let { if (terms.size == 1) it else "($it)" }
        }
        val exclude = antenna.excludeTerms.map { "-" + quoted(it) }
        return (listOfNotNull(include) + exclude).joinToString(" ").trim()
    }

    /**
     * What [antenna] keeps of a page of [items]: all of it for a search
     * antenna; for a following one, only items whose author ([authorId]) is
     * in [followedIds], and nothing when nobody is followed.
     */
    fun <T> kept(antenna: Antenna, items: List<T>, followedIds: Set<String>, authorId: (T) -> String?): List<T> = when {
        antenna.scope == AntennaScope.SEARCH -> items
        followedIds.isEmpty() -> emptyList()
        else -> items.filter { item -> authorId(item)?.let { it in followedIds } == true }
    }

    private fun quoted(term: String): String = term.trim().let { if (' ' in it) "\"$it\"" else it }
}
