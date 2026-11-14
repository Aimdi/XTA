package com.aimdi.xtanative.plugins.threads

/** What the search screens decide about people, apart from drawing them. */
internal object ThreadsSearchRules {
    /** How many accounts "Try a public account" offers once Threads recommends some; never fewer than are shipped. */
    const val ACCOUNTS_TO_TRY = 12

    /**
     * Where a people search for [query] is kept: a session's under the name
     * every session search shares, a guest's under its own, so signing in
     * never shows what the guest read found, nor the other way round.
     */
    fun peopleKey(query: String, session: Boolean): String = if (session) "threads-people-$query" else "threads-guest-people-$query"

    /**
     * The handles "Try a public account" offers: those Threads recommends a
     * guest first ([recommended], none while that read is off), then the
     * ones shipped ([starters]), each once whatever its case.
     */
    fun accountsToTry(recommended: List<String>, starters: List<String>): List<String> = (recommended + starters)
        .map { it.trim().removePrefix("@") }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(maxOf(ACCOUNTS_TO_TRY, starters.size))
}
