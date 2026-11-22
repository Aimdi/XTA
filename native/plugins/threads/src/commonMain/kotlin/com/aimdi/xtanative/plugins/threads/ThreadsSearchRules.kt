package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsSession
import kotlinx.coroutines.CancellationException

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
     * The accounts a people search finds: what a [guest] is shown, and the
     * reader's [account] only when the guest read is off, refused, failed
     * or found no one, so a search a guest can make never spends it.
     */
    suspend fun people(guest: suspend () -> List<ThreadsProfile>?, account: (suspend () -> List<ThreadsProfile>)?): List<ThreadsProfile> {
        val shown = try {
            guest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (account == null) throw e
            null
        }
        return shown?.ifEmpty { null } ?: account?.invoke() ?: shown.orEmpty()
    }

    /**
     * Whether a search's words and handles list accounts before anything
     * else: only with cookies and the reader's opt-in to session reads
     * ([ThreadsSession.useSessionApis]). Otherwise they open what a guest
     * reads, and the account is spent on the People tab once it is opened.
     */
    fun listsPeopleFirst(session: ThreadsSession): Boolean = session.hasCookies && session.useSessionApis

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
