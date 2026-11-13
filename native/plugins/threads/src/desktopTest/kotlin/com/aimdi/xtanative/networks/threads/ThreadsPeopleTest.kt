package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The people lists a signed-in reader is shown: how they are asked for, read, filtered, and that they are only read. */
class ThreadsPeopleTest {
    private val time = FakeTime()
    private val bearer = ThreadsSession(bearer = "Bearer IGT:2:made-up")

    private fun person(handle: String, name: String = "") = ThreadsProfile(pk = "", id = "", username = handle, fullName = name)

    @Test
    fun eachListHasItsOwnPathAndOnlyFollowListsAreSearchedByMeta() {
        assertEquals("/api/v1/friendships/42/followers/", ThreadsPeople.path(ThreadsPeopleList.FOLLOWERS, "42"))
        assertEquals("/api/v1/friendships/42/following/", ThreadsPeople.path(ThreadsPeopleList.FOLLOWING, "42"))
        assertEquals("/api/v1/friendships/42/mutual_followers/", ThreadsPeople.path(ThreadsPeopleList.MUTUAL, "42"))
        assertEquals("/api/v1/media/42/likers/", ThreadsPeople.path(ThreadsPeopleList.LIKERS, "42"))
        assertEquals(
            listOf("count" to "50", "search_surface" to "follow_list_page", "query" to "ann", "max_id" to "c1"),
            ThreadsPeople.params(ThreadsPeopleList.FOLLOWERS, " c1 ", " @ann "),
        )
        assertEquals(listOf("count" to "50"), ThreadsPeople.params(ThreadsPeopleList.MUTUAL, "", "ann"))
        assertEquals(emptyList(), ThreadsPeople.params(ThreadsPeopleList.LIKERS, null, "ann"))
    }

    @Test
    fun aPageReadsEachUserOnceAndTheCursorAfterThem() {
        val page = ThreadsPeople.page(j(ThreadsJson.decode(fixture("friendships_followers.json"))))
        assertEquals(listOf("first.reader", "second_reader"), page.items.map { it.username })
        val first = page.items.first()
        assertEquals("1000000001", first.pk)
        assertTrue(first.isVerified)
        assertEquals("https://cdn.example/first.jpg", first.profilePicUrl)
        assertNull(first.followerCount, "a row never shows a count it was not given")
        assertEquals("1000000002", page.items[1].pk)
        assertEquals("QVFE-made-up-cursor", page.next)
    }

    @Test
    fun aPageEndsWhereMetaSaysAndAMissingListIsEmpty() {
        assertNull(ThreadsPeople.page(j(mapOf("users" to listOf(mapOf("username" to "a")), "next_max_id" to "x", "has_more" to false))).next)
        assertNull(ThreadsPeople.page(j(mapOf("users" to listOf(mapOf("username" to "a")), "big_list" to false))).next)
        assertEquals("12", ThreadsPeople.page(j(mapOf("users" to listOf(mapOf("username" to "a")), "next_max_id" to 12))).next)
        val nothing = ThreadsPeople.page(j(mapOf("status" to "ok", "next_max_id" to "x")))
        assertEquals(emptyList(), nothing.items)
        assertNull(nothing.next, "a page that brought no one names no next page")
    }

    @Test
    fun typedWordsFilterByHandleOrNameIgnoringCaseAndTheAt() {
        val people = listOf(person("ann.lee", "Ann Lee"), person("bob", "Robert Ann"), person("carol", "Carol"))
        assertEquals(people, ThreadsPeople.filter(people, "  "))
        assertEquals(listOf("ann.lee", "bob"), ThreadsPeople.filter(people, "@ANN").map { it.username })
        assertEquals(listOf("bob"), ThreadsPeople.filter(people, "robert ann").map { it.username })
        assertEquals(emptyList(), ThreadsPeople.filter(people, "dave"))
    }

    @Test
    fun bothNeedsAReaderAndIsNotAskedOfTheirOwnAccount() {
        assertEquals(listOf(ThreadsPeopleList.FOLLOWERS, ThreadsPeopleList.FOLLOWING), ThreadsPeople.tabs(signedIn = false, ownProfile = false))
        assertEquals(ThreadsPeopleList.MUTUAL, ThreadsPeople.tabs(signedIn = true, ownProfile = false).last())
        assertEquals(2, ThreadsPeople.tabs(signedIn = true, ownProfile = true).size)
    }

    @Test
    fun aPostsMediaIdIsItsShortCodeDecoded() {
        assertEquals("1", ThreadsPeople.idOfShortcode("B"))
        assertEquals("181", ThreadsPeople.idOfShortcode("C1"), "C is 2, 1 is 53: 2 × 64 + 53")
        assertEquals("4095", ThreadsPeople.idOfShortcode("__"))
        assertEquals(ThreadsPeople.idOfShortcode("CuXFPIeLLod"), ThreadsPeople.idOfShortcode("CuXFPIeLLodAAAAprivate"), "a private code's id is its first eleven letters")
        assertNull(ThreadsPeople.idOfShortcode("no!"))
        assertNull(ThreadsPeople.idOfShortcode(""))
        val post = ThreadsPost(id = "999", handle = "zuck", authorName = "Z", text = "t", url = "https://www.threads.com/@zuck/post/C1")
        assertEquals("181", ThreadsPeople.mediaId(post))
        assertEquals("999", ThreadsPeople.mediaId(post.copy(url = null)))
        assertNull(ThreadsPeople.mediaId(post.copy(url = null, repostedByHandle = "meta")), "a repost's own id is not the original's")
        assertNull(ThreadsPeople.mediaId(post.copy(url = null, id = "rss-item")))
    }

    @Test
    fun theCookiesReadTheListOnThreadsComWithAGetAndTheCursor(): Unit = runBlocking {
        val meta = FakeMeta { ok(fixture("friendships_followers.json")) }
        val page = direct(meta, ThreadsSession(COOKIES), time = time).people(ThreadsPeopleList.FOLLOWERS, "42", cursor = "c1", query = "ann")
        assertEquals(2, page.items.size)
        val asked = meta.asked.single()
        assertEquals("GET", asked.method)
        assertEquals("www.threads.com", asked.url.host)
        assertEquals("/api/v1/friendships/42/followers/", asked.path)
        assertEquals("c1", asked.url.queryParameter("max_id"))
        assertEquals("ann", asked.url.queryParameter("query"))
        assertEquals("c", asked.headers["X-CSRFToken"])
    }

    @Test
    fun aBearerReadsTheAppApiAndNoSessionReadsNothing(): Unit = runBlocking {
        val meta = FakeMeta { ok("""{"users":[{"pk":"7","username":"liker"}]}""") }
        val likers = direct(meta, bearer, time = time).people(ThreadsPeopleList.LIKERS, "3141")
        assertEquals(listOf("liker"), likers.items.map { it.username })
        val asked = meta.asked.single()
        assertEquals("i.instagram.com", asked.url.host)
        assertEquals("/api/v1/media/3141/likers/", asked.path)
        assertEquals("Bearer IGT:2:made-up", asked.headers["Authorization"])

        val none = FakeMeta { error("a guest asks nothing") }
        val failure = assertFailsWith<ThreadsException> { direct(none, time = time).people(ThreadsPeopleList.FOLLOWING, "42") }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, failure.failure)
        val noId = assertFailsWith<ThreadsException> { direct(none, bearer, time = time).people(ThreadsPeopleList.FOLLOWING, "zuck") }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, noId.failure)
        assertEquals(0, none.asked.size)
    }

    @Test
    fun aThrottleOrARefusalIsATypedFailureAndParksTheSession(): Unit = runBlocking {
        val throttled = direct(FakeMeta { XResponse(429, "") }, ThreadsSession(COOKIES), time = time)
        assertEquals(ThreadsFailure.THROTTLED, assertFailsWith<ThreadsException> { throttled.people(ThreadsPeopleList.FOLLOWERS, "42") }.failure)
        assertTrue(throttled.isSessionParked)
        val missing = direct(FakeMeta { XResponse(404, "") }, ThreadsSession(COOKIES), time = time)
        assertEquals(ThreadsFailure.NO_SUCH_FEED, assertFailsWith<ThreadsException> { missing.people(ThreadsPeopleList.MUTUAL, "42") }.failure)
        val refused = direct(FakeMeta { XResponse(403, "") }, bearer, time = time)
        assertEquals(ThreadsFailure.UNAUTHORIZED, assertFailsWith<ThreadsException> { refused.people(ThreadsPeopleList.LIKERS, "42") }.failure)
    }

    @Test
    fun anAccountsIdIsTheOneRememberedElseItsPublicPagesOwn(): Unit = runBlocking {
        val remembered = FakeMeta { error("the id is known") }
        val memory = ThreadsMemory.InMemory(ThreadsKept(userIds = mapOf("zuck" to "314")))
        assertEquals("314", direct(remembered, memory = memory, time = time).userIdFor("@Zuck"))

        val page = FakeMeta { ok(fixture("profile_page.html")) }
        assertEquals("314216", direct(page, time = time).userIdFor("zuck"))
        assertTrue(page.asked.all { it.method == "GET" })
    }
}
