package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One account Threads suggests the signed-in reader follow: as its card shows it, with the id Meta keeps it under. */
data class ThreadsSuggestedAccount(
    val handle: String,
    val name: String,
    val avatarUrl: String?,
    val pk: String,
    val verified: Boolean = false,
)

/**
 * Which of the signed-in reader's suggestion reads may be asked
 * ([ThreadsDirectClient.suggestedPeople]): the app API's likely GETs
 * ([path]) and the recommended-users query with the session's cookies
 * ([query]). Each ships off ([SHIPPED]) until CI's probe sees it answer.
 */
data class ThreadsSuggestedRoutes(val path: Boolean = ThreadsSuggested.PATH_KNOWN, val query: Boolean = ThreadsSuggested.QUERY_KNOWN) {
    /** Whether a read with [session] has a route to go by. */
    fun any(session: ThreadsSession): Boolean = (path && session.hasDirectAuth) || (query && session.hasCookies)

    companion object {
        /** The routes as this release ships them. */
        val SHIPPED = ThreadsSuggestedRoutes()
    }
}

/**
 * The accounts Threads suggests a signed-in reader, as threads.com's For
 * you shows them between posts ("Suggested for you"). Read only with the
 * reader's session, through the account guard, never as a guest's read:
 * a guest asking `BarcelonaSearchRecommendedUsersRefetchableQuery` was
 * refused (`field_exception`, round 22).
 *
 * Three sources, none confirmed by a session yet:
 * - a Bearer's For you timeline, which the reader opted in to: Instagram's
 *   timelines carry `suggested_users` items between posts, read out of the
 *   page already read ([fromTimeline]), costing nothing more;
 * - the app API's likely GETs ([PATHS]), tried in turn as every session
 *   list is, off until CI's probe (`LIVE Threads signed in suggested …`)
 *   sees one send a guest to sign in beside the controls ([PATH_KNOWN]);
 * - the recommended-users query with the session's cookies ([QUERY_KNOWN]),
 *   whose answer CI's doc-id probe read the fields of
 *   (`xdt_text_app_recommended_users { edges { node } page_info }`).
 *
 * A query, never a mutation: nothing is followed or dismissed on Threads.
 */
object ThreadsSuggested {
    /** Whether one of [PATHS] is known to answer a session: off until CI's probe sees one. */
    const val PATH_KNOWN = false

    /** Whether the recommended-users query answers a cookie session without the page's `fb_dtsg`: off until a session says so. */
    const val QUERY_KNOWN = false

    /** The query threads.com's search page asks its suggested accounts by (CI's doc-id probe, round 22). */
    const val QUERY = ThreadsBundleReads.RECOMMENDED_USERS

    /** Its doc id as CI's probe read it out of the bundles (round 22); `repairs.json`'s `recommended_users` replaces it. */
    const val QUERY_DOC_ID = "38988761787435415"

    /** How many accounts are asked for and kept. */
    const val ASKED = 20

    /** Where the app API may list the suggested accounts, in the order they are tried. */
    val PATHS = listOf(
        "/api/v1/text_feed/recommended_users/",
        "/api/v1/text_feed/suggested_users/",
        "/api/v1/discover/ayml/",
    )

    /** What each path is asked with. */
    fun params(): List<Pair<String, String>> = listOf("count" to "$ASKED", "module" to "text_app_feed")

    /** The query's variables, as its bundle declares them (`after`, `before`, `first`, `forced_user_ids`, `last`): the first page only. */
    fun variables(): JsonObject = JsonObject(mapOf("first" to JsonPrimitive(ASKED)))

    /**
     * The accounts the recommended-users query answered: `data.xdt_text_app_recommended_users.edges[].node`,
     * or the right panel's `data.xdt_text_app_accounts_recs.users[]`. A node without a username or a
     * numeric id, or one the reader follows on Threads already, is skipped.
     */
    fun fromQuery(body: J): List<ThreadsSuggestedAccount> {
        val data = body["data"]
        val edges = data["xdt_text_app_recommended_users"]["edges"].list.map { it["node"] }
        return people(edges.ifEmpty { data["xdt_text_app_accounts_recs"]["users"].list })
    }

    /**
     * The accounts an app API path answered: `users[]`, `suggested_users[]` or
     * `suggestions[]` (each the account or `{user: …}`), or `groups[].items[]`.
     */
    fun fromRest(body: J): List<ThreadsSuggestedAccount> {
        val lists = listOf(body["users"], body["suggested_users"], body["suggestions"], body["suggested_users"]["suggestions"])
        val flat = lists.flatMap { it.list } + body["groups"].list.flatMap { it["items"].list }
        return people(flat.map { if (it["user"].exists) it["user"] else it })
    }

    /** The accounts a For you timeline page carries between its posts: its items' `suggested_users.suggestions[].user`. */
    fun fromTimeline(body: J): List<ThreadsSuggestedAccount> {
        val items = body["items"].list + body["threads"].list + body["feed_items"].list
        val units = items.map { it["suggested_users"] }.filter { it.exists } + body["suggested_users"].takeIf { it.exists }.let(::listOfNotNull)
        return people(units.flatMap { it["suggestions"].list }.map { if (it["user"].exists) it["user"] else it })
    }

    /** Each account once, in Meta's order, at most [ASKED]. */
    private fun people(nodes: List<J>): List<ThreadsSuggestedAccount> =
        nodes.mapNotNull(::suggestion).distinctBy { it.handle }.take(ASKED)

    private fun suggestion(node: J): ThreadsSuggestedAccount? {
        if (node["friendship_status"]["following"].boolean == true) return null
        val profile = ThreadsParser.profile(node) ?: return null
        val pk = profile.pk.ifEmpty { profile.id }.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        return ThreadsSuggestedAccount(
            handle = profile.username.lowercase(),
            name = profile.fullName.ifBlank { profile.username },
            avatarUrl = profile.profilePicUrl.ifEmpty { null },
            pk = pk,
            verified = profile.isVerified,
        )
    }

    /**
     * Whether the For you row may read the suggestions with the account: a
     * route to go by ([routes]) for the [session], and an account that is
     * neither resting nor signed out (the guard would refuse it; the guest
     * strip stands in, quietly, without asking it).
     */
    fun usesAccount(session: ThreadsSession, state: ThreadsAccountState, routes: ThreadsSuggestedRoutes = ThreadsSuggestedRoutes.SHIPPED): Boolean =
        routes.any(session) && state == ThreadsAccountState.READY
}
