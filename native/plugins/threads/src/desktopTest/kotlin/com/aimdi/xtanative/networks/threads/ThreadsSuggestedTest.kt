package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The accounts Threads suggests a signed-in reader: read out of the shapes
 * Meta may answer (made-up fixtures, none seen from a session yet), only with
 * the session, through the account guard, by a route CI has seen answer.
 */
class ThreadsSuggestedTest {
    private val time = FakeTime()
    private val cookies = ThreadsSession(COOKIES)
    private val on = ThreadsSuggestedRoutes(path = true, query = true)
    private val pathsOnly = ThreadsSuggestedRoutes(path = true, query = false)

    private fun node(username: String?, pk: Any? = "1${username?.length ?: 0}", name: String? = username?.uppercase(), following: Boolean? = false) = buildMap<String, Any?> {
        username?.let { put("username", it) }
        pk?.let { put("pk", it) }
        name?.let { put("full_name", it) }
        put("profile_pic_url", "https://scontent.cdninstagram.com/$username.jpg")
        following?.let { put("friendship_status", mapOf("following" to it)) }
        put("profile_context_facepile_users", listOf(mapOf("username" to "friend", "pk" to "77")))
    }

    private fun recommended(vararg nodes: Map<String, Any?>) = mapOf(
        "data" to mapOf(
            "xdt_text_app_recommended_users" to mapOf(
                "edges" to nodes.map { mapOf("node" to it, "cursor" to "c") },
                "page_info" to mapOf("has_next_page" to true, "end_cursor" to "e"),
            ),
        ),
    )

    // ---- Parsing ----

    @Test
    fun theRecommendedUsersQuerysEdgesAreReadNodeByNode() {
        val body = j(recommended(node("ceramics", pk = "101"), node("glaze", pk = 102L, name = null)))
        val found = ThreadsSuggested.fromQuery(body)
        assertEquals(listOf("ceramics", "glaze"), found.map { it.handle })
        assertEquals(ThreadsSuggestedAccount("ceramics", "CERAMICS", "https://scontent.cdninstagram.com/ceramics.jpg", "101"), found.first())
        assertEquals("glaze", found[1].name, "no name: the handle stands in")
        assertEquals("102", found[1].pk)
    }

    @Test
    fun aNodeMissingWhatACardNeedsIsSkipped() {
        val body = j(
            recommended(
                node(null, pk = "5"),
                node("noid", pk = null),
                node("words", pk = "abc"),
                node("followed", pk = "9", following = true),
                node("kept", pk = "10", following = null),
                node("Kept", pk = "10"),
                mapOf("pk" to "3"),
            ),
        )
        assertEquals(listOf("kept"), ThreadsSuggested.fromQuery(body).map { it.handle }, "once each, and only with a username and an id of digits")
        assertEquals(emptyList(), ThreadsSuggested.fromQuery(j(mapOf("data" to null, "errors" to listOf(mapOf("message" to "field_exception"))))))
        assertEquals(emptyList(), ThreadsSuggested.fromQuery(j("<html>")))
    }

    @Test
    fun theRightPanelsShapeAndTheAppApisShapesAreReadToo() {
        val panel = j(mapOf("data" to mapOf("xdt_text_app_accounts_recs" to mapOf("users" to listOf(node("panel", pk = "4"))))))
        assertEquals(listOf("panel"), ThreadsSuggested.fromQuery(panel).map { it.handle })
        assertEquals(listOf("a"), ThreadsSuggested.fromRest(j(mapOf("users" to listOf(node("a", pk = "1"))))).map { it.handle })
        assertEquals(listOf("b"), ThreadsSuggested.fromRest(j(mapOf("suggested_users" to listOf(mapOf("user" to node("b", pk = "2")))))).map { it.handle })
        assertEquals(listOf("c"), ThreadsSuggested.fromRest(j(mapOf("groups" to listOf(mapOf("items" to listOf(mapOf("user" to node("c", pk = "3")))))))).map { it.handle })
        assertEquals(emptyList(), ThreadsSuggested.fromRest(j(mapOf("status" to "ok"))))
    }

    @Test
    fun aTimelinePageCarriesItsSuggestionsBetweenThePosts() {
        val body = j(
            mapOf(
                "items" to listOf(
                    mapOf("post" to postJson("1")),
                    mapOf("suggested_users" to mapOf("suggestions" to listOf(mapOf("user" to node("between", pk = "8")), mapOf("user" to node(null))))),
                ),
            ),
        )
        assertEquals(listOf("between"), ThreadsSuggested.fromTimeline(body).map { it.handle })
        assertEquals(listOf("1"), ThreadsParser.feed(body).map { it.id }, "the posts are read as before")
        assertEquals(emptyList(), ThreadsSuggested.fromTimeline(j(mapOf("items" to listOf(mapOf("post" to postJson("1")))))))
    }

    // ---- When the account is used ----

    @Test
    fun theAccountIsUsedOnlyByARouteKnownWithASessionThatIsReady() {
        assertTrue(ThreadsSuggested.PATH_KNOWN, "CI's probe saw the paths send a guest to sign in")
        assertFalse(ThreadsSuggested.QUERY_KNOWN, "ships off until a session's answer is seen")
        assertTrue(ThreadsSuggested.usesAccount(cookies, ThreadsAccountState.READY), "the shipped paths go with a ready session")
        assertFalse(ThreadsSuggested.usesAccount(cookies, ThreadsAccountState.READY, ThreadsSuggestedRoutes(path = false, query = false)))
        assertTrue(ThreadsSuggested.usesAccount(cookies, ThreadsAccountState.READY, on))
        assertTrue(ThreadsSuggested.usesAccount(ThreadsSession(bearer = "IGT:2:x"), ThreadsAccountState.READY, pathsOnly))
        assertFalse(ThreadsSuggested.usesAccount(ThreadsSession(bearer = "IGT:2:x"), ThreadsAccountState.READY, ThreadsSuggestedRoutes(query = true, path = false)), "the query is sent with cookies only")
        assertFalse(ThreadsSuggested.usesAccount(cookies, ThreadsAccountState.RESTING, on), "a rest shows the guest's strip, asking nothing")
        assertFalse(ThreadsSuggested.usesAccount(cookies, ThreadsAccountState.SIGNED_OUT, on))
        assertFalse(ThreadsSuggested.usesAccount(ThreadsSession(), ThreadsAccountState.READY, on))
    }

    @Test
    fun noRouteAsksNothing() = runBlocking {
        val meta = FakeMeta { ok("{}") }
        val refused = assertFailsWith<ThreadsException> { direct(meta, cookies, time = time).suggestedPeople(ThreadsSuggestedRoutes(path = false, query = false)) }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, refused.failure)
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun theShippedRoutesAskThePathsCIsProbeSawAndNeverTheQuery() = runBlocking {
        val meta = FakeMeta { asked -> if (asked.path == "/api/v1/discover/ayml/") ok(json(mapOf("users" to listOf(node("ceramics", pk = "101")))).toString()) else XResponse(404, "") }
        assertEquals(listOf("ceramics"), direct(meta, cookies, time = time).suggestedPeople().map { it.handle })
        assertEquals(listOf("/api/v1/text_feed/recommended_users/", "/api/v1/discover/ayml/"), meta.asked.map { it.path })
    }

    @Test
    fun thePathsAreASessionReadThroughTheGuardTriedInTurnAndKept() = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.path) {
                "/api/v1/discover/ayml/" -> ok(json(mapOf("users" to listOf(node("ceramics", pk = "101")))).toString())
                else -> XResponse(404, "")
            }
        }
        val client = direct(meta, cookies, time = time)
        assertEquals(listOf("ceramics"), client.suggestedPeople(pathsOnly).map { it.handle })
        assertEquals(listOf("/api/v1/text_feed/recommended_users/", "/api/v1/discover/ayml/"), meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.method == "GET" && "Cookie" in it.headers }, "GETs with the session, nothing written")
        client.suggestedPeople(pathsOnly)
        assertEquals(2, meta.asked.size, "kept for half an hour")
        assertEquals(2, client.guard.refresh().usedToday, "each read sent counted against the account's budget")
    }

    @Test
    fun theQueryIsAQueryWithTheCookiesNeverAMutation() = runBlocking {
        val meta = FakeMeta { ok(json(recommended(node("glaze", pk = "102"))).toString()) }
        val client = direct(meta, cookies, time = time)
        assertEquals(listOf("glaze"), client.suggestedPeople(on).map { it.handle })
        val asked = meta.asked.single()
        assertEquals("POST", asked.method)
        assertEquals("/api/graphql", asked.path)
        assertTrue("Cookie" in asked.headers)
        assertEquals(ThreadsSuggested.QUERY_DOC_ID, asked.form["doc_id"])
        assertEquals("BarcelonaSearchRecommendedUsersRefetchableQuery", asked.form["fb_api_req_friendly_name"])
        assertFalse(asked.body.orEmpty().contains("Mutation"))
        assertEquals("""{"first":20}""", asked.form["variables"])
    }

    @Test
    fun aQuerySentToAChallengeRestsTheAccount() = runBlocking {
        val meta = FakeMeta { XResponse(302, "", mapOf("Location" to listOf("https://www.threads.com/challenge/?next=/"))) }
        val client = direct(meta, cookies, time = time)
        assertFailsWith<ThreadsException> { client.suggestedPeople(on) }
        assertEquals(ThreadsAccountState.RESTING, client.guard.refresh().state)
    }

    @Test
    fun aRestingAccountOrTheBackgroundSendsNothing() = runBlocking {
        val meta = FakeMeta { ok("{}") }
        val parked = direct(meta, cookies, ThreadsMemory.InMemory(ThreadsKept(cooldownUntil = time.now + 60 * 60 * 1000L)), time)
        assertFailsWith<ThreadsException> { parked.suggestedPeople(on) }
        val idle = direct(meta, cookies, time = time)
        assertFailsWith<ThreadsException> { withContext(ThreadsBackground) { idle.suggestedPeople(on) } }
        assertTrue(meta.asked.isEmpty(), "the guard refused both before anything left")
    }

    @Test
    fun aBearersForYouPageKeepsTheSuggestionsItCarried() = runBlocking {
        val meta = FakeMeta {
            ok(json(mapOf("items" to listOf(mapOf("post" to postJson("1")), mapOf("suggested_users" to mapOf("suggestions" to listOf(mapOf("user" to node("between", pk = "8")))))))).toString())
        }
        val client = direct(meta, ThreadsSession(bearer = "IGT:2:test", useSessionApis = true), time = time)
        assertNull(client.timelineSuggestions())
        client.forYouTimeline()
        assertEquals(time.now to listOf("between"), client.timelineSuggestions()?.let { (at, people) -> at to people.map { it.handle } })
        assertEquals(1, meta.asked.size, "nothing more than the page was read")
    }
}
