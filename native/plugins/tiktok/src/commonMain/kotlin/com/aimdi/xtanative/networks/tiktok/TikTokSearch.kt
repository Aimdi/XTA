package com.aimdi.xtanative.networks.tiktok

/**
 * How XTA's guest search turns typed words into people (tiktok_parse.dart).
 * TikTok's own search wants a signature a guest cannot make, so a search
 * guesses usernames from the words, reads those profiles, and adds the
 * people on the discover page whose names match.
 */
object TikTokSearch {
    private val apostrophes = Regex("['’]")
    private val junk = Regex("(?U)[^a-z0-9._\\s]+")
    private val spaces = Regex("(?U)\\s+")

    /** At most this many usernames are guessed for one search. */
    private const val MAX_CANDIDATES = 8

    /** Suggestions past these are not guessed from. */
    private const val MAX_SUGGESTIONS = 6

    /**
     * The usernames worth reading as profiles for [query], at most eight, in
     * order: the query itself, its words glued together, its first word,
     * then TikTok's [suggestions] and their words glued likewise.
     */
    fun handleCandidates(query: String, suggestions: List<String> = emptyList()): List<String> {
        val words = words(query)
        val own = listOf(query) + gluedHandles(words) + listOfNotNull(words.firstOrNull())
        val suggested = suggestions.take(MAX_SUGGESTIONS).flatMap { listOf(it) + gluedHandles(words(it)) }
        return (own + suggested).mapNotNull(TikTokParser::handle).distinct().take(MAX_CANDIDATES)
    }

    /** [raw]'s words, lowercased, without apostrophes or anything a username cannot hold. */
    fun words(raw: String): List<String> =
        raw.lowercase().replace(apostrophes, "").replace(junk, " ").trim().split(spaces).filter { it.isNotEmpty() }

    /** Two to four words as a username might join them: "charlidamelio", "charli.damelio", "charli_damelio". */
    fun gluedHandles(words: List<String>): List<String> =
        if (words.size in 2..4) listOf("", ".", "_").map { words.joinToString(it) } else emptyList()

    /** Whether [user]'s username or nickname holds what [query] says, glued or not. */
    fun matches(user: TikTokSearchUser, query: String): Boolean {
        val folded = fold(query)
        if (folded.isEmpty()) return false
        return folded in fold(user.uniqueId) || folded in fold(user.nickname) || folded.replace(" ", "") in user.uniqueId.lowercase()
    }

    /** [raw] as [matches] compares it: lowercased, without an "@" in front, apostrophes or punctuation, spaces single. */
    fun fold(raw: String): String =
        raw.lowercase().removePrefix("@").replace(apostrophes, "").replace(junk, " ").replace(spaces, " ").trim()

    /** The people a search shows: those [found] by username first, then the [discover] people who match [query], each once. */
    fun people(query: String, found: List<TikTokSearchUser>, discover: List<TikTokSearchUser>): List<TikTokSearchUser> =
        (found + discover.filter { matches(it, query) }).distinctBy { it.uniqueId.lowercase() }
}
