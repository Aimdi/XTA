package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPeopleList
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the followers, following, both and likers lists show, and where their counts lead. */
class ThreadsPeopleViewTest {
    private fun person(handle: String, name: String = "") = ThreadsProfile(pk = "", id = "", username = handle, fullName = name)

    private val post = ThreadsPost(id = "9", handle = "zuck", authorName = "Z", text = "t", url = "https://www.threads.com/@zuck/post/C1", likeCount = 3)

    @Test
    fun aSignedOutReaderIsToldWhyBeforeAnythingIsRead() {
        assertFalse(ThreadsPeopleView.signedIn(ThreadsSession()))
        assertTrue(ThreadsPeopleView.signedIn(ThreadsSession(bearer = "IGT:2:x")))
        assertEquals(ThreadsPeopleView.Body.SIGNED_OUT, ThreadsPeopleView.body(signedIn = false, read = true, problem = false, people = 4, typed = ""))
    }

    @Test
    fun theListsPlaceSaysWhatItHas() {
        fun body(read: Boolean, problem: Boolean = false, people: Int = 0, typed: String = "") = ThreadsPeopleView.body(true, read, problem, people, typed)
        assertEquals(ThreadsPeopleView.Body.LOADING, body(read = false))
        assertEquals(ThreadsPeopleView.Body.PROBLEM, body(read = false, problem = true))
        assertEquals(ThreadsPeopleView.Body.EMPTY, body(read = true))
        assertEquals(ThreadsPeopleView.Body.NO_MATCH, body(read = true, typed = "ann"))
        assertEquals(ThreadsPeopleView.Body.PEOPLE, body(read = true, people = 2))
        assertEquals(ThreadsPeopleView.Body.PEOPLE, body(read = false, problem = true, people = 2), "people read before stay when a later read fails")
    }

    @Test
    fun metaIsAskedToSearchOnlyFollowListsAndOnlyForTwoLettersOrMore() {
        assertEquals("ann", ThreadsPeopleView.serverQuery(ThreadsPeopleList.FOLLOWERS, " @ann "))
        assertEquals("an", ThreadsPeopleView.serverQuery(ThreadsPeopleList.FOLLOWING, "an"))
        assertNull(ThreadsPeopleView.serverQuery(ThreadsPeopleList.FOLLOWERS, "a"))
        assertNull(ThreadsPeopleView.serverQuery(ThreadsPeopleList.MUTUAL, "ann"))
        assertNull(ThreadsPeopleView.serverQuery(ThreadsPeopleList.LIKERS, "ann"))
    }

    @Test
    fun theShownListIsEveryPageOnceFilteredByWhatIsTyped() {
        val shown = ThreadsPeopleView.shown(listOf(person("ann"), person("bob", "Annie")), listOf(person("ANN"), person("carol")), "ann")
        assertEquals(listOf("ann", "bob"), shown.map { it.username })
    }

    @Test
    fun bothIsATabOnlyForASignedInReaderLookingAtSomeoneElse() {
        assertEquals(3, ThreadsPeopleView.tabsFor(signedIn = true, me = null, handle = "zuck").size)
        assertEquals(2, ThreadsPeopleView.tabsFor(signedIn = true, me = "Zuck", handle = "@zuck").size)
        assertEquals(2, ThreadsPeopleView.tabsFor(signedIn = false, me = null, handle = "zuck").size)
    }

    @Test
    fun aTabNamesTheCountTheProfileTold() {
        val profile = person("zuck").copy(followerCount = 18_027, followingCount = 19)
        assertEquals(18_027L, ThreadsPeopleView.count(profile, ThreadsPeopleList.FOLLOWERS))
        assertEquals(19L, ThreadsPeopleView.count(profile, ThreadsPeopleList.FOLLOWING))
        assertNull(ThreadsPeopleView.count(profile, ThreadsPeopleList.MUTUAL))
        assertNull(ThreadsPeopleView.count(null, ThreadsPeopleList.FOLLOWERS))
    }

    @Test
    fun aLikeCountOpensWhoLikedOnlyForASignedInReaderAndALikedPost() {
        assertTrue(ThreadsPeopleView.opensLikers(post, signedIn = true))
        assertFalse(ThreadsPeopleView.opensLikers(post, signedIn = false))
        assertFalse(ThreadsPeopleView.opensLikers(post.copy(likeCount = 0), signedIn = true))
        assertFalse(ThreadsPeopleView.opensLikers(post.copy(likeCount = null), signedIn = true))
        assertFalse(ThreadsPeopleView.opensLikers(post.copy(url = null, id = "rss-item"), signedIn = true))
    }

    @Test
    fun theCountsAndTheLikersHaveRoutesOfTheirOwn() {
        assertEquals(ThreadsPeopleList.FOLLOWERS, ThreadsProfileHeader.list(ThreadsCount.FOLLOWERS))
        assertEquals(ThreadsPeopleList.FOLLOWING, ThreadsProfileHeader.list(ThreadsCount.FOLLOWING))
        assertNull(ThreadsProfileHeader.list(ThreadsCount.POSTS))
        assertEquals(ThreadsRoute.People("zuck", ThreadsPeopleList.FOLLOWING), ThreadsRoutes.parse(ThreadsRoutes.people("zuck", ThreadsPeopleList.FOLLOWING)))
        assertEquals(ThreadsRoute.People("zuck", ThreadsPeopleList.MUTUAL), ThreadsRoutes.parse(ThreadsRoutes.people("@Zuck", ThreadsPeopleList.MUTUAL)))
        assertEquals(ThreadsRoute.People("zuck", ThreadsPeopleList.FOLLOWERS), ThreadsRoutes.parse("people?handle=zuck&list=likers"))
        assertEquals(ThreadsRoute.Likers("181", post.url), ThreadsRoutes.parse(ThreadsRoutes.likers("181", post.url)))
        assertNull(ThreadsRoutes.parse("likers?id=abc"))
    }
}
