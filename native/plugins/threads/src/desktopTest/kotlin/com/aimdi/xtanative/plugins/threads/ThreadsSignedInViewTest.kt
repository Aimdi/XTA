package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsActivityItem
import com.aimdi.xtanative.networks.threads.ThreadsActivityKind
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsOwnFeed
import com.aimdi.xtanative.networks.threads.ThreadsSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a signed-in reader is offered of their own Threads, where each leads, and what a list says when Threads does not give it. */
class ThreadsSignedInViewTest {
    private val cookies = ThreadsSession(cookieHeader = "sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g")
    private val bearer = ThreadsSession(bearer = "IGT:2:made-up")

    @Test
    fun nothingIsOfferedWithoutASessionAndAListOnlyOnceItsPathIsKnown() {
        val everything: (ThreadsSignedInPlace) -> Boolean = { true }
        assertEquals(emptyList(), ThreadsSignedInRules.offered(ThreadsSession(), everything))
        assertEquals(ThreadsSignedInPlace.entries, ThreadsSignedInRules.offered(cookies, everything))
        assertEquals(
            listOf(ThreadsSignedInPlace.FOLLOWING, ThreadsSignedInPlace.ACTIVITY, ThreadsSignedInPlace.SAVED, ThreadsSignedInPlace.LIKED),
            ThreadsSignedInRules.offered(bearer, everything),
            "whom a Bearer signs in as is read nowhere, so it has no profile to open",
        )
        assertEquals(listOf(ThreadsSignedInPlace.PROFILE), ThreadsSignedInRules.offered(cookies), "no list Meta was never seen to answer")
        assertEquals(emptyList(), ThreadsSignedInRules.offered(bearer))
        assertEquals(listOf(ThreadsSignedInPlace.ACTIVITY), ThreadsSignedInRules.offered(bearer) { it == ThreadsSignedInPlace.ACTIVITY })
    }

    @Test
    fun eachPlaceHasARouteOfItsOwnThatReadsBack() {
        ThreadsOwnFeed.entries.forEach { feed ->
            assertEquals(ThreadsRoute.OwnFeed(feed), ThreadsRoutes.parse(ThreadsRoutes.ownFeed(feed)))
        }
        assertEquals(ThreadsRoute.Activity, ThreadsRoutes.parse(ThreadsSignedInRules.route(ThreadsSignedInPlace.ACTIVITY)!!))
        assertEquals(ThreadsRoute.OwnFeed(ThreadsOwnFeed.SAVED), ThreadsRoutes.parse(ThreadsSignedInRules.route(ThreadsSignedInPlace.SAVED)!!))
        assertNull(ThreadsSignedInRules.route(ThreadsSignedInPlace.PROFILE), "the reader's handle is asked when the row is tapped")
        assertNull(ThreadsRoutes.parse("own?feed=nothing"))
        assertNull(ThreadsRoutes.parse("own"))
        ThreadsSignedInPlace.entries.forEach { place -> assertTrue(ThreadsSignedInRules.label(place).get("en").isNotBlank()) }
    }

    @Test
    fun anActivityLineOpensItsPostElseWhoeverDidIt() {
        val line = ThreadsActivityItem("1", ThreadsActivityKind.REPLY, "ann replied", handle = "ann", postCode = "Cx1")
        assertEquals(ThreadsRoute.Post("https://www.threads.com/t/Cx1", ""), ThreadsRoutes.parse(ThreadsSignedInRules.opens(line)!!))
        assertEquals(ThreadsRoute.Profile("ann"), ThreadsRoutes.parse(ThreadsSignedInRules.opens(line.copy(postCode = null))!!))
        assertNull(ThreadsSignedInRules.opens(line.copy(postCode = null, handle = null)))
    }

    @Test
    fun aListNoPathAnswersSaysThreadsDoesNotGiveItHere() {
        assertEquals(Strings.signed_in_unavailable.get("en"), ThreadsFailures.ownList(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404"), "en"))
        assertEquals(Strings.error_session_suspended.get("en"), ThreadsFailures.ownList(ThreadsException(ThreadsFailure.SESSION_SUSPENDED, "parked"), "en"))
    }
}
