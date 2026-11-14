package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What round 22 does with the doc ids CI's probe read out of threads.com's
 * bundles: the refetch queries a list's next pages fall back on, Relay's
 * provided variables, the inline replies asked with every variable their
 * query declares, and a guest's people reads. Every page and answer here is
 * made up; no answer of these queries was ever seen.
 */
class ThreadsRefetchTest {
    private val token = """<script>["LSD",[],{"token":"tok"}]</script>"""
    private val ownId = "24000000000000001"
    private val profileRefetchId = ThreadsRepairs().profileRefetchDocId
    private val withoutDocId by lazy { fixture("paging_profile_page.html").replace(Regex(""""queryID":"\d+",?"""), "") }

    private fun variables(asked: Asked): Map<String, JsonElement> = ThreadsJson.decode(asked.form.getValue("variables")) as JsonObject

    private fun own(asked: Asked) = variables(asked).filterKeys { !ThreadsRelayProviders.isProvided(it) }

    private fun posts(meta: FakeMeta) = meta.asked.filter { it.method == "POST" }

    /** A query's answer as a page preloads it. */
    private fun answered(name: String, data: Any?): String {
        val result = mapOf("__bbox" to mapOf("complete" to true, "result" to mapOf("data" to data)))
        val stream = listOf("RelayPrefetchedStreamCache", "next", emptyList<Any>(), listOf("adp_${name}RelayPreloader_1", result))
        return """<script type="application/json" data-sjs>${json(mapOf("require" to listOf(stream)))}</script>"""
    }

    /** The queries a page says it preloaded, each with its variables and, when given, its doc id. */
    private fun preloaded(vararg queries: Triple<String, String?, Map<String, Any?>>): String {
        val listed = queries.map { (name, docId, variables) ->
            listOfNotNull("preloaderID" to "adp_${name}RelayPreloader_1", docId?.let { "queryID" to it }, "variables" to variables).toMap()
        }
        val preload = listOf("BarcelonaRelayPreloaders", "preload", emptyList<Any>(), listOf(mapOf("queries" to listed)))
        return """<script type="application/json" data-sjs>${json(mapOf("require" to listOf(preload)))}</script>"""
    }

    /** A post page whose one reply thread goes on: its posts' own `page_info` names where. */
    private fun conversationPage(threadInfo: Map<String, Any?>, node: Map<String, Any?> = emptyMap()): String {
        val thread = mapOf("node" to (mapOf("posts" to mapOf("edges" to listOf(mapOf("node" to postJson("2", user = "a"))), "page_info" to threadInfo)) + node))
        val replies = mapOf("direct_replies" to mapOf("edges" to listOf(thread), "page_info" to mapOf("has_next_page" to false)))
        return "<html>$token" +
            preloaded(Triple("BarcelonaPostPageDownwardQuery", "24000000000000002", mapOf("postID" to "1", "__relay_internal__pv__BarcelonaHasCommunitiesrelayprovider" to true))) +
            answered("BarcelonaPostPageTargetQuery", mapOf("media" to postJson("1"))) +
            answered("BarcelonaPostPageDownwardQuery", mapOf("media" to postJson("1", info = replies))) +
            "</html>"
    }

    @Test
    fun aListWhoseOwnQueryIsRefusedGoesOnByItsRefetchQueryAndStaysOnIt(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            when {
                asked.method == "GET" -> ok(fixture("paging_profile_page.html"))
                asked.form["doc_id"] == ownId -> XResponse(400, "{}")
                else -> ok(fixture("paging_next_answer.json"))
            }
        }
        val client = direct(meta)
        client.guestProfile("zuck")
        val cursor = assertNotNull(client.profileNext("zuck"))
        assertEquals(ownId, ThreadsNextPage.decode(cursor)?.docId, "a page that names its query's doc id starts from it, as before")

        val more = client.moreThreads("zuck", cursor)

        assertEquals(listOf("3400000000000000102", "3400000000000000103"), more.items.map { it.id })
        assertFalse(more.refused)
        assertEquals(listOf(ownId, profileRefetchId), posts(meta).map { it.form["doc_id"] }, "the page's own query first, then the refetch once")
        val refetch = posts(meta).last()
        assertEquals("BarcelonaProfileThreadsTabRefetchableDirectQuery", refetch.form["fb_api_req_friendly_name"])
        assertEquals("BarcelonaProfileThreadsTabRefetchableDirectQuery", refetch.headers["X-FB-Friendly-Name"])
        assertEquals(mapOf("userID" to JsonPrimitive("314216"), "after" to JsonPrimitive("QVFIcursor1"), "first" to JsonPrimitive(ThreadsNextPage.PAGE_SIZE)), own(refetch))
        assertEquals(JsonPrimitive(false), variables(refetch)["__relay_internal__pv__BarcelonaIsLoggedInrelayprovider"], "the page's value")
        assertEquals(JsonPrimitive(true), variables(refetch)["__relay_internal__pv__BarcelonaIsLoggedOutrelayprovider"], "a guest's, where no page said")
        assertEquals(ThreadsRelayProviders.defaults.size, variables(refetch).keys.count(ThreadsRelayProviders::isProvided))

        val next = assertNotNull(ThreadsNextPage.decode(more.next))
        assertEquals("BarcelonaProfileThreadsTabRefetchableDirectQuery" to "QVFIcursor2", next.query to next.after)
        client.moreThreads("zuck", more.next!!)
        assertEquals(listOf(ownId, profileRefetchId, profileRefetchId), posts(meta).map { it.form["doc_id"] }, "the pages after ask the query that answered, once each")
    }

    @Test
    fun aListThatPagesTodayNeverAsksItsRefetchQuery(): Unit = runBlocking {
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok(fixture("paging_profile_page.html")) else ok(fixture("paging_next_answer.json")) }
        val client = direct(meta)
        client.guestProfile("zuck")

        val more = client.moreThreads("zuck", client.profileNext("zuck")!!)

        assertEquals(2, more.items.size)
        assertEquals(listOf(ownId), posts(meta).map { it.form["doc_id"] })
        assertEquals(mapOf("userID" to JsonPrimitive("314216"), "after" to JsonPrimitive("QVFIcursor1"), "first" to JsonPrimitive(ThreadsNextPage.PAGE_SIZE)), own(posts(meta).single()))
        assertEquals(1, variables(posts(meta).single()).keys.count(ThreadsRelayProviders::isProvided), "its own query is sent what its page preloaded it with and nothing more")
    }

    @Test
    fun aRefetchThatBringsNothingEitherLeavesTheListAsItsOwnQueryLeftIt(): Unit = runBlocking {
        var second = XResponse(400, "{}")
        val meta = FakeMeta { asked ->
            when {
                asked.method == "GET" -> ok(fixture("paging_profile_page.html"))
                asked.form["doc_id"] == ownId -> ok("""{"errors":[{"code":1675030}],"data":null}""")
                else -> second
            }
        }
        val client = direct(meta)
        client.guestProfile("zuck")
        val cursor = client.profileNext("zuck")!!

        listOf(XResponse(400, "{}"), ok("<!DOCTYPE html>"), ok("""{"data":{"mediaData":{"edges":[]}}}"""), XResponse(429, ""), XResponse(503, "")).forEach { said ->
            second = said
            val more = client.moreThreads("zuck", cursor)
            assertTrue(more.refused, "today's answer: the rest is at threads.com (${said.code})")
            assertEquals(emptyList(), more.items)
        }
        assertEquals(10, posts(meta).size, "the refetch is asked once a page, never again for it")
    }

    @Test
    fun aThrottledListIsThrownAsBeforeAndAWithdrawnRefetchIsNeverAsked(): Unit = runBlocking {
        val throttling = FakeMeta { asked -> if (asked.method == "GET") ok(fixture("paging_profile_page.html")) else XResponse(429, "") }
        val throttled = direct(throttling)
        throttled.guestProfile("zuck")
        assertEquals(ThreadsFailure.THROTTLED, (runCatching { throttled.moreThreads("zuck", throttled.profileNext("zuck")!!) }.exceptionOrNull() as ThreadsException).failure)
        assertEquals(1, posts(throttling).size, "a throttle is waited out, not asked round")

        val refusing = FakeMeta { asked -> if (asked.method == "GET") ok(fixture("paging_profile_page.html")) else XResponse(400, "{}") }
        val off = direct(refusing, repairs = ThreadsRepairs.published(mapOf("threads.profile_refetch_doc_id" to "0")))
        off.guestProfile("zuck")
        assertTrue(off.moreThreads("zuck", off.profileNext("zuck")!!).refused)
        assertEquals(listOf(ownId), posts(refusing).map { it.form["doc_id"] })
    }

    @Test
    fun aPageThatNamesACursorButNoDocIdIsFollowedByTheRefetchQuery(): Unit = runBlocking {
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok(withoutDocId) else ok(fixture("paging_next_answer.json")) }
        val client = direct(meta)
        client.guestProfile("zuck")

        val first = assertNotNull(ThreadsNextPage.decode(client.profileNext("zuck")), "before: no next page, and the profile ended at its first")
        assertEquals("BarcelonaProfileThreadsTabRefetchableDirectQuery" to profileRefetchId, first.query to first.docId)
        assertEquals(JsonPrimitive("314216"), first.variables["userID"], "the variables its own query was preloaded with")
        assertEquals("QVFIcursor1", first.after)

        val more = client.moreThreads("zuck", first.encoded())
        assertEquals(2, more.items.size)
        assertEquals(listOf(profileRefetchId), posts(meta).map { it.form["doc_id"] })

        val off = direct(FakeMeta { ok(withoutDocId) }, repairs = ThreadsRepairs.published(mapOf("threads.profile_refetch_doc_id" to "0")))
        off.guestProfile("zuck")
        assertNull(off.profileNext("zuck"), "withdrawn, the list ends where it did")
    }

    @Test
    fun aFirstPagesFallbackStartsFromItsSeedWhereThePagePreloadedNoVariables() {
        val page = answered("BarcelonaSearchResultsQuery", mapOf("searchResults" to mapOf("edges" to emptyList<Any>(), "page_info" to mapOf("end_cursor" to "S1", "has_next_page" to true))))
        val refetch = { seed: JsonObject? -> ThreadsRefetchBy(ThreadsRefetch.SEARCH.query, "7000000000000007") { seed } }

        val next = assertNotNull(ThreadsNextPages.first(page, refetch(ThreadsRefetch.SEARCH.seed(" kiln "))) { "Search" in it.query.orEmpty() })
        assertEquals(ThreadsNextPage(ThreadsRefetch.SEARCH.query, "7000000000000007", JsonObject(mapOf("query" to JsonPrimitive("kiln"))), "S1", "data.searchResults.page_info"), next)
        assertNull(ThreadsNextPages.first(page, refetch(null)) { true }, "no variables to ask it with")
        assertNull(ThreadsNextPages.first(page, refetch(ThreadsRefetch.SEARCH.seed(""))) { true })
        assertNull(ThreadsNextPages.first(page) { true }, "without a fallback, as before")
        assertNull(ThreadsNextPages.first(page, ThreadsRefetchBy("X", "not-an-id") { JsonObject(emptyMap()) }) { true })
        assertNull(ThreadsNextPages.first(page, refetch(ThreadsRefetch.SEARCH.seed("kiln"))) { false }, "a list not wanted is not followed")
        val ended = page.replace("true", "false")
        assertNull(ThreadsNextPages.first(ended, refetch(ThreadsRefetch.SEARCH.seed("kiln"))) { true }, "a list Meta says ends has no next page")
        assertEquals(ThreadsRefetch.REPLIES, ThreadsRefetch.named("BarcelonaPostPageDirectRepliesRefetchQuery"))
        assertNull(ThreadsRefetch.named("BarcelonaPostPageDownwardQuery"))
    }

    @Test
    fun aSearchOfWordsFallsBackOnItsRefetchQueryAndATopicOrRecentOneDoesNot(): Unit = runBlocking {
        val page = "<html>$token" + answered("BarcelonaSearchResultsQuery", mapOf("searchResults" to mapOf("edges" to emptyList<Any>(), "page_info" to mapOf("end_cursor" to "S1", "has_next_page" to true)))) + "</html>"
        val client = direct(FakeMeta { ok(page) })

        val next = assertNotNull(ThreadsNextPage.decode(client.searchFound("kiln").next))
        assertEquals(ThreadsRefetch.SEARCH.query to ThreadsRepairs().searchRefetchDocId, next.query to next.docId)
        assertEquals(JsonObject(mapOf("query" to JsonPrimitive("kiln"))), next.variables)
        assertNull(client.searchFound("kiln", tag = true).next, "a topic's variables are not known")
        assertNull(client.searchFound("kiln", recent = true).next)
    }

    @Test
    fun relaysProvidedVariablesAreReadOffAPageAndSentOverAGuestsDefaults() {
        val page = """<script>{"variables":{"userID":"1","__relay_internal__pv__BarcelonaHasCommunitiesrelayprovider":true,"__relay_internal__pv__BarcelonaIsLoggedInrelayprovider" : false,""" +
            """"__relay_internal__pv__BarcelonaNewOnerelayprovider":true,"__relay_internal__pv__BarcelonaHasCommunitiesrelayprovider":false,"__relay_internal__pv__BarcelonaTextrelayprovider":"x"}}</script>"""
        val read = ThreadsRelayProviders.read(page)
        assertEquals(mapOf("BarcelonaHasCommunitiesrelayprovider" to true, "BarcelonaIsLoggedInrelayprovider" to false, "BarcelonaNewOnerelayprovider" to true), read)
        assertEquals(emptyMap(), ThreadsRelayProviders.read("<html>__relay_internal__pv__</html>"))

        val sent = ThreadsRelayProviders.with(JsonObject(mapOf("postID" to JsonPrimitive("9"), "__relay_internal__pv__BarcelonaIsCrawlerrelayprovider" to JsonPrimitive(true))), read)
        assertEquals(JsonPrimitive("9"), sent["postID"])
        assertEquals(JsonPrimitive(true), sent["__relay_internal__pv__BarcelonaHasCommunitiesrelayprovider"], "the page's value")
        assertEquals(JsonPrimitive(true), sent["__relay_internal__pv__BarcelonaNewOnerelayprovider"], "a name only a page showed")
        assertEquals(JsonPrimitive(true), sent["__relay_internal__pv__BarcelonaIsCrawlerrelayprovider"], "a query's own value stands")
        assertEquals(JsonPrimitive(false), sent["__relay_internal__pv__BarcelonaHasSaveCountrelayprovider"])
        assertEquals(JsonPrimitive(true), sent["__relay_internal__pv__BarcelonaShouldFetchPostAuthorFullNamerelayprovider"])
        assertTrue(ThreadsRelayProviders.defaults.size > 60)
        assertEquals(setOf("x"), ThreadsRelayProviders.with(JsonObject(mapOf("x" to JsonNull)), read, only = emptySet()).keys)
        assertEquals(
            setOf("__relay_internal__pv__BarcelonaHasCommunitiesrelayprovider"),
            ThreadsRelayProviders.with(JsonObject(emptyMap()), read, only = setOf("BarcelonaHasCommunitiesrelayprovider")).keys,
        )
    }

    @Test
    fun aReplyThreadKeepsWhereItsRestStartsAsItsEdgeNamesIt() {
        fun token(info: Map<String, Any?>, node: Map<String, Any?> = emptyMap()) = ThreadsPostPage.read(conversationPage(info, node)).replies.single().pagingToken

        assertEquals("AFTER2", token(mapOf("end_cursor" to "AFTER2", "has_next_page" to true), mapOf("fetch_token" to "FETCH")), "the cursor after its posts when they go on")
        assertEquals("FETCH", token(mapOf("end_cursor" to "AFTER2", "has_next_page" to false), mapOf("fetch_token" to "FETCH")))
        assertEquals("DOWN", token(mapOf("has_next_page" to false), mapOf("paging_tokens" to mapOf("downwards" to "DOWN"), "fetch_token" to "FETCH")))
        assertNull(token(mapOf("has_next_page" to false)))
        assertNull(token(mapOf("has_next_page" to true, "end_cursor" to 7), mapOf("fetch_token" to " ")), "a token that is no text is none")
        val twice = ThreadsReplyThread(emptyList(), pagingToken = "a").then(ThreadsReplyThread(emptyList(), pagingToken = "b"))
        assertEquals("b", twice.pagingToken, "the rest starts where the last read says")
    }

    @Test
    fun repliesUnderAReplyAreAskedWithItsConversationsPostItsThreadsTokenAndThePagesFlags(): Unit = runBlocking {
        val under = mapOf("media" to postJson("2", user = "a", info = mapOf("direct_replies" to mapOf("edges" to listOf(mapOf("node" to mapOf("posts" to mapOf("edges" to listOf(mapOf("node" to postJson("3", user = "b"))), "page_info" to mapOf("end_cursor" to "AFTER3", "has_next_page" to true))))), "page_info" to mapOf("has_next_page" to false)))))
        val meta = FakeMeta { asked ->
            if (asked.method == "GET") ok(conversationPage(mapOf("end_cursor" to "AFTER2", "has_next_page" to true))) else ok(json(mapOf("data" to under)).toString())
        }
        val on = ThreadsRepairs.published(mapOf("threads.inline_replies_doc_id" to "7000000000000001", "threads.inline_replies_expansion" to "CHILDREN"))
        val client = direct(meta, repairs = on)
        val page = client.postPage("https://www.threads.com/@zuck/post/C1")
        val reply = page.replies.single().posts.last()
        assertTrue(page.replies.single().hasMore)

        val brought = client.deeperReplies(reply)

        assertEquals(listOf("3"), brought?.posts?.map { it.id })
        assertEquals(listOf("GET /@zuck/post/C1", "POST /api/graphql"), meta.asked.map { "${it.method} ${it.path}" }, "the conversation's page gave the token: no page is read for one")
        val asked = posts(meta).single()
        assertEquals(
            mapOf(
                "postID" to JsonPrimitive(ThreadsPeople.idOfShortcode("C2")),
                "permalinkPostID" to JsonPrimitive(ThreadsPeople.idOfShortcode("C1")),
                "pagingToken" to JsonPrimitive("AFTER2"),
                "expansionType" to JsonPrimitive("CHILDREN"),
            ),
            own(asked),
        )
        assertEquals(JsonPrimitive(true), variables(asked)["__relay_internal__pv__BarcelonaHasCommunitiesrelayprovider"], "as the page's own queries were told")
        assertEquals(JsonPrimitive(false), variables(asked)["__relay_internal__pv__BarcelonaIsLoggedInrelayprovider"])

        client.deeperReplies(assertNotNull(brought).posts.last())
        assertEquals(JsonPrimitive("AFTER3"), own(posts(meta).last())["pagingToken"], "the thread read in place goes on from where its answer says")
        assertEquals(JsonPrimitive(ThreadsPeople.idOfShortcode("C1")), own(posts(meta).last())["permalinkPostID"])
    }

    @Test
    fun asShippedAGuestsPeopleReadsAreOffAndAskMetaNothing(): Unit = runBlocking {
        val meta = FakeMeta { ok("""{"data":{"users":[{"pk":"11","username":"never_asked"}]}}""") }
        val client = direct(meta)

        assertFalse(client.readsGuestPeople)
        assertFalse(client.readsRecommendedPeople)
        assertNull(client.guestPeople("kiln"))
        assertEquals(emptyList(), client.recommendedPeople())
        assertEquals(emptyList(), meta.asked.toList())
    }

    @Test
    fun withItsIdAGuestsPeopleSearchIsAskedAsTheSearchPageAsksItAndReadWhereverItNamesAccounts(): Unit = runBlocking {
        val found = mapOf(
            "xdt_api__v1__users__search_connection" to mapOf(
                "edges" to listOf(
                    mapOf("node" to mapOf("pk" to "11", "username" to "kiln_co", "full_name" to "Kiln", "is_verified" to true, "follower_count" to 5)),
                    mapOf("node" to mapOf("user" to mapOf("id" to "12", "username" to "Potter"))),
                    mapOf("node" to mapOf("pk" to "11", "username" to "KILN_CO")),
                    mapOf("node" to postJson("9", user = "an_author", extra = mapOf("pk" to "9"))),
                    mapOf("node" to mapOf("pk" to "not-digits", "username" to "odd")),
                    mapOf("node" to mapOf("pk" to "13")),
                ),
            ),
        )
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok("<html>$token</html>") else ok(json(mapOf("data" to found)).toString()) }
        val memory = ThreadsMemory.InMemory()
        val on = ThreadsRepairs.published(mapOf("threads.search_users_doc_id" to "7000000000000004"))
        val client = direct(meta, memory = memory, repairs = on)

        assertTrue(client.readsGuestPeople)
        val people = assertNotNull(client.guestPeople(" @kiln "))

        assertEquals(listOf("kiln_co", "Potter"), people.map { it.username }, "each account once; a post's author is no result")
        assertEquals("Kiln" to true, people.first().fullName to people.first().isVerified)
        assertNull(people.first().followerCount, "a row carries no counts")
        assertEquals(mapOf("kiln_co" to "11", "potter" to "12"), memory.kept.userIds, "their ids are kept, so opening one asks no search")
        assertEquals(listOf("GET /search", "POST /api/graphql"), meta.asked.map { "${it.method} ${it.path}" })
        val asked = posts(meta).single()
        assertEquals("7000000000000004", asked.form["doc_id"])
        assertEquals("BarcelonaSearchUserResultsRefetchableQuery", asked.form["fb_api_req_friendly_name"])
        assertEquals(mapOf("query" to JsonPrimitive("kiln"), "first" to JsonPrimitive(ThreadsBundleReads.PEOPLE_ASKED)), own(asked))
        assertEquals("tok", asked.form["lsd"])
        assertTrue(asked.headers.getValue("Referer").startsWith("https://www.threads.com/search?q=kiln"))
        assertNull(client.guestPeople("  "), "blank words ask nothing")
        assertEquals(2, meta.asked.size)

        val renamed = direct(meta, repairs = ThreadsRepairs.published(mapOf("threads.search_users_doc_id" to "7000000000000004", "threads.search_users_variable" to "search_query")))
        renamed.guestPeople("kiln")
        assertEquals(JsonPrimitive("kiln"), own(posts(meta).last())["search_query"], "a published variable name is the one sent")
    }

    @Test
    fun aRefusedPeopleSearchIsNoResultAndAThrottleIsThrown(): Unit = runBlocking {
        val on = ThreadsRepairs.published(mapOf("threads.search_users_doc_id" to "7000000000000004", "threads.recommended_users_doc_id" to "7000000000000005"))
        fun client(said: XResponse) = direct(FakeMeta { asked -> if (asked.method == "GET") ok("<html>$token</html>") else said }, repairs = on)

        listOf(XResponse(400, ""), ok("<html>sign in</html>"), ok("""{"errors":[{"message":"no"}]}""")).forEach { said ->
            assertNull(client(said).guestPeople("kiln"))
            assertEquals(emptyList(), client(said).recommendedPeople())
        }
        assertEquals(emptyList(), client(ok("""{"data":{"viewer":null}}""")).guestPeople("kiln"), "an answer that names no one is an empty search")
        assertNull(direct(FakeMeta { ok("<html></html>") }, repairs = on).guestPeople("kiln"), "a page without a token")
        assertEquals(ThreadsFailure.THROTTLED, (runCatching { client(XResponse(429, "")).guestPeople("kiln") }.exceptionOrNull() as ThreadsException).failure)
        assertEquals(emptyList(), client(XResponse(429, "")).recommendedPeople(), "a suggestion is never worth an error")
        assertEquals(emptyList(), client(XResponse(503, "")).recommendedPeople())
    }

    @Test
    fun withItsIdTheAccountsThreadsRecommendsAreAskedOnceAndRemembered(): Unit = runBlocking {
        val recommended = mapOf("recommended_users" to mapOf("edges" to listOf(mapOf("node" to mapOf("pk" to "21", "username" to "ceramics")), mapOf("node" to mapOf("pk" to "22", "username" to "glaze")))))
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok("<html>$token</html>") else ok(json(mapOf("data" to recommended)).toString()) }
        val time = FakeTime()
        val client = direct(meta, time = time, repairs = ThreadsRepairs.published(mapOf("threads.recommended_users_doc_id" to "7000000000000005")))

        assertTrue(client.readsRecommendedPeople)
        assertEquals(listOf("ceramics", "glaze"), client.recommendedPeople().map { it.username })
        assertEquals(listOf("ceramics", "glaze"), client.recommendedPeople().map { it.username })
        assertEquals(listOf("GET /search", "POST /api/graphql"), meta.asked.map { "${it.method} ${it.path}" }, "remembered as the trends are")
        val asked = posts(meta).single()
        assertEquals("7000000000000005", asked.form["doc_id"])
        assertEquals("BarcelonaSearchRecommendedUsersRefetchableQuery", asked.form["fb_api_req_friendly_name"])
        assertEquals(mapOf("first" to JsonPrimitive(ThreadsBundleReads.PEOPLE_ASKED)), own(asked))

        time.now += ThreadsDirectClient.TRENDS_TTL_MILLIS + 1
        client.recommendedPeople()
        assertEquals(2, posts(meta).size, "past half an hour they are asked again")
    }

    @Test
    fun accountsAreReadOnlyUnderDataAndNeverMoreThanARowHolds() {
        fun users(root: Any?) = ThreadsGuestPeople.users(listOf(json(root)))
        val many = (1..ThreadsGuestPeople.MAX + 5).map { mapOf("pk" to "$it", "username" to "u$it") }

        assertEquals(ThreadsGuestPeople.MAX, users(mapOf("data" to mapOf("users" to many))).size)
        assertEquals(emptyList(), users(mapOf("users" to many)), "only under data")
        assertEquals(emptyList(), users(mapOf("data" to mapOf("users" to listOf(mapOf("username" to "no_id"), mapOf("pk" to "1"), "text", 7, null)))))
        assertEquals(emptyList(), ThreadsGuestPeople.users(emptyList()))
        assertEquals(emptyList(), ThreadsGuestPeople.users(listOf(JsonPrimitive("x"), JsonNull)))
        assertEquals(listOf("outer"), users(mapOf("data" to mapOf("pk" to "1", "username" to "outer", "friends" to listOf(mapOf("pk" to "2", "username" to "inner"))))).map { it.username }, "an account's own lists are not results")
    }
}
