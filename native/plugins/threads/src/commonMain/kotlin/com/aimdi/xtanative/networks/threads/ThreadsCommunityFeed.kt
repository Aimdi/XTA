package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page

/**
 * A community's feed as a signed-in reader sees it, which Threads may
 * personalise: read with the reader's session through the account guard,
 * only when the reader opens the community, never for Home.
 *
 * No path is known to answer. threads.com reads a community through Relay
 * queries no bundle has named a doc id for (`BarcelonaGetCommunityCustomFeedQuery`
 * was not found, round 22), so these are the app API's likely GETs, tried in
 * turn as every session list is. They stay off ([PATH_KNOWN] false) until
 * CI's probe (ThreadsSignedInLiveTest, `LIVE Threads signed in community …`)
 * sees one send a guest to sign in, as real session paths do, beside the
 * controls; until then a community is read as a guest, as before.
 */
object ThreadsCommunityFeed {
    /** Whether one of [paths] is known to answer a session: off until CI's probe sees one. */
    const val PATH_KNOWN = false

    private val slugOut = Regex("[^\\p{L}\\p{N}_]")

    /** The community's name as a tag is written: its letters and digits, lower case, no spaces. */
    fun slug(name: String): String = name.trim().removePrefix("#").lowercase().replace(slugOut, "")

    /** Where a community named [name] may be read with a session, in the order they are tried. */
    fun paths(name: String): List<String> {
        val slug = slug(name)
        return listOfNotNull(
            "/api/v1/text_feed/tag_feed/",
            "/api/v1/text_feed/community_feed/",
            slug.ifEmpty { null }?.let { "/api/v1/feed/tag/$it/" },
        )
    }

    /** One page's query: the name under each key a path may read it by, after [cursor] when it is not the first. */
    fun params(name: String, cursor: String?): List<Pair<String, String>> = listOfNotNull(
        "tag_name" to name.trim().removePrefix("#"),
        "community_name" to name.trim().removePrefix("#"),
        cursor?.trim()?.ifEmpty { null }?.let { "max_id" to it },
    )

    /** A page of posts, read as the reader's own lists are ([ThreadsListedPosts]). */
    fun page(body: J): Page<ThreadsPost> = ThreadsListedPosts.page(body)

    /**
     * Whether opening a community reads it with the account: a path known to
     * answer ([pathKnown]), a session, and an account that is not signed out.
     * A rest is no reason to skip it: the guard refuses it without sending,
     * and the guest read stands in.
     */
    fun usesAccount(session: ThreadsSession, state: ThreadsAccountState, pathKnown: Boolean = PATH_KNOWN): Boolean =
        pathKnown && session.hasDirectAuth && state != ThreadsAccountState.SIGNED_OUT
}
